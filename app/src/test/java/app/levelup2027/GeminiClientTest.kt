package app.levelup2027

import app.levelup2027.ai.AiError
import app.levelup2027.ai.AiJson
import app.levelup2027.ai.AiOperation
import app.levelup2027.ai.AiRequest
import app.levelup2027.ai.ChatTurn
import app.levelup2027.ai.GeminiClient
import app.levelup2027.ai.KeyRing
import app.levelup2027.ai.Prompts
import app.levelup2027.ai.Schemas
import app.levelup2027.ai.UrlConnectionTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Gemini client against a local mock of generateContent: request shape, key rotation, errors. */
class GeminiClientTest {
    private var server: MockHttp? = null
    @After fun stop() { server?.close() }

    private fun ok(text: String = """{"summary":"ok"}""", finish: String = "STOP") = 200 to """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"thinking...","thought":true},{"text":${JsonPrimitive(text)}}]},"finishReason":"$finish"}],
         "usageMetadata":{"promptTokenCount":900,"candidatesTokenCount":300,"thoughtsTokenCount":200,"cachedContentTokenCount":0},
         "modelVersion":"gemini-3.8-flash"}
    """.trimIndent()

    private val rateLimited = 429 to """{"error":{"code":429,"message":"Resource exhausted","status":"RESOURCE_EXHAUSTED",
        "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"37s"}]}}"""

    private fun client(keys: KeyRing, respond: (MockRequest) -> Pair<Int, String>): GeminiClient {
        val m = MockHttp(respond)
        server = m
        return GeminiClient(keys, http = UrlConnectionTransport(), baseUrl = m.url)
    }

    private val plan = AiRequest(AiOperation.PLAN, Prompts.PLAN, listOf(ChatTurn("user", "{\"a\":1}"), ChatTurn("assistant", "x"), ChatTurn("user", "y")), Schemas.plan)
    private fun key(r: MockRequest) = r.headers.getValue("x-goog-api-key").single()

    @Test fun `sends system instruction, roles, JSON schema and key header, and skips thought parts`() = runTest {
        val c = client(KeyRing(listOf("key-A"))) { ok() }
        val res = c.call(plan)
        assertEquals("""{"summary":"ok"}""", res.json)
        assertEquals(900L, res.usage.inputTokens)
        assertEquals(500L, res.usage.outputTokens) // answer + thinking
        val r = server!!.requests.single()
        assertEquals("/v1beta/models/${GeminiClient.PLAN_MODEL}:generateContent", r.path)
        assertEquals("key-A", key(r))
        assertTrue("key-A" !in r.path) // the key is never in the URL
        val body = AiJson.parseToJsonElement(r.body).jsonObject
        assertEquals(Prompts.PLAN, body.getValue("systemInstruction").jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(listOf("user", "model", "user"), body.getValue("contents").jsonArray.map { it.jsonObject.getValue("role").jsonPrimitive.content })
        val gen = body.getValue("generationConfig").jsonObject
        assertEquals("application/json", gen.getValue("responseMimeType").jsonPrimitive.content)
        assertEquals(Schemas.plan, gen.getValue("responseJsonSchema"))
        assertEquals(32000, gen.getValue("maxOutputTokens").jsonPrimitive.content.toInt())
    }

    @Test fun `intake uses the lighter model`() = runTest {
        val c = client(KeyRing(listOf("k"))) { ok() }
        c.call(plan.copy(op = AiOperation.INTAKE, schema = Schemas.intake))
        assertEquals("/v1beta/models/${GeminiClient.LIGHT_MODEL}:generateContent", server!!.requests.single().path)
    }

    @Test fun `keys take turns across requests`() = runTest {
        val c = client(KeyRing(listOf("k1", "k2", "k3"))) { ok() }
        repeat(4) { c.call(plan) }
        assertEquals(listOf("k1", "k2", "k3", "k1"), server!!.requests.map(::key))
    }

    @Test fun `a rate-limited key rests and the next key answers the same request`() = runTest {
        var now = 0L
        val ring = KeyRing(listOf("k1", "k2"), clock = { now })
        val c = client(ring) { r -> if (key(r) == "k1") rateLimited else ok() }
        c.call(plan) // k1 → 429, k2 → ok
        c.call(plan) // would start at k2 anyway
        c.call(plan) // k1 is resting (37 s), so k2 again
        assertEquals(listOf("k1", "k2", "k2", "k2"), server!!.requests.map(::key))
        now += 40_000
        c.call(plan); c.call(plan) // k1 is back in the rotation
        assertTrue("k1" in server!!.requests.takeLast(2).map(::key))
    }

    @Test fun `when every key is rate-limited the user gets a clear message`() = runTest {
        val ring = KeyRing(listOf("k1", "k2"))
        val c = client(ring) { rateLimited }
        val e = runCatching { c.call(plan) }.exceptionOrNull()
        assertTrue(e is AiError.RateLimited)
        val again = runCatching { c.call(plan) }.exceptionOrNull()
        assertTrue(again is AiError.RateLimited)
        assertEquals(2, server!!.requests.size) // second call didn't hit the network: both keys resting
    }

    @Test fun `an invalid key is skipped, and overloaded or unavailable models fall back to the next`() = runTest {
        val (first, second, third) = GeminiClient.PLAN_MODELS
        val c = client(KeyRing(listOf("bad", "good"))) { r ->
            when {
                key(r) == "bad" -> 400 to """{"error":{"code":400,"message":"API key not valid.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""
                first in r.path -> 503 to """{"error":{"code":503,"message":"high demand","status":"UNAVAILABLE"}}"""
                second in r.path -> 404 to """{"error":{"code":404,"message":"no longer available to new users"}}"""
                else -> ok()
            }
        }
        c.call(plan)
        val calls = server!!.requests.map { key(it) to it.path.substringAfter("models/").substringBefore(":") }
        assertEquals(listOf("bad" to first, "good" to first, "good" to second, "good" to third), calls)
    }

    @Test fun `when every model is busy the user is told, and a model override is tried first`() = runTest {
        val c = client(KeyRing(listOf("k"))) { 503 to "{}" }
        assertTrue(runCatching { c.call(plan) }.exceptionOrNull() is AiError.Server)
        assertEquals(GeminiClient.PLAN_MODELS.size, server!!.requests.size)
        server!!.close()
        val m = MockHttp { ok() }
        server = m
        GeminiClient(KeyRing(listOf("k")), modelOverride = "my-model", http = UrlConnectionTransport(), baseUrl = m.url).call(plan)
        assertTrue(m.requests.single().path.contains("models/my-model:"))
    }

    @Test fun `truncated, blocked and empty answers are reported`() = runTest {
        suspend fun err(resp: Pair<Int, String>) = runCatching { client(KeyRing(listOf("k"))) { resp }.call(plan) }.exceptionOrNull().also { server?.close() }
        assertTrue(err(ok("{\"summ", "MAX_TOKENS")) is AiError.Truncated)
        assertTrue(err(ok("", "SAFETY")) is AiError.Refused)
        assertTrue(err(200 to """{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"}}""") is AiError.Refused)
        assertTrue(err(200 to """{"candidates":[{"content":{"parts":[]},"finishReason":"STOP"}]}""") is AiError.InvalidOutput)
        assertTrue(err(503 to """{"error":{"message":"overloaded"}}""") is AiError.Server)
        assertTrue(runCatching { GeminiClient(KeyRing(emptyList())).call(plan) }.exceptionOrNull() is AiError.NotConfigured)
    }
}
