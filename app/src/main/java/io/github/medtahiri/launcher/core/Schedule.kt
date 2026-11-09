package io.github.medtahiri.launcher.core

import java.time.DayOfWeek
import java.time.LocalDateTime

/** Hours and days, shared by focus rules and profiles. Kept free of Android so it can be unit tested. */
object Schedule {
    private val TIME = Regex("([01][0-9]|2[0-3]):[0-5][0-9]|24:00")
    private val DAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val GROUPS = mapOf(
        "weekdays" to setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        "weekends" to setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    )

    private fun minutes(time: String) = time.substringBefore(':').toInt() * 60 + time.substringAfter(':').toInt()

    private fun days(names: List<String>): Set<DayOfWeek> =
        if (names.isEmpty()) DayOfWeek.entries.toSet() else names.flatMap { GROUPS[it] ?: setOfNotNull(DAYS[it]) }.toSet()

    /**
     * Whether [at] is within [from] to [to] on one of [days] (empty means every day). From 22:00 to 07:00 runs overnight,
     * belonging to the day it starts. Equal times mean all day.
     */
    fun isActive(from: String, to: String, days: List<String>, at: LocalDateTime): Boolean {
        val now = at.hour * 60 + at.minute
        val start = minutes(from)
        val end = minutes(to)
        val on = days(days)
        val today = at.dayOfWeek in on
        return when {
            start == end -> today
            start < end -> today && now >= start && now < end
            else -> (today && now >= start) || (at.dayOfWeek.minus(1) in on && now < end)
        }
    }

    /** What's wrong with these hours and days, each problem starting with [name]. */
    fun validate(name: String, from: String, to: String, days: List<String>): List<String> = buildList {
        listOf(from, to).filterNot { TIME.matches(it) }.forEach { add("$name: time must be HH:MM, like 09:30, not \"$it\"") }
        (days - DAYS.keys - GROUPS.keys).forEach { add("$name: unknown day \"$it\". Use mon to sun, weekdays or weekends") }
    }
}
