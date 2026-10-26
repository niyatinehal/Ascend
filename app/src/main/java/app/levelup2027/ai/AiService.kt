package app.levelup2027.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.levelup2027.data.AiMode
import app.levelup2027.data.AiUsage
import app.levelup2027.data.AiUsageDao
import app.levelup2027.data.SettingsSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.security.KeyStore
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small secrets (the user's API key, the server device token). */
interface SecretStore {
    fun get(name: String): String?
    fun put(name: String, value: String?)
}

class MemorySecretStore : SecretStore {
    private val map = HashMap<String, String>()
    override fun get(name: String) = map[name]
    override fun put(name: String, value: String?) { if (value == null) map.remove(name) else map[name] = value }
}

/**
 * Encrypts values with an AES key held in the Android Keystore (never exportable). The ciphertext
 * lives in a private SharedPreferences file that is excluded from cloud backup and device transfer.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    private val alias = "ascend_secrets"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    override fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return runCatching {
            val (iv, data) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            c.doFinal(data).decodeToString()
        }.getOrNull()
    }

    override fun put(name: String, value: String?) {
        if (value == null) { prefs.edit().remove(name).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(value.toByteArray()), Base64.NO_WRAP)
        prefs.edit().putString(name, enc).apply()
    }
}

/** Approximate list prices (USD per million tokens) for the usage estimate in own-key mode. */
object ModelPrices {
    private val perMillion = mapOf(
        "claude-haiku-5-5" to (0.10 to 0.50),
        "claude-sonnet-5-5" to (2.00 to 10.00),
    )

    fun estimateUsd(model: String, input: Long, output: Long): Double {
        val (i, o) = perMillion.entries.firstOrNull { model.startsWith(it.key) }?.value ?: return 0.0
        return input / 1e6 * i + output / 1e6 * o
    }
}

enum class AiAvailability { READY, NEEDS_CONSENT, NOT_CONFIGURED, OFF }

/**
 * The single entry point for AI features. Checks consent and configuration, enforces the local
 * quota in own-key mode, picks the client, records usage (counts only, never content), parses the
 * structured output, and leaves validation to the caller.
 */
class AiService(
    private val settings: SettingsSource,
    private val secrets: SecretStore,
    private val usage: AiUsageDao,
    private val builtInServerUrl: String,
    private val http: HttpTransport = UrlConnectionTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val directClientFactory: (String) -> AiClient = { key -> AnthropicDirectClient(key) },
) {
    companion object {
        const val KEY_API = "anthropic_api_key"
        const val KEY_DEVICE_TOKEN = "server_device_token"
    }

    fun hasApiKey() = !secrets.get(KEY_API).isNullOrBlank()
    fun setApiKey(key: String?) = secrets.put(KEY_API, key?.trim()?.ifBlank { null })

    suspend fun serverUrl(): String = settings.current().ai.serverUrl.ifBlank { builtInServerUrl }.trim()

    suspend fun availability(): AiAvailability {
        val s = settings.current().ai
        return when {
            s.mode == AiMode.OFF -> AiAvailability.OFF
            s.mode == AiMode.OWN_KEY && !hasApiKey() -> AiAvailability.NOT_CONFIGURED
            s.mode == AiMode.SERVER && serverUrl().isBlank() -> AiAvailability.NOT_CONFIGURED
            !s.consentGiven -> AiAvailability.NEEDS_CONSENT
            else -> AiAvailability.READY
        }
    }

    private fun monthStart(): Long =
        LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    suspend fun usageThisMonth(): List<AiUsage> = usage.since(monthStart())

    @Serializable private data class RegisterResponse(val token: String = "")

    private suspend fun deviceToken(forceNew: Boolean): String {
        if (!forceNew) secrets.get(KEY_DEVICE_TOKEN)?.let { return it }
        val res = http.post("${serverUrl().trimEnd('/')}/v1/register", "{}", null)
        if (res.status == 429) throw AiError.RateLimited("Too many new devices from this network today. Try again tomorrow.")
        val token = runCatching { AiJson.decodeFromString(RegisterResponse.serializer(), res.body).token }.getOrNull()
        if (res.status !in 200..299 || token.isNullOrBlank()) {
            throw if (res.status == 0) AiError.Network("Couldn't reach the AI server. Check your connection.") else AiError.Server("Couldn't register with the AI server (${res.status}).")
        }
        secrets.put(KEY_DEVICE_TOKEN, token)
        return token
    }

    /** Removes this device's record (token and usage counters) from the server. Best effort. */
    suspend fun forgetDeviceOnServer() {
        val token = secrets.get(KEY_DEVICE_TOKEN) ?: return
        runCatching { http.delete("${serverUrl().trimEnd('/')}/v1/device", token) }
        secrets.put(KEY_DEVICE_TOKEN, null)
    }

    private suspend fun client(): AiClient {
        val s = settings.current().ai
        return when (s.mode) {
            AiMode.OFF -> throw AiError.NotConfigured("AI features are turned off in Settings.")
            AiMode.OWN_KEY -> {
                val key = secrets.get(KEY_API) ?: throw AiError.NotConfigured("Add your Anthropic API key in Settings → AI.")
                val used = usageThisMonth().count { it.route == "own_key" }
                if (used >= s.monthlyRequestLimit) {
                    throw AiError.QuotaExceeded("You've reached your own limit of ${s.monthlyRequestLimit} AI requests this month. You can raise it in Settings → AI.")
                }
                directClientFactory(key)
            }
            AiMode.SERVER -> {
                val url = serverUrl()
                if (url.isBlank()) throw AiError.NotConfigured("This build has no AI server set. Add your own API key or a server address in Settings → AI.")
                ProxyAiClient(url, ::deviceToken, http)
            }
        }
    }

    private suspend fun <T> run(op: AiOperation, system: String, messages: List<ChatTurn>, schema: kotlinx.serialization.json.JsonObject, serializer: KSerializer<T>): T {
        if (!settings.current().ai.consentGiven) throw AiError.NoConsent()
        val c = client()
        val res = try {
            c.call(AiRequest(op, system, messages, schema))
        } catch (e: AiError) {
            usage.insert(AiUsage(at = clock(), operation = op.wireName, model = op.model, route = c.route, success = false, errorCode = e.code))
            throw e
        }
        usage.insert(AiUsage(at = clock(), operation = op.wireName, model = res.model.ifBlank { op.model }, route = c.route,
            inputTokens = res.usage.inputTokens, outputTokens = res.usage.outputTokens, cacheReadTokens = res.usage.cacheReadTokens, success = true))
        return runCatching { AiJson.decodeFromString(serializer, res.json) }
            .getOrElse { throw AiError.InvalidOutput("The AI's answer couldn't be read. Please try again.") }
    }

    suspend fun intake(today: LocalDate, text: String): IntakeResult {
        val r = run(AiOperation.INTAKE, Prompts.INTAKE, listOf(ChatTurn("user", ContextBuilder.intake(today, text))), Schemas.intake, IntakeResult.serializer())
        return r.copy(questions = r.questions.filter { it.question.isNotBlank() }.take(3))
    }

    suspend fun generatePlan(payload: String): PlanDraft =
        run(AiOperation.PLAN, Prompts.PLAN, listOf(ChatTurn("user", payload)), Schemas.plan, PlanDraft.serializer())

    suspend fun planWeek(payload: String): WeekPlan =
        run(AiOperation.WEEK, Prompts.WEEK, listOf(ChatTurn("user", payload)), Schemas.week, WeekPlan.serializer())

    suspend fun adjust(payload: String): AdjustResult =
        run(AiOperation.ADJUST, Prompts.ADJUST, listOf(ChatTurn("user", payload)), Schemas.adjust, AdjustResult.serializer())
}
