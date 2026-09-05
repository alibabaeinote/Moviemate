package com.moviemate.app.ui.screens.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The calendar arithmetic behind "tonight" and "tomorrow".
 *
 * Worth testing because the tempting implementation — dividing a duration by 24
 * hours — is wrong in exactly the case that shows up every evening: 23:30 and
 * 00:30 are forty minutes apart and on different days.
 */
class WatchTimeTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    @Test
    fun `forty minutes across midnight is a day apart`() {
        val lateTonight = at(2026, Calendar.MARCH, 10, 23, 30)
        val earlyTomorrow = at(2026, Calendar.MARCH, 11, 0, 30)

        assertEquals(1, dayOffset(lateTonight, earlyTomorrow))
    }

    @Test
    fun `twenty three hours inside one day is not a day apart`() {
        val earlyMorning = at(2026, Calendar.MARCH, 10, 0, 30)
        val lateEvening = at(2026, Calendar.MARCH, 10, 23, 30)

        assertEquals(0, dayOffset(earlyMorning, lateEvening))
    }

    @Test
    fun `an evening slot today reads as tonight`() {
        val now = at(2026, Calendar.MARCH, 10, 18, 0)
        val slot = at(2026, Calendar.MARCH, 10, 21, 0)

        assertEquals("tonight at 21:00", formatWatchTime(slot, now))
    }

    @Test
    fun `an afternoon slot today is today, not tonight`() {
        val now = at(2026, Calendar.MARCH, 10, 9, 0)
        val slot = at(2026, Calendar.MARCH, 10, 14, 30)

        assertEquals("today at 14:30", formatWatchTime(slot, now))
    }

    @Test
    fun `the next day reads as tomorrow`() {
        val now = at(2026, Calendar.MARCH, 10, 23, 0)
        val slot = at(2026, Calendar.MARCH, 11, 20, 0)

        assertEquals("tomorrow at 20:00", formatWatchTime(slot, now))
    }

    /**
     * Offering 20:00 at half past nine is offering nothing, so a passed slot
     * has to roll to the next day rather than simply being listed.
     */
    @Test
    fun `passed slots roll forward and every offer is in the future`() {
        val now = at(2026, Calendar.MARCH, 10, 21, 30)
        val slots = suggestedWatchTimes(now)

        assertEquals(3, slots.size)
        slots.forEach { assertTrue("slot $it is not in the future", it > now) }
    }

    @Test
    fun `early in the day every slot is still tonight`() {
        val now = at(2026, Calendar.MARCH, 10, 9, 0)
        val slots = suggestedWatchTimes(now)

        slots.forEach { assertEquals(0, dayOffset(now, it)) }
    }
}
