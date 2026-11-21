package app.levelup2027

import app.levelup2027.ai.PlanChange
import app.levelup2027.ai.ValidatedChanges
import app.levelup2027.domain.PresetKind
import app.levelup2027.ui.plan.shouldAutoApply
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class AdjustDecisionTest {
    private val simple = ValidatedChanges(listOf(PlanChange.SetLowEnergy(LocalDate.of(2026, 11, 21), "r")), emptyList())
    private val major = ValidatedChanges(listOf(PlanChange.SetPreset(PresetKind.LAZY, "r")), emptyList())

    @Test fun `assistant never auto-applies`() =
        assertFalse(shouldAutoApply(autoApplySimple = true, alwaysConfirm = true, validated = simple, needsClarification = false))

    @Test fun `simple changes auto-apply when allowed`() {
        assertTrue(shouldAutoApply(true, false, simple, false))
        assertFalse(shouldAutoApply(false, false, simple, false))
    }

    @Test fun `major changes never auto-apply`() = assertFalse(shouldAutoApply(true, false, major, false))

    @Test fun `clarifying questions never auto-apply`() = assertFalse(shouldAutoApply(true, false, simple, true))
}
