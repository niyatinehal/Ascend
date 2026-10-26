package app.levelup2027.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration

/**
 * Each AI operation has a fixed model, effort and output budget. The server applies its own copy
 * of this table (worker/src/operations.js) and ignores what the client asks for; own-key mode uses
 * this one. Plan generation uses Claude Sonnet 5.5; the short intake step uses Claude Haiku 5.5.
 */
enum class AiOperation(val wireName: String, val model: String, val effort: String, val maxTokens: Long, val serverFallback: Boolean) {
    INTAKE("intake", "claude-haiku-5-5", "low", 4_000, serverFallback = false),
    PLAN("plan", "claude-sonnet-5-5", "medium", 16_000, serverFallback = true),
    WEEK("week", "claude-sonnet-5-5", "low", 8_000, serverFallback = true),
    ADJUST("adjust", "claude-sonnet-5-5", "low", 6_000, serverFallback = true),
}

data class AiRequest(
    val op: AiOperation,
    val system: String,
    /** Alternating user/assistant turns, ending with a user turn. */
    val messages: List<ChatTurn>,
    val schema: JsonObject,
)

data class AiUsageInfo(val inputTokens: Long, val outputTokens: Long, val cacheReadTokens: Long)

data class AiResponse(val json: String, val model: String, val usage: AiUsageInfo)

/** Every way an AI call can fail, in terms the UI can explain. The user's input is never lost on failure. */
sealed class AiError(message: String) : Exception(message) {
    abstract val code: String
    class NotConfigured(msg: String) : AiError(msg) { override val code = "not_configured" }
    class NoConsent : AiError("Please review what is shared with the AI first.") { override val code = "no_consent" }
    class QuotaExceeded(msg: String) : AiError(msg) { override val code = "quota" }
    class RateLimited(msg: String) : AiError(msg) { override val code = "rate_limited" }
    class Auth(msg: String) : AiError(msg) { override val code = "auth" }
    class Network(msg: String) : AiError(msg) { override val code = "network" }
    class Server(msg: String) : AiError(msg) { override val code = "server" }
    class Refused(msg: String) : AiError(msg) { override val code = "refused" }
    class Truncated : AiError("The AI's answer was cut off. Try again, or describe fewer goals at once.") { override val code = "truncated" }
    class InvalidOutput(msg: String) : AiError(msg) { override val code = "invalid_output" }
}

/** The one interface the app depends on; the provider sits behind it. */
interface AiClient {
    val route: String
    suspend fun call(request: AiRequest): AiResponse
}

/** Converts kotlinx JSON to plain Kotlin values for the SDK's JsonValue.from(). */
fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonObject -> mapValues { it.value.toPlain() }
    is JsonArray -> map { it.toPlain() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        else -> doubleOrNull
    }
}

/**
 * Own-key mode: calls the Claude API directly with the official Java SDK. The key is the user's,
 * stored encrypted on the phone, and never leaves it except in requests to api.anthropic.com.
 */
class AnthropicDirectClient(
    apiKey: String,
    baseUrl: String? = null,
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .timeout(Duration.ofMinutes(4))
        .maxRetries(2)
        .build(),
) : AiClient {
    override val route = "own_key"

    override suspend fun call(request: AiRequest): AiResponse = withContext(Dispatchers.IO) {
        val op = request.op
        val schema = JsonOutputFormat.Schema.builder().apply {
            request.schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v.toPlain())) }
        }.build()
        val params = MessageCreateParams.builder()
            .model(op.model)
            .maxTokens(op.maxTokens)
            // The system prompt is identical for every user, so it caches across requests.
            .systemOfTextBlockParams(listOf(TextBlockParam.builder().text(request.system).cacheControl(CacheControlEphemeral.builder().build()).build()))
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.of(op.effort))
                    .format(JsonOutputFormat.builder().schema(schema).build())
                    .build(),
            )
            .apply {
                request.messages.forEach { if (it.role == "assistant") addAssistantMessage(it.text) else addUserMessage(it.text) }
                if (op.serverFallback) {
                    // Re-run a safety-classifier refusal on Anthropic's recommended fallback model.
                    putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()
        val message = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw AiError.Auth("The API key was rejected. Check it in Settings → AI.")
        } catch (e: PermissionDeniedException) {
            throw AiError.Auth("This API key isn't allowed to use ${op.model}.")
        } catch (e: RateLimitException) {
            throw AiError.RateLimited("Your Anthropic account hit a rate or spend limit. Try again in a few minutes.")
        } catch (e: BadRequestException) {
            throw AiError.Server("The request was rejected (${e.message?.take(160)}).")
        } catch (e: InternalServerException) {
            throw AiError.Server("The AI service had a problem. Try again shortly.")
        } catch (e: AnthropicServiceException) {
            throw AiError.Server("The AI service returned an error (${e.statusCode()}).")
        } catch (e: AnthropicIoException) {
            throw AiError.Network("Couldn't reach the AI service. Check your connection.")
        }
        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw AiError.Refused("The AI declined this request. Try rephrasing it.")
            StopReason.MAX_TOKENS -> throw AiError.Truncated()
            else -> Unit
        }
        val text = message.content().mapNotNull { b -> b.text().orElse(null)?.text() }.lastOrNull()
            ?: throw AiError.InvalidOutput("The AI returned no plan text.")
        val u = message.usage()
        AiResponse(text, message.model().asString(), AiUsageInfo(u.inputTokens(), u.outputTokens(), u.cacheReadInputTokens().orElse(0L)))
    }
}

/**
 * Server mode: the Ascend AI server (a Cloudflare Worker, see /worker) holds the Anthropic key,
 * picks the model, and enforces per-device quotas. This is our own small HTTP API, not the Claude API.
 */
class ProxyAiClient(
    private val baseUrl: String,
    private val tokenProvider: suspend (forceNew: Boolean) -> String,
    private val http: HttpTransport = UrlConnectionTransport(),
) : AiClient {
    override val route = "server"

    @Serializable
    private data class ProxyMessage(val role: String, val content: String)

    @Serializable
    private data class ProxyRequest(val op: String, val system: String, val messages: List<ProxyMessage>, val schema: JsonObject)

    @Serializable
    private data class ProxyUsage(val inputTokens: Long = 0, val outputTokens: Long = 0, val cacheReadTokens: Long = 0)

    @Serializable
    private data class ProxyResponse(val output: String = "", val model: String = "", val usage: ProxyUsage = ProxyUsage(), val stopReason: String = "")

    @Serializable
    private data class ProxyError(val error: String = "", val message: String = "")

    override suspend fun call(request: AiRequest): AiResponse {
        val body = AiJson.encodeToString(
            ProxyRequest.serializer(),
            ProxyRequest(request.op.wireName, request.system, request.messages.map { ProxyMessage(it.role, it.text) }, request.schema),
        )
        var token = tokenProvider(false)
        var res = http.post("${baseUrl.trimEnd('/')}/v1/ai", body, token)
        if (res.status == 401) {
            token = tokenProvider(true)
            res = http.post("${baseUrl.trimEnd('/')}/v1/ai", body, token)
        }
        if (res.status !in 200..299) {
            val err = runCatching { AiJson.decodeFromString(ProxyError.serializer(), res.body) }.getOrNull()
            val msg = err?.message?.ifBlank { null }
            throw when {
                res.status == 429 && err?.error == "quota_exceeded" -> AiError.QuotaExceeded(msg ?: "You've used this month's AI allowance. Your plan keeps working without AI.")
                res.status == 429 -> AiError.RateLimited(msg ?: "The AI server is busy. Try again in a minute.")
                res.status == 401 || res.status == 403 -> AiError.Auth(msg ?: "The AI server didn't accept this device.")
                res.status == 422 && err?.error == "refused" -> AiError.Refused(msg ?: "The AI declined this request. Try rephrasing it.")
                res.status == 422 && err?.error == "truncated" -> AiError.Truncated()
                res.status == 0 -> AiError.Network(msg ?: "Couldn't reach the AI server. Check your connection.")
                else -> AiError.Server(msg ?: "The AI server returned an error (${res.status}).")
            }
        }
        val r = runCatching { AiJson.decodeFromString(ProxyResponse.serializer(), res.body) }
            .getOrElse { throw AiError.InvalidOutput("The AI server sent an unreadable response.") }
        if (r.output.isBlank()) throw AiError.InvalidOutput("The AI server returned no plan text.")
        return AiResponse(r.output, r.model, AiUsageInfo(r.usage.inputTokens, r.usage.outputTokens, r.usage.cacheReadTokens))
    }
}

data class HttpResult(val status: Int, val body: String)

interface HttpTransport {
    suspend fun post(url: String, body: String, bearer: String?): HttpResult
    suspend fun delete(url: String, bearer: String?): HttpResult
    suspend fun postWithHeaders(url: String, body: String, headers: Map<String, String>): HttpResult =
        throw UnsupportedOperationException("postWithHeaders")
}

class UrlConnectionTransport(private val timeoutMs: Int = 240_000) : HttpTransport {
    override suspend fun post(url: String, body: String, bearer: String?) = request("POST", url, body, bearerHeader(bearer))
    override suspend fun delete(url: String, bearer: String?) = request("DELETE", url, null, bearerHeader(bearer))
    override suspend fun postWithHeaders(url: String, body: String, headers: Map<String, String>) = request("POST", url, body, headers)

    private fun bearerHeader(bearer: String?) = if (bearer != null) mapOf("Authorization" to "Bearer $bearer") else emptyMap()

    private suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = timeoutMs
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = c.responseCode
            val text = (if (status in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() } ?: ""
            HttpResult(status, text)
        } catch (e: java.net.SocketTimeoutException) {
            // Distinguish "too slow" (the provider may be overloaded) from "no connection".
            HttpResult(504, "{}")
        } catch (e: IOException) {
            HttpResult(0, Json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("message" to JsonPrimitive("Couldn't reach the AI server. Check your connection.")))))
        }
    }
}
