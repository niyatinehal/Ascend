package app.levelup2027

import app.levelup2027.ai.AiJson
import app.levelup2027.ai.AiOperation
import app.levelup2027.ai.AiRequest
import app.levelup2027.ai.ChatTurn
import app.levelup2027.ai.ContextBuilder
import app.levelup2027.ai.GeminiClient
import app.levelup2027.ai.IntakeResult
import app.levelup2027.ai.KeyRing
import app.levelup2027.ai.PlanDraft
import app.levelup2027.ai.PlanMaterializer
import app.levelup2027.ai.PlanValidator
import app.levelup2027.ai.Prompts
import app.levelup2027.ai.QuestionAnswer
import app.levelup2027.ai.Schemas
import app.levelup2027.ai.UrlConnectionTransport
import app.levelup2027.data.Capacity
import app.levelup2027.data.Cat
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Properties

/**
 * Real Gemini calls through the app's own code path: intake → plan → validation → materialisation.
 * Skipped unless GEMINI_LIVE=1 (it uses free-tier quota). Keys come from local.properties.
 */
class GeminiLiveTest {
    @Test fun `live onboarding with real Gemini`() = runBlocking {
        assumeTrue(System.getenv("GEMINI_LIVE") == "1")
        val props = Properties().apply { File("../local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
        val keys = props.getProperty("geminiApiKeys").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        assumeTrue(keys.isNotEmpty())
        val client = GeminiClient(KeyRing(keys), modelOverride = System.getenv("GEMINI_MODEL").orEmpty(), http = UrlConnectionTransport(150_000))
        val today = LocalDate.of(2026, 10, 26)
        val text = "I want to get fit and learn Spanish. I work 9 to 6 and have about 45 minutes free on weekday evenings and 2 hours on weekends."

        var t0 = System.currentTimeMillis()
        val intakeRes = client.call(AiRequest(AiOperation.INTAKE, Prompts.INTAKE, listOf(ChatTurn("user", ContextBuilder.intake(today, text))), Schemas.intake))
        val intake = AiJson.decodeFromString(IntakeResult.serializer(), intakeRes.json)
        println("INTAKE ${intakeRes.model} ${System.currentTimeMillis() - t0} ms, in=${intakeRes.usage.inputTokens} out=${intakeRes.usage.outputTokens}")
        println("  weekday=${intake.context.weekdayFreeMinutes} weekend=${intake.context.weekendFreeMinutes} ready=${intake.readyToPlan}")
        intake.questions.forEach { println("  Q: ${it.question}") }

        t0 = System.currentTimeMillis()
        val payload = ContextBuilder.plan(today, today, text, intake, intake.questions.map { QuestionAnswer(it.question, "") }, Cat.DEFAULTS, emptyList(), Capacity())
        val planRes = client.call(AiRequest(AiOperation.PLAN, Prompts.PLAN, listOf(ChatTurn("user", payload)), Schemas.plan))
        val draft = AiJson.decodeFromString(PlanDraft.serializer(), planRes.json)
        println("PLAN ${planRes.model} ${System.currentTimeMillis() - t0} ms, in=${planRes.usage.inputTokens} out=${planRes.usage.outputTokens}")
        val v = PlanValidator.validate(draft, today, Cat.DEFAULTS)
        println("  summary: ${draft.summary}")
        println("  goals: " + v.draft.goals.joinToString { "${it.title} (by ${it.deadline.ifBlank { "-" }}${if (it.deadlineAssumed) ", assumed" else ""})" })
        println("  habits: " + v.draft.habits.joinToString { "${it.title} ${it.days} ${it.idealMinutes}/${it.minimumMinutes}m" })
        println("  first week: ${v.draft.firstWeek.size} tasks; load per day: ${v.dailyLoad.values}")
        println("  capacity: ${v.weekdayMinutes}/${v.weekendMinutes}; issues: ${v.issues.map { "${it.level}: ${it.message}" }}")
        v.draft.firstWeek.take(3).forEach { println("  e.g. ${it.date} ${it.title}: IDEAL ${it.ideal} | MIN ${it.minimum}") }
        check(!v.hasErrors) { "validation errors" }
        val m = PlanMaterializer().materialize(v, "live", Cat.DEFAULTS, emptyList(), emptyList())
        println("  materialised: ${m.goals.size} goals, ${m.tasks.size} tasks, ${m.milestones.size} milestones")
    }
}
