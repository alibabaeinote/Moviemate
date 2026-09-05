package com.moviemate.app.ui.screens.match

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Watch-time formatting and the suggested slots.
 *
 * Kept out of the composables so it can be tested: "is this tonight or
 * tomorrow" is a calendar question with real edge cases, and none of them are
 * reachable through a UI test.
 */

/** "Tonight at 21:00", "Tomorrow at 20:30", or a dated form further out. */
fun formatWatchTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val clock = String.format(Locale.getDefault(), "%02d:%02d", hourOf(millis), minuteOf(millis))
    return when (dayOffset(now, millis)) {
        0 -> if (hourOf(millis) >= EVENING_HOUR) "tonight at $clock" else "today at $clock"
        1 -> "tomorrow at $clock"
        else -> "${dayName(millis)} at $clock"
    }
}

/**
 * Whole calendar days between two instants, not a division by 24 hours.
 *
 * 23:30 and 00:30 are forty minutes apart and on different days; a duration
 * would call that the same day and label tomorrow's slot "tonight".
 */
internal fun dayOffset(from: Long, to: Long): Int {
    val start = midnightOf(from)
    val end = midnightOf(to)
    return TimeUnit.MILLISECONDS.toDays(end - start).toInt()
}

/**
 * The times offered on the scheduling screen.
 *
 * Anchored to this evening, and rolled to tomorrow once a slot has passed —
 * offering someone 20:00 at half past nine is offering them nothing.
 */
fun suggestedWatchTimes(now: Long = System.currentTimeMillis()): List<Long> =
    SUGGESTED_HOURS.map { hour ->
        val slot = atHour(now, hour)
        if (slot > now) slot else atHour(now + DAY_MS, hour)
    }

private fun midnightOf(millis: Long): Long = calendarAt(millis).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun atHour(millis: Long, hour: Int): Long = calendarAt(millis).apply {
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun calendarAt(millis: Long): Calendar =
    Calendar.getInstance().apply { timeInMillis = millis }

private fun hourOf(millis: Long) = calendarAt(millis).get(Calendar.HOUR_OF_DAY)

private fun minuteOf(millis: Long) = calendarAt(millis).get(Calendar.MINUTE)

private fun dayName(millis: Long): String =
    java.text.SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(millis))

private const val EVENING_HOUR = 17
private const val DAY_MS = 24L * 60 * 60 * 1000
private val SUGGESTED_HOURS = listOf(20, 21, 22)
