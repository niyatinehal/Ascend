package app.levelup2027

import app.levelup2027.ui.plan.AdjustState
import app.levelup2027.ui.plan.AiBlock
import app.levelup2027.ui.plan.blockedBy
import app.levelup2027.ui.plan.friendlyRejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdjustSheetTextTest {
    @Test fun `rejections drop the change type prefix`() {
        assertEquals("\"Gym\" is already done", friendlyRejection("COMPLETE_TASK: \"Gym\" is already done"))
        assertEquals("Lazy Day is already today's preset", friendlyRejection("SET_PRESET: Lazy Day is already today's preset"))
        assertEquals("Unknown change type", friendlyRejection("FOO_BAR: unknown change type"))
    }

    @Test fun `rejections without a type prefix are kept, only capitalised`() {
        assertEquals("Blocks overlap: Gym and Work", friendlyRejection("blocks overlap: Gym and Work"))
        assertEquals("Note: keep this", friendlyRejection("Note: keep this"))
    }

    @Test fun `a blocked send keeps the request so it is sent after consent`() {
        val s = AdjustState(input = "").blockedBy(AiBlock.NeedsConsent, "Switch to Lazy Day")
        assertEquals("Switch to Lazy Day", s.input)
        assertEquals(AiBlock.NeedsConsent, s.block)
        assertNull(s.proposal)
    }
}
