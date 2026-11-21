package app.levelup2027.screenshots

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.levelup2027.LevelUpApp
import app.levelup2027.MainActivity
import app.levelup2027.domain.Block
import app.levelup2027.domain.Dates
import app.levelup2027.domain.RoutineSetup
import app.levelup2027.notify.PendingCheckIn
import app.levelup2027.ui.checkin.CheckInActivity
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZonedDateTime

/**
 * Walks the routine feature in the real app and saves a PNG per step. Not part of the normal suite:
 * runs only with -PscreenshotDir=<folder>.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class RoutineScreenshots {
    @get:Rule val compose = createEmptyComposeRule()

    private val dir: String? = System.getProperty("screenshotDir")

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File(dir!!, "$name.png").path)
    }

    /** Saves go through DataStore on a background thread, so wait for the next screen to appear. */
    private fun waitFor(t: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun text(t: String): SemanticsNodeInteraction = compose.onNodeWithText(t, substring = true)

    @Test fun walkThrough() {
        assumeTrue(dir != null)
        val app = ApplicationProvider.getApplicationContext<LevelUpApp>()
        val c = app.container
        runBlocking {
            c.repository.ensureDefaults()
            c.repository.loadExamplePlan()
        }

        // ---- New user: "Your day" after onboarding ----
        var main = ActivityScenario.launch(MainActivity::class.java)
        shot("01-your-day-daily-empty")

        val labels = listOf("Gym", "Breakfast & reading", "Commute", "Deep work")
        labels.forEachIndexed { i, label ->
            text("Add block").performClick()
            compose.onAllNodes(hasSetTextAction())[i].performTextInput(label)
        }
        shot("02-your-day-daily-blocks")

        text("Save and continue").performClick()
        waitFor("Start from a copy")
        shot("03-your-day-productive-copy-offer")

        text("Start from a copy").performClick()
        shot("04-your-day-productive-copied")
        text("Save and continue").performClick()
        waitFor("For low days")
        shot("05-your-day-lazy")
        text("Set up later").performClick()
        waitFor("Finish")
        shot("06-your-day-checkins-page")
        text("Finish").performClick()
        waitFor("Productive")

        // ---- Today: preset switcher ----
        shot("07-today-daily")
        compose.onNodeWithText("Lazy").performClick()
        waitFor("minimums only")
        shot("08-today-lazy-minimums")
        compose.onNodeWithText("Daily").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("minimums only", substring = true).fetchSemanticsNodes().isEmpty() }

        // ---- Settings → Routine ----
        compose.onAllNodesWithText("Settings").onLast().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Block check-ins"))
        shot("09-settings-routine")

        compose.onAllNodesWithText("Daily Routine").onFirst().performClick()
        shot("10-editor-sheet")
        if (compose.onAllNodesWithText("Describe it instead").fetchSemanticsNodes().isNotEmpty()) {
            text("Describe it instead").performClick()
            shot("11-editor-describe-your-day")
        }
        text("Add block").performClick()
        compose.onAllNodesWithText("Save").onLast().performClick()
        waitFor("needs a name")
        shot("12-editor-validation-error")
        main.close()

        // ---- Existing user: one-time prompt on Today ----
        runBlocking { c.repository.updateSettings { it.copy(routine = it.routine.copy(setup = RoutineSetup.PROMPT, promptDismissed = false)) } }
        main = ActivityScenario.launch(MainActivity::class.java)
        waitFor("Set up your daily routine")
        shot("13-today-existing-user-prompt")
        main.close()

        // ---- Block check-in ----
        val today = Dates.today()
        val intent = PendingCheckIn(today, Block(360, 420, "Gym"), ZonedDateTime.now())
            .putInto(Intent(app, CheckInActivity::class.java))
        val checkIn = ActivityScenario.launch<CheckInActivity>(intent)
        waitFor("Morning routine")
        shot("14-checkin")
        text("Morning routine").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Done")).fetchSemanticsNodes().isNotEmpty() }
        shot("15-checkin-ticked")
        checkIn.close()
    }
}
