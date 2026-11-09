package io.github.medtahiri.launcher.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Focus rules, from launcher.json: during set hours, hide distracting apps or make the person wait a few seconds before
 * one opens. Friction, not a lock: hidden apps are one press away, and nothing stops other ways of opening them.
 */
@Serializable
data class FocusConfig(
    val rules: List<FocusRule> = emptyList(),
    /** Whether the drawer offers "Show" for hidden apps, until the person leaves the launcher. */
    val allowShowAnyway: Boolean = true,
)

@Serializable
data class FocusRule(
    /** Package names. */
    val apps: List<String> = emptyList(),
    /** App categories, as apps report them: game, social, video, audio, image, news, maps, productivity. */
    val categories: List<String> = emptyList(),
    /** "HH:MM". A rule from 22:00 to 07:00 runs overnight. Equal times mean all day. */
    val from: String = "00:00",
    /** "HH:MM", up to "24:00". */
    val to: String = "24:00",
    /** mon to sun, weekdays or weekends. Empty means every day. For an overnight rule, the day it starts. */
    val days: List<String> = emptyList(),
    /** "hide" or "pause" (wait before opening). */
    val action: String = "hide",
    /** For pause: how long to wait, in seconds. */
    val seconds: Int = 5,
)

/** What the rules do now: which packages are hidden, which pause and for how long, and until when apps are hidden. */
data class FocusNow(val hidden: Set<String>, val pauses: Map<String, Int>, val hiddenUntil: String?) {
    companion object {
        val NONE = FocusNow(emptySet(), emptyMap(), null)
    }
}

object FocusRules {
    val CATEGORIES = setOf("game", "social", "video", "audio", "image", "news", "maps", "productivity")
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

    private fun days(rule: FocusRule): Set<DayOfWeek> =
        if (rule.days.isEmpty()) DayOfWeek.entries.toSet() else rule.days.flatMap { GROUPS[it] ?: setOfNotNull(DAYS[it]) }.toSet()

    /** Whether the rule applies at this moment. */
    fun isActive(rule: FocusRule, at: LocalDateTime): Boolean {
        val now = at.hour * 60 + at.minute
        val from = minutes(rule.from)
        val to = minutes(rule.to)
        val days = days(rule)
        val today = at.dayOfWeek in days
        return when {
            from == to -> today
            from < to -> today && now >= from && now < to
            else -> (today && now >= from) || (at.dayOfWeek.minus(1) in days && now < to) // overnight
        }
    }

    /** What the rules do at [at], for apps given as package name and category. */
    fun now(rules: List<FocusRule>, apps: List<Pair<String, String>>, at: LocalDateTime): FocusNow {
        val active = rules.filter { isActive(it, at) }
        if (active.isEmpty()) return FocusNow.NONE
        fun matching(rule: FocusRule) = apps.filter { (pkg, category) -> pkg in rule.apps || category in rule.categories }.map { it.first }
        val hiding = active.filter { it.action == "hide" }
        val hidden = hiding.flatMap(::matching).toSet()
        val pauses = mutableMapOf<String, Int>()
        active.filter { it.action == "pause" }.forEach { rule ->
            matching(rule).forEach { pauses[it] = maxOf(pauses[it] ?: 0, rule.seconds) }
        }
        val until = hiding.map { it.to }.distinct().singleOrNull()?.takeIf { it != "24:00" && hidden.isNotEmpty() }
        return FocusNow(hidden, pauses, until)
    }

    fun validate(focus: FocusConfig, isPackage: (String) -> Boolean): List<String> = buildList {
        focus.rules.forEachIndexed { i, rule ->
            val name = "focus.rules[${i + 1}]"
            if (rule.apps.isEmpty() && rule.categories.isEmpty()) add("$name needs apps or categories")
            rule.apps.filterNot(isPackage).forEach { add("$name: \"$it\" isn't a package name") }
            (rule.categories - CATEGORIES).forEach { add("$name: unknown category \"$it\". Categories: ${CATEGORIES.sorted().joinToString()}") }
            listOf(rule.from, rule.to).filterNot { TIME.matches(it) }.forEach { add("$name: time must be HH:MM, like 09:30, not \"$it\"") }
            (rule.days - DAYS.keys - GROUPS.keys).forEach { add("$name: unknown day \"$it\". Use mon to sun, weekdays or weekends") }
            if (rule.action !in setOf("hide", "pause")) add("$name: action must be hide or pause, not \"${rule.action}\"")
            if (rule.seconds !in 1..60) add("$name: seconds must be from 1 to 60, not ${rule.seconds}")
        }
    }
}
