package app.levelup2027

import app.levelup2027.ai.AiAvailability
import app.levelup2027.ai.AiClient
import app.levelup2027.ai.AiError
import app.levelup2027.ai.AiRequest
import app.levelup2027.ai.AiResponse
import app.levelup2027.ai.AiService
import app.levelup2027.ai.AiUsageInfo
import app.levelup2027.ai.ContextBuilder
import app.levelup2027.ai.HttpResult
import app.levelup2027.ai.HttpTransport
import app.levelup2027.ai.MemorySecretStore
import app.levelup2027.data.AiMode
import app.levelup2027.data.AiSettings
import app.levelup2027.data.AppSettings
import app.levelup2027.data.Capacity
import app.levelup2027.data.CompletionType
import app.levelup2027.data.Completion
import app.levelup2027.data.Goal
import app.levelup2027.data.MemorySettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AiServiceTest {
    private val usage = FakeAiUsageDao()
    private val secrets = MemorySecretStore()

    private class Scripted(var json: String = "{}") : AiClient {
        override val route = "own_key"
        val calls = mutableListOf<AiRequest>()
        override suspend fun call(request: AiRequest): AiResponse { calls += request; return AiResponse(json, request.op.model, AiUsageInfo(100, 50, 0)) }
    }

    private class NoNetwork : HttpTransport {
        override suspend fun post(url: String, body: String, bearer: String?) = HttpResult(0, "{}")
        override suspend fun delete(url: String, bearer: String?) = HttpResult(0, "{}")
    }

    private fun service(ai: AiSettings, client: Scripted = Scripted(), serverUrl: String = "") =
        AiService(MemorySettings(AppSettings(ai = ai)), secrets, usage, serverUrl, emptyList(), NoNetwork(), clock = { System.currentTimeMillis() }) { client }

    @Test fun `availability reflects mode, key, server and consent`() = runTest {
        assertEquals(AiAvailability.NOT_CONFIGURED, service(AiSettings(mode = AiMode.SERVER)).availability())
        assertEquals(AiAvailability.NEEDS_CONSENT, service(AiSettings(mode = AiMode.SERVER), serverUrl = "https://x").availability())
        assertEquals(AiAvailability.NOT_CONFIGURED, service(AiSettings(mode = AiMode.OWN_KEY, consentGiven = true)).availability())
        secrets.put(AiService.KEY_API, "sk-ant-x")
        assertEquals(AiAvailability.READY, service(AiSettings(mode = AiMode.OWN_KEY, consentGiven = true)).availability())
        assertEquals(AiAvailability.OFF, service(AiSettings(mode = AiMode.OFF, consentGiven = true)).availability())
    }

    @Test fun `no request is made without consent`() = runTest {
        secrets.put(AiService.KEY_API, "sk-ant-x")
        val c = Scripted()
        val r = runCatching { service(AiSettings(mode = AiMode.OWN_KEY), c).intake(LocalDate.now(), "get fit") }
        assertTrue(r.exceptionOrNull() is AiError.NoConsent)
        assertTrue(c.calls.isEmpty())
    }

    @Test fun `usage is logged per request without content, and the own-key monthly cap is enforced`() = runTest {
        secrets.put(AiService.KEY_API, "sk-ant-x")
        val c = Scripted("""{"context":{},"questions":[{"id":"q1","question":"How much time?"},{"id":"q2","question":"a"},{"id":"q3","question":"b"},{"id":"q4","question":"c"}],"ready_to_plan":true}""")
        val s = service(AiSettings(mode = AiMode.OWN_KEY, consentGiven = true, monthlyRequestLimit = 2), c)
        val intake = s.intake(LocalDate.now(), "get fit")
        assertEquals(3, intake.questions.size) // never more than three questions
        s.intake(LocalDate.now(), "again")
        assertEquals(2, usage.rows.size)
        assertEquals(100L, usage.rows.first().inputTokens)
        assertTrue(usage.rows.all { it.success && it.route == "own_key" })
        val third = runCatching { s.intake(LocalDate.now(), "third") }
        assertTrue(third.exceptionOrNull() is AiError.QuotaExceeded)
        assertEquals(2, c.calls.size)
    }

    @Test fun `unreadable output is reported, not saved`() = runTest {
        secrets.put(AiService.KEY_API, "sk-ant-x")
        val s = service(AiSettings(mode = AiMode.OWN_KEY, consentGiven = true), Scripted("not json"))
        assertTrue(runCatching { s.generatePlan("{}") }.exceptionOrNull() is AiError.InvalidOutput)
    }

    @Test fun `server mode without network fails cleanly and records the failure`() = runTest {
        val s = service(AiSettings(mode = AiMode.SERVER, consentGiven = true), serverUrl = "https://ai.example.com")
        val r = runCatching { s.adjust("{}") }
        assertTrue(r.exceptionOrNull() is AiError.Network)
    }

    @Test fun `adjust context is limited to a week and never includes notes`() {
        val today = LocalDate.of(2026, 11, 4)
        val old = task("old", date = today.minusDays(40)).copy(notes = "PRIVATE NOTE")
        val t = task("gym", rule = "DAILY").copy(notes = "SECRET")
        val comps = (1..60).map { Completion(taskId = "gym", taskTitle = "gym", scheduledDate = today.minusDays(it.toLong()), completionType = CompletionType.IDEAL, completedAt = 0, notes = "diary entry") }
        val json = ContextBuilder.adjust(today, LocalTime.NOON, listOf(t, old), comps, listOf(Goal("g", "Fit", "", "", today.plusDays(90), "", "")), emptyList(),
            Capacity(30, 60), false, emptySet(), emptyList())
        assertFalse(json.contains("SECRET") || json.contains("PRIVATE") || json.contains("diary"))
        val o = app.levelup2027.ai.AiJson.parseToJsonElement(json).jsonObject
        assertEquals(7, (o.getValue("next_seven_days") as kotlinx.serialization.json.JsonArray).size)
        assertTrue(json.length < 6000)
    }
}

class AiServiceGeminiTest {
    private val usage = FakeAiUsageDao()
    private val secrets = MemorySecretStore()

    private class Capture : HttpTransport {
        val bodies = mutableListOf<String>()
        val keys = mutableListOf<String>()
        override suspend fun post(url: String, body: String, bearer: String?) = HttpResult(0, "{}")
        override suspend fun delete(url: String, bearer: String?) = HttpResult(0, "{}")
        override suspend fun postWithHeaders(url: String, body: String, headers: Map<String, String>): HttpResult {
            bodies += body; keys += headers.getValue("x-goog-api-key")
            return HttpResult(200, """{"candidates":[{"content":{"parts":[{"text":"{\"reply\":\"ok\"}"}]},"finishReason":"STOP"}]}""")
        }
    }

    private fun service(http: Capture, ai: AiSettings = AiSettings(mode = AiMode.GEMINI, consentGiven = true), builtIn: List<String> = listOf("shared-1", "shared-2")) =
        AiService(MemorySettings(AppSettings(ai = ai)), secrets, usage, "", builtIn, http)

    @Test fun `built-in keys work out of the box, user keys replace them`() = runTest {
        val http = Capture()
        val s = service(http)
        assertEquals(AiAvailability.READY, s.availability())
        assertTrue(s.usingBuiltInGeminiKeys())
        s.adjust("{}"); s.adjust("{}")
        assertEquals(listOf("shared-1", "shared-2"), http.keys)
        assertEquals("gemini-shared", usage.rows.last().route)
        s.setUserGeminiKeys(listOf("mine-1", " mine-1 ", "mine-2"))
        assertEquals(listOf("mine-1", "mine-2"), s.userGeminiKeys())
        s.adjust("{}")
        assertEquals("mine-1", http.keys.last())
        assertEquals("gemini", usage.rows.last().route)
    }

    @Test fun `no keys at all means not configured`() = runTest {
        assertEquals(AiAvailability.NOT_CONFIGURED, service(Capture(), builtIn = emptyList()).availability())
    }

    @Test fun `shared keys are capped per phone per month, own keys are not`() = runTest {
        val http = Capture()
        val s = service(http)
        repeat(AiService.SHARED_MONTHLY_LIMIT) { s.adjust("{}") }
        assertTrue(runCatching { s.adjust("{}") }.exceptionOrNull() is AiError.QuotaExceeded)
        s.setUserGeminiKeys(listOf("mine"))
        s.adjust("{}")
        assertEquals("mine", http.keys.last())
    }

    @Test fun `standing instructions are added to the request payload, not the system prompt`() = runTest {
        val http = Capture()
        val s = service(http, AiSettings(mode = AiMode.GEMINI, consentGiven = true, instructions = "No tasks before 7am."))
        s.adjust("""{"today":"2026-11-04"}""")
        val body = kotlinx.serialization.json.Json.parseToJsonElement(http.bodies.single()).jsonObject
        val userText = body.getValue("contents").jsonArray.last().jsonObject.getValue("parts").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content
        val payload = kotlinx.serialization.json.Json.parseToJsonElement(userText).jsonObject
        assertEquals("No tasks before 7am.", payload.getValue("user_instructions").jsonPrimitive.content)
        assertEquals("2026-11-04", payload.getValue("today").jsonPrimitive.content)
        assertFalse(http.bodies.single().substringBefore("contents").contains("No tasks before 7am"))
    }
}
