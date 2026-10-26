package app.levelup2027

import app.levelup2027.ai.AiOperation
import app.levelup2027.ai.Prompts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The AI server only accepts the system prompts this app ships (worker/src/prompt-hashes.json).
 * If you change a prompt, run the tests with WRITE_PROMPT_HASHES=1 to update the file, then
 * redeploy the server before releasing the app.
 */
class PromptHashesTest {
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private val prompts = mapOf(
        AiOperation.INTAKE.wireName to Prompts.INTAKE,
        AiOperation.PLAN.wireName to Prompts.PLAN,
        AiOperation.WEEK.wireName to Prompts.WEEK,
        AiOperation.ADJUST.wireName to Prompts.ADJUST,
    )

    @Test fun `server prompt allowlist matches the app's prompts`() {
        val file = listOf(File("../worker/src/prompt-hashes.json"), File("worker/src/prompt-hashes.json")).first { it.parentFile.exists() }
        val expected = JsonObject(prompts.mapValues { JsonPrimitive(sha256(it.value)) })
        if (System.getenv("WRITE_PROMPT_HASHES") == "1") file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), expected) + "\n")
        assertEquals("Prompts changed: run tests with WRITE_PROMPT_HASHES=1 and redeploy the worker", expected, Json.parseToJsonElement(file.readText()).jsonObject)
    }
}
