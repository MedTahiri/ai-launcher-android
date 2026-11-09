package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.core.FocusRule
import io.github.medtahiri.launcher.core.FocusRules
import io.github.medtahiri.launcher.core.parseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FocusRulesTest {
    // 2026-11-09 is a Monday.
    private fun at(day: Int, time: String) = LocalDateTime.parse("2026-11-%02dT$time".format(day))

    @Test fun dayRuleCoversItsHoursOnly() {
        val rule = FocusRule(apps = listOf("a.b"), from = "09:00", to = "18:00")
        assertFalse(FocusRules.isActive(rule, at(9, "08:59")))
        assertTrue(FocusRules.isActive(rule, at(9, "09:00")))
        assertTrue(FocusRules.isActive(rule, at(9, "17:59")))
        assertFalse(FocusRules.isActive(rule, at(9, "18:00")))
    }

    @Test fun overnightRuleCrossesMidnightFromItsStartDay() {
        val rule = FocusRule(apps = listOf("a.b"), from = "22:00", to = "07:00", days = listOf("fri"))
        assertTrue(FocusRules.isActive(rule, at(13, "23:30"))) // Friday night
        assertTrue(FocusRules.isActive(rule, at(14, "06:59"))) // Saturday morning, still Friday's rule
        assertFalse(FocusRules.isActive(rule, at(14, "07:00")))
        assertFalse(FocusRules.isActive(rule, at(13, "06:00"))) // Friday morning belongs to Thursday
    }

    @Test fun weekdaysAndWeekends() {
        val rule = FocusRule(apps = listOf("a.b"), days = listOf("weekdays"))
        assertTrue(FocusRules.isActive(rule, at(13, "12:00")))
        assertFalse(FocusRules.isActive(rule, at(14, "12:00")))
    }

    @Test fun untilMidnightAndAllDay() {
        assertTrue(FocusRules.isActive(FocusRule(from = "20:00", to = "24:00"), at(9, "23:59")))
        assertTrue(FocusRules.isActive(FocusRule(from = "08:00", to = "08:00"), at(9, "03:00")))
    }

    @Test fun hidesByPackageAndCategoryAndPausesTheLongest() {
        val rules = listOf(
            FocusRule(categories = listOf("social"), from = "09:00", to = "18:00"),
            FocusRule(apps = listOf("yt"), action = "pause", seconds = 5),
            FocusRule(apps = listOf("yt"), action = "pause", seconds = 10),
        )
        val now = FocusRules.now(rules, listOf("ig" to "social", "yt" to "video", "maps" to "maps"), at(9, "10:00"))
        assertEquals(setOf("ig"), now.hidden)
        assertEquals(mapOf("yt" to 10), now.pauses)
        assertEquals("18:00", now.hiddenUntil)
    }

    @Test fun nothingOutsideTheHours() {
        val rules = listOf(FocusRule(categories = listOf("social"), from = "09:00", to = "18:00"))
        assertEquals(emptySet<String>(), FocusRules.now(rules, listOf("ig" to "social"), at(9, "19:00")).hidden)
    }

    @Test fun badRulesAreReported() {
        val problems = parseConfig(
            """{"focus":{"rules":[{"categories":["socials"],"from":"9:00","days":["monday"],"action":"block","seconds":0},{}]}}""",
            emptySet(),
        ).problems
        assertEquals(6, problems.size)
    }
}
