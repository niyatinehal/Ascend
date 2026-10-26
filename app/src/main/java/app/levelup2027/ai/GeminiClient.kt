package app.levelup2027.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Round-robin over several API keys. Each request starts at the next key; a key that hits its
 * rate limit rests for a while and is skipped, and an invalid key is skipped for much longer.
 * Note that Gemini rate limits are per Google Cloud project, so only keys from different projects
 * add capacity; keys from one project share its limit.
 */
class KeyRing(keys: List<String>, private val clock: () -> Long = System::currentTimeMillis) {
    val keys: List<String> = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    private var next = 0
    private val restingUntil = HashMap<String, Long>()

    val isEmpty: Boolean get() = keys.isEmpty()

    /** Keys to try for one request, in order: rotated, with resting keys left out. */
    @Synchronized
    fun order(): List<String> {
        if (keys.isEmpty()) return emptyList()
        val start = next % keys.size
        next = (next + 1) % keys.size
        val now = clock()
        return (keys.indices).map { keys[(start + it) % keys.size] }.filter { (restingUntil[it] ?: 0) <= now }
    }

    @Synchronized
    fun rest(key: String, seconds: Long) { restingUntil[key] = clock() + seconds * 1000 }

    /** Seconds until the first resting key is usable again (0 if one is usable now). */
    @Synchronized
    fun secondsUntilAvailable(): Long {
        val now = clock()
        val soonest = keys.minOfOrNull { restingUntil[it] ?: 0 } ?: return 0
        return ((soonest - now) / 1000).coerceAtLeast(0)
    }
}

/**
 * Google Gemini (generateContent with a JSON-schema response). Uses the same schemas and prompts as
 * the Claude routes; the app validates the output the same way whichever provider produced it.
 */
class GeminiClient(
    private val keys: KeyRing,
    private val modelOverride: String = "",
    private val http: HttpTransport = UrlConnectionTransport(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    override val route: String = "gemini",
) : AiClient {

    companion object {
        /**
         * Free-tier models to try in order. A model that is overloaded (503), unavailable (404) or
         * times out is skipped for the next one. Measured on 26 Oct 2026 with real requests:
         * gemini-3-flash-preview drafted a full plan in ~13 s, gemini-3.6-flash in ~30 s,
         * gemini-3.8-flash was overloaded (timeouts), gemini-2.5-* is closed to new users.
         */
        val PLAN_MODELS = listOf("gemini-3-flash-preview", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash")
        val LIGHT_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3-flash-preview")
        val PLAN_MODEL = PLAN_MODELS.first()
        val LIGHT_MODEL = LIGHT_MODELS.first()

        fun modelsFor(op: AiOperation) = if (op == AiOperation.INTAKE) LIGHT_MODELS else PLAN_MODELS

        /** Thinking tokens count towards Gemini's output limit, so allow more room than for Claude. */
        fun maxOutputTokens(op: AiOperation) = (op.maxTokens * 2).coerceAtMost(65_536)

        private val REFUSALS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "LANGUAGE", "IMAGE_SAFETY")
    }

    fun body(request: AiRequest): String {
        val o = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", request.system) }) } }
            put("contents", buildJsonArray {
                request.messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == "assistant") "model" else "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", m.text) }) }
                    })
                }
            })
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseJsonSchema", request.schema)
                put("maxOutputTokens", maxOutputTokens(request.op))
            }
        }
        return AiJson.encodeToString(JsonObject.serializer(), o)
    }

    override suspend fun call(request: AiRequest): AiResponse {
        if (keys.isEmpty) throw AiError.NotConfigured("No Gemini API key is set. Add one in Settings → AI.")
        val body = body(request)
        val models = (listOfNotNull(modelOverride.ifBlank { null }) + modelsFor(request.op)).distinct()
        var lastError: AiError = AiError.RateLimited("All Gemini keys have reached their free-tier limit. Try again in about ${keys.secondsUntilAvailable() / 60 + 1} min.")
        var busyModels = 0
        for (model in models) {
            val order = keys.order()
            if (order.isEmpty()) break
            keyLoop@ for (key in order) {
                val res = http.postWithHeaders("$baseUrl/v1beta/models/$model:generateContent", body, mapOf("x-goog-api-key" to key))
                when {
                    res.status in 200..299 -> return parse(res.body, model)
                    // The model is overloaded, unavailable to this account, or too slow: try the next model.
                    res.status == 503 || res.status == 504 || res.status == 404 || res.status == 500 -> {
                        busyModels++
                        lastError = AiError.Server("Gemini is very busy right now. Try again in a few minutes.")
                        break@keyLoop
                    }
                    res.status == 429 -> {
                        keys.rest(key, retryAfterSeconds(res.body) ?: 60)
                        lastError = AiError.RateLimited("All Gemini keys have reached their free-tier limit. Try again in a few minutes.")
                    }
                    res.status == 400 && res.body.contains("API_KEY_INVALID") || res.status == 401 || res.status == 403 -> {
                        keys.rest(key, 24 * 3600)
                        lastError = AiError.Auth("A Gemini API key was rejected. Check the keys in Settings → AI.")
                    }
                    res.status == 0 -> throw AiError.Network("Couldn't reach Gemini. Check your connection.")
                    else -> throw AiError.Server("Gemini rejected the request (${res.status}): ${errorMessage(res.body)}")
                }
            }
            if (lastError is AiError.RateLimited || lastError is AiError.Auth) break // every key failed; another model won't help
        }
        throw lastError
    }

    private fun errorMessage(body: String) = runCatching {
        AiJson.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content.take(160)
    }.getOrDefault("")

    /** Reads google.rpc.RetryInfo ("retryDelay": "37s") from a 429 body. */
    private fun retryAfterSeconds(body: String): Long? = runCatching {
        val details = AiJson.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["details"]!!.jsonArray
        details.firstNotNullOf { d -> d.jsonObject["retryDelay"]?.jsonPrimitive?.content }.removeSuffix("s").toDouble().toLong() + 1
    }.getOrNull()

    private fun parse(body: String, model: String): AiResponse {
        val o = runCatching { AiJson.parseToJsonElement(body).jsonObject }.getOrElse { throw AiError.InvalidOutput("Gemini sent an unreadable response.") }
        o["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull?.let {
            throw AiError.Refused("Gemini declined this request ($it). Try rephrasing it.")
        }
        val candidate = (o["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject ?: throw AiError.InvalidOutput("Gemini returned no answer.")
        val finish = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
        if (finish == "MAX_TOKENS") throw AiError.Truncated()
        if (finish in REFUSALS) throw AiError.Refused("Gemini declined this request. Try rephrasing it.")
        val text = (candidate["content"]?.jsonObject?.get("parts") as? JsonArray).orEmpty()
            .map { it.jsonObject }
            .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
        if (text.isBlank()) throw AiError.InvalidOutput("Gemini returned no plan text.")
        val u = o["usageMetadata"]?.jsonObject
        fun n(k: String) = (u?.get(k) as? JsonPrimitive)?.longOrNull ?: 0L
        return AiResponse(
            text,
            o["modelVersion"]?.jsonPrimitive?.contentOrNull ?: model,
            AiUsageInfo(n("promptTokenCount"), n("candidatesTokenCount") + n("thoughtsTokenCount"), n("cachedContentTokenCount")),
        )
    }
}
