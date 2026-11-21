package app.levelup2027

import app.levelup2027.domain.PresetKind
import app.levelup2027.domain.RoutineSettings
import app.levelup2027.notify.CheckInScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import app.levelup2027.domain.DayOverride
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CheckInSchedulerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 21)
    private fun at(hhmm: String, date: LocalDate = today) = date.atStartOfDay(zone).plusMinutes(minuteOf(hhmm).toLong())
    private val daily = mapOf(PresetKind.DAILY to listOf(block("05:00", "07:00", "Gym"), block("09:00", "10:00", "Read")))

    @Test fun `arms the next block end in today's preset`() {
        val p = CheckInScheduler.next(RoutineSettings(presets = daily), at("06:00"))!!
        assertEquals(at("07:00"), p.at)
        assertEquals("Gym", p.block.label)
        assertEquals(today, p.date)
    }

    @Test fun `nothing when check-ins are off`() {
        assertNull(CheckInScheduler.next(RoutineSettings(presets = daily, checkInsEnabled = false), at("06:00")))
    }

    @Test fun `nothing when the preset is empty`() {
        assertNull(CheckInScheduler.next(RoutineSettings(), at("06:00")))
    }

    @Test fun `after today's last block, tomorrow's first Daily block is armed`() {
        val p = CheckInScheduler.next(RoutineSettings(presets = daily), at("10:00"))!!
        assertEquals("Gym", p.block.label)
        assertEquals(today.plusDays(1), p.date)
        assertEquals(at("07:00", today.plusDays(1)), p.at)
    }

    @Test fun `tomorrow always starts on Daily even after a Lazy switch today`() {
        val r = RoutineSettings(presets = daily + (PresetKind.LAZY to listOf(block("06:00", "09:00", "Slow"))), activeKind = PresetKind.LAZY, activeDate = today)
        val p = CheckInScheduler.next(r, at("09:30"))!!
        assertEquals("Gym", p.block.label)
        assertEquals(today.plusDays(1), p.date)
    }

    @Test fun `nothing when today is done and Daily is empty`() {
        val r = RoutineSettings(presets = mapOf(PresetKind.LAZY to listOf(block("06:00", "09:00", "Slow"))), activeKind = PresetKind.LAZY, activeDate = today)
        assertNull(CheckInScheduler.next(r, at("09:30")))
    }

    @Test fun `autumn clock change still fires at wall-clock end, once`() {
        val ny = ZoneId.of("America/New_York")
        val fallBack = LocalDate.of(2026, 11, 1)
        val now = ZonedDateTime.of(fallBack.atTime(6, 0), ny)
        val p = CheckInScheduler.next(RoutineSettings(presets = daily), now)!!
        assertEquals(ZonedDateTime.of(fallBack.atTime(7, 0), ny), p.at)
        // Firing at 07:00 moves on to the next block instead of re-arming Gym.
        assertEquals("Read", CheckInScheduler.next(RoutineSettings(presets = daily), p.at)!!.block.label)
    }

    @Test fun `spring clock change fires at wall-clock end`() {
        val ny = ZoneId.of("America/New_York")
        val springForward = LocalDate.of(2026, 3, 8)
        val now = ZonedDateTime.of(springForward.atTime(4, 0), ny)
        assertEquals(ZonedDateTime.of(springForward.atTime(7, 0), ny), CheckInScheduler.next(RoutineSettings(presets = daily), now)!!.at)
    }

    @Test fun `a block ending exactly now is not armed again`() {
        assertEquals("Read", CheckInScheduler.next(RoutineSettings(presets = daily), at("07:00"))!!.block.label)
    }

    @Test fun `switching mid-block arms the current block's end`() {
        val r = RoutineSettings(
            presets = daily + (PresetKind.LAZY to listOf(block("06:00", "09:00", "Slow morning"))),
            activeKind = PresetKind.LAZY, activeDate = today,
        )
        val p = CheckInScheduler.next(r, at("06:30"))!!
        assertEquals("Slow morning", p.block.label)
        assertEquals(at("09:00"), p.at)
    }

    @Test fun `yesterday's switch is ignored`() {
        val r = RoutineSettings(
            presets = daily + (PresetKind.LAZY to listOf(block("06:00", "09:00", "Slow morning"))),
            activeKind = PresetKind.LAZY, activeDate = today.minusDays(1),
        )
        assertEquals("Gym", CheckInScheduler.next(r, at("06:30"))!!.block.label)
    }

    @Test fun `midnight end carries the block's date`() {
        val r = RoutineSettings(presets = mapOf(PresetKind.DAILY to listOf(block("22:00", "24:00", "Wind down"))))
        val p = CheckInScheduler.next(r, at("23:00"))!!
        assertEquals(today.plusDays(1).atStartOfDay(zone), p.at)
        assertEquals(today, p.date)
    }

    @Test fun `uses wall-clock time across a zone`() {
        val ny = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(today.atTime(6, 0), ny)
        assertEquals(ZonedDateTime.of(today.atTime(7, 0), ny), CheckInScheduler.next(RoutineSettings(presets = daily), now)!!.at)
    }

    @Test fun `check-ins follow today's override`() {
        val r = RoutineSettings(presets = daily, todayOverride = DayOverride(today, PresetKind.DAILY, listOf(block("06:00", "08:00", "Gym"))))
        val p = CheckInScheduler.next(r, at("05:30"))!!
        assertEquals(at("08:00"), p.at)
        assertEquals("Gym", p.block.label)
    }
}
