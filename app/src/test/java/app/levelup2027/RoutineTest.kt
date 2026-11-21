package app.levelup2027

import app.levelup2027.data.Cat
import app.levelup2027.domain.Block
import app.levelup2027.domain.Occurrence
import app.levelup2027.domain.PresetKind
import app.levelup2027.domain.Routine
import app.levelup2027.domain.RoutineSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

fun minuteOf(hhmm: String): Int = hhmm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
fun block(start: String, end: String, label: String) = Block(minuteOf(start), minuteOf(end), label)

class RoutineTest {
    private val day = LocalDate.of(2026, 11, 21)

    @Test fun `valid preset passes`() {
        assertEquals(emptyList<String>(), Routine.validate(listOf(block("05:00", "07:00", "Gym"), block("07:00", "08:00", "Breakfast"))))
    }

    @Test fun `overlap is rejected`() {
        assertTrue(Routine.validate(listOf(block("05:00", "07:00", "Gym"), block("06:30", "08:00", "Run"))).isNotEmpty())
    }

    @Test fun `end before or equal to start is rejected`() {
        assertTrue(Routine.validate(listOf(block("07:00", "07:00", "X"))).isNotEmpty())
        assertTrue(Routine.validate(listOf(block("23:00", "00:00", "Late"))).isNotEmpty())
    }

    @Test fun `block ending at midnight is allowed`() {
        assertEquals(emptyList<String>(), Routine.validate(listOf(block("22:00", "24:00", "Wind down"))))
    }

    @Test fun `times outside the day are rejected`() {
        assertTrue(Routine.validate(listOf(Block(-10, 60, "X"))).isNotEmpty())
        assertTrue(Routine.validate(listOf(Block(1380, 1500, "X"))).isNotEmpty())
    }

    @Test fun `blank or over-long label is rejected`() {
        assertTrue(Routine.validate(listOf(block("05:00", "06:00", "  "))).isNotEmpty())
        assertTrue(Routine.validate(listOf(block("05:00", "06:00", "x".repeat(41)))).isNotEmpty())
    }

    @Test fun `gaps are allowed`() {
        assertEquals(emptyList<String>(), Routine.validate(listOf(block("05:00", "06:00", "A"), block("09:00", "10:00", "B"))))
    }

    @Test fun `normalized trims labels and sorts by start`() {
        val n = Routine.normalized(listOf(block("09:00", "10:00", " Read "), block("05:00", "06:00", "Gym")))
        assertEquals(listOf("Gym", "Read"), n.map { it.label })
    }

    @Test fun `switch applies only on its date`() {
        val r = RoutineSettings(activeKind = PresetKind.LAZY, activeDate = day)
        assertEquals(PresetKind.LAZY, Routine.activeKind(r, day))
        assertEquals(PresetKind.DAILY, Routine.activeKind(r, day.plusDays(1)))
    }

    @Test fun `blocksFor uses the active preset`() {
        val r = RoutineSettings(
            presets = mapOf(PresetKind.DAILY to listOf(block("05:00", "07:00", "Gym")), PresetKind.LAZY to listOf(block("09:00", "11:00", "Slow"))),
            activeKind = PresetKind.LAZY, activeDate = day,
        )
        assertEquals("Slow", Routine.blocksFor(r, day).single().label)
        assertEquals("Gym", Routine.blocksFor(r, day.plusDays(1)).single().label)
    }

    @Test fun `current and next block`() {
        val bl = listOf(block("05:00", "07:00", "Gym"), block("09:00", "10:00", "Read"))
        assertEquals("Gym", Routine.currentBlock(bl, minuteOf("06:59"))?.label)
        assertNull(Routine.currentBlock(bl, minuteOf("07:00")))
        assertEquals("Read", Routine.nextEnding(bl, minuteOf("07:00"))?.label)
        assertNull(Routine.nextEnding(bl, minuteOf("10:00")))
    }

    @Test fun `matching tasks come first, others keep order`() {
        val read = Occurrence(task("Read 20 pages", category = Cat.LEARNING), day, null)
        val gym = Occurrence(task("Gym session", category = Cat.HEALTH), day, null)
        val walk = Occurrence(task("Evening walk", category = Cat.HEALTH).copy(exactTime = "05:30"), day, null)
        val names = mapOf(Cat.LEARNING to "Learning", Cat.HEALTH to "Health")

        val ordered = Routine.orderForBlock(block("05:00", "07:00", "Gym"), listOf(read, gym, walk), names)

        assertEquals(listOf("Gym session", "Evening walk", "Read 20 pages"), ordered.map { it.task.title })
    }

    @Test fun `category name counts as a match`() {
        val read = Occurrence(task("Read 20 pages", category = Cat.LEARNING), day, null)
        val pushups = Occurrence(task("Push-ups", category = Cat.FITNESS), day, null)
        val ordered = Routine.orderForBlock(block("05:00", "07:00", "Fitness"), listOf(read, pushups), mapOf(Cat.FITNESS to "Fitness"))
        assertEquals(listOf("Push-ups", "Read 20 pages"), ordered.map { it.task.title })
    }

    @Test fun `no match keeps original order`() {
        val a = Occurrence(task("Alpha"), day, null)
        val b = Occurrence(task("Beta"), day, null)
        assertEquals(listOf(a, b), Routine.orderForBlock(block("05:00", "07:00", "Gym"), listOf(a, b), emptyMap()))
    }

    @Test fun `format shows clock time`() {
        assertEquals("5:00", Routine.format(300))
        assertEquals("19:30", Routine.format(1170))
        assertEquals("0:00", Routine.format(1440))
    }
}
