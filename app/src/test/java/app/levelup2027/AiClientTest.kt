package app.levelup2027

import app.levelup2027.ai.AiError
import app.levelup2027.ai.AiJson
import app.levelup2027.ai.AiOperation
import app.levelup2027.ai.AiRequest
import app.levelup2027.ai.AnthropicDirectClient
import app.levelup2027.ai.ChatTurn
import app.levelup2027.ai.HttpResult
import app.levelup2027.ai.HttpTransport
import app.levelup2027.ai.Prompts
import app.levelup2027.ai.ProxyAiClient
import app.levelup2027.ai.Schemas
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * The own-key client runs the real Anthropic Java SDK against a local HTTP server, so these tests
 * check the exact request the SDK sends and how responses and errors are mapped. They don't call
 * the real API (that needs a key; see the README's "Testing AI with a real key").
 */
class AiClientTest {
    private var server: ServerSocket? = null
    private val requests = mutableListOf<Pair<Map<String, List<String>>, String>>()

    /** A minimal HTTP/1.1 server: answers every request with [status] and [body]. */
    private fun serve(status: Int, body: String): String {
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        server = s
        thread(isDaemon = true) {
            while (!s.isClosed) {
                val sock = runCatching { s.accept() }.getOrNull() ?: break
                sock.use { c ->
                    val input = c.getInputStream().buffered()
                    val headers = LinkedHashMap<String, MutableList<String>>()
                    val line = StringBuilder()
                    fun readLine(): String {
                        line.setLength(0)
                        while (true) {
                            val b = input.read()
                            if (b == -1 || b == '\n'.code) break
                            if (b != '\r'.code) line.append(b.toChar())
                        }
                        return line.toString()
                    }
                    readLine() // request line
                    while (true) {
                        val h = readLine()
                        if (h.isEmpty()) break
                        val (k, v) = h.split(":", limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
                        headers.getOrPut(k) { mutableListOf() } += v
                    }
                    val len = headers["content-length"]?.firstOrNull()?.toInt() ?: 0
                    val bytes = ByteArray(len)
                    var read = 0
                    while (read < len) { val n = input.read(bytes, read, len - read); if (n < 0) break; read += n }
                    synchronized(requests) { requests += headers to bytes.decodeToString() }
                    val out = body.toByteArray()
                    val reason = if (status in 200..299) "OK" else "Error"
                    c.getOutputStream().apply {
                        write("HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${out.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(out)
                        flush()
                    }
                }
            }
        }
        return "http://127.0.0.1:${s.localPort}"
    }

    @After fun stop() { server?.close() }

    private fun message(text: String, stop: String = "end_turn", model: String = "claude-sonnet-5-5") = """
        {"id":"msg_1","type":"message","role":"assistant","model":"$model",
         "content":[{"type":"text","text":${JsonPrimitive(text)}}],
         "stop_reason":"$stop","stop_sequence":null,
         "usage":{"input_tokens":1200,"output_tokens":800,"cache_read_input_tokens":900,"cache_creation_input_tokens":0}}
    """.trimIndent()

    private val req = AiRequest(AiOperation.PLAN, Prompts.PLAN, listOf(ChatTurn("user", "{\"today\":\"2026-11-02\"}")), Schemas.plan)

    @Test fun `own-key client sends model, structured output schema, effort, caching and fallback`() = runTest {
        val url = serve(200, message("""{"summary":"ok"}"""))
        val res = AnthropicDirectClient("sk-ant-test", url).call(req)
        assertEquals("""{"summary":"ok"}""", res.json)
        assertEquals(1200L, res.usage.inputTokens)
        assertEquals(800L, res.usage.outputTokens)
        assertEquals(900L, res.usage.cacheReadTokens)

        val (headers, body) = requests.single()
        assertEquals("sk-ant-test", headers.getValue("x-api-key").single())
        assertTrue(headers.getValue("anthropic-beta").single().contains("server-side-fallback-2026-07-01"))
        val json = AiJson.parseToJsonElement(body).jsonObject
        assertEquals("claude-sonnet-5-5", json.getValue("model").jsonPrimitive.content)
        assertEquals(16000, json.getValue("max_tokens").jsonPrimitive.content.toInt())
        assertEquals("default", json.getValue("fallbacks").jsonPrimitive.content)
        assertEquals("adaptive", json.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        val output = json.getValue("output_config").jsonObject
        assertEquals("medium", output.getValue("effort").jsonPrimitive.content)
        assertEquals("json_schema", output.getValue("format").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(Schemas.plan, output.getValue("format").jsonObject.getValue("schema"))
        val system = json.getValue("system").jsonArray.single().jsonObject
        assertEquals(Prompts.PLAN, system.getValue("text").jsonPrimitive.content)
        assertEquals("ephemeral", system.getValue("cache_control").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("user", json.getValue("messages").jsonArray.single().jsonObject.getValue("role").jsonPrimitive.content)
    }

    @Test fun `intake uses Haiku without the server-side fallback`() = runTest {
        val url = serve(200, message("{}", model = "claude-haiku-5-5"))
        AnthropicDirectClient("sk-ant-test", url).call(req.copy(op = AiOperation.INTAKE, schema = Schemas.intake))
        val (headers, body) = requests.single()
        val json = AiJson.parseToJsonElement(body).jsonObject
        assertEquals("claude-haiku-5-5", json.getValue("model").jsonPrimitive.content)
        assertTrue("fallbacks" !in json)
        assertTrue(headers["anthropic-beta"].orEmpty().none { it.contains("fallback") })
    }

    @Test fun `own-key errors are mapped to clear messages`() = runTest {
        suspend fun failsWith(status: Int, body: String): AiError {
            requests.clear(); server?.close()
            val url = serve(status, body)
            return try { AnthropicDirectClient("sk-ant-test", url).call(req); fail("expected an error"); throw IllegalStateException() } catch (e: AiError) { e }
        }
        val err = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        assertTrue(failsWith(401, err) is AiError.Auth)
        assertTrue(failsWith(200, message("{\"summ", stop = "max_tokens")) is AiError.Truncated)
        assertTrue(failsWith(200, message("", stop = "refusal")) is AiError.Refused)
        assertTrue(failsWith(400, """{"type":"error","error":{"type":"invalid_request_error","message":"bad"}}""") is AiError.Server)
    }

    // ---- server (proxy) mode ----

    private class FakeTransport(val responses: ArrayDeque<HttpResult>) : HttpTransport {
        val calls = mutableListOf<Triple<String, String?, String?>>()
        override suspend fun post(url: String, body: String, bearer: String?): HttpResult { calls += Triple(url, body, bearer); return responses.removeFirst() }
        override suspend fun delete(url: String, bearer: String?): HttpResult { calls += Triple(url, null, bearer); return responses.removeFirst() }
    }

    @Test fun `proxy client sends the operation, schema and token and reads usage`() = runTest {
        val t = FakeTransport(ArrayDeque(listOf(HttpResult(200, """{"output":"{\"reply\":\"ok\"}","model":"claude-sonnet-5-5","usage":{"input_tokens":10,"output_tokens":5,"cache_read_tokens":0}}"""))))
        val r = ProxyAiClient("https://ai.example.com/", { "tok-1" }, t).call(req.copy(op = AiOperation.ADJUST, schema = Schemas.adjust))
        assertEquals("""{"reply":"ok"}""", r.json)
        assertEquals(10L, r.usage.inputTokens)
        val (url, body, bearer) = t.calls.single()
        assertEquals("https://ai.example.com/v1/ai", url)
        assertEquals("tok-1", bearer)
        val json = AiJson.parseToJsonElement(body!!).jsonObject
        assertEquals("adjust", json.getValue("op").jsonPrimitive.content)
        assertEquals(Schemas.adjust, json.getValue("schema"))
        assertTrue("model" !in json) // the server picks the model
    }

    @Test fun `proxy client re-registers once on 401 and maps quota and network errors`() = runTest {
        val t = FakeTransport(ArrayDeque(listOf(HttpResult(401, "{}"), HttpResult(200, """{"output":"{}","model":"m"}"""))))
        val tokens = mutableListOf<Boolean>()
        ProxyAiClient("https://ai.example.com", { force -> tokens += force; if (force) "new" else "old" }, t).call(req)
        assertEquals(listOf(false, true), tokens)
        assertEquals(listOf("old", "new"), t.calls.map { it.third })

        suspend fun err(res: HttpResult) = try {
            ProxyAiClient("https://x", { "t" }, FakeTransport(ArrayDeque(listOf(res)))).call(req); null
        } catch (e: AiError) { e }
        assertTrue(err(HttpResult(429, """{"error":"quota_exceeded","message":"Monthly AI allowance used."}""")) is AiError.QuotaExceeded)
        assertTrue(err(HttpResult(429, """{"error":"rate_limited"}""")) is AiError.RateLimited)
        assertTrue(err(HttpResult(0, "{}")) is AiError.Network)
        assertTrue(err(HttpResult(422, """{"error":"refused"}""")) is AiError.Refused)
        assertTrue(err(HttpResult(502, "<html>")) is AiError.Server)
        assertTrue(err(HttpResult(200, """{"output":""}""")) is AiError.InvalidOutput)
    }

    // ---- schemas ----

    private fun checkObjects(path: String, e: JsonElement) {
        when (e) {
            is JsonObject -> {
                // Keys of a "properties" map are field names (a task has a field called "minimum"), not keywords.
                if (!path.endsWith(".properties")) listOf("minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems", "pattern").forEach {
                    assertTrue("$path uses unsupported keyword $it", it !in e)
                }
                if (e["type"] == JsonPrimitive("object")) {
                    assertEquals("$path additionalProperties", JsonPrimitive(false), e["additionalProperties"])
                    val props = e.getValue("properties").jsonObject.keys
                    val required = e.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()
                    assertEquals("$path required", props, required)
                }
                e.forEach { (k, v) -> checkObjects("$path.$k", v) }
            }
            is JsonArray -> e.forEachIndexed { i, v -> checkObjects("$path[$i]", v) }
            else -> Unit
        }
    }

    @Test fun `all schemas follow the structured-output rules`() {
        checkObjects("intake", Schemas.intake)
        checkObjects("plan", Schemas.plan)
        checkObjects("week", Schemas.week)
        checkObjects("adjust", Schemas.adjust)
    }

    @Test fun `system prompts contain nothing per-request so they cache`() {
        listOf(Prompts.INTAKE, Prompts.PLAN, Prompts.WEEK, Prompts.ADJUST).forEach { p ->
            assertTrue(!Regex("20\\d\\d-\\d\\d-\\d\\d").containsMatchIn(p))
        }
    }
}
