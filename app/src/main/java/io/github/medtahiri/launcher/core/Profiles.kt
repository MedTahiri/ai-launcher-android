package io.github.medtahiri.launcher.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.LocalDateTime

/**
 * A profile: another look for the same launcher, such as Work or Evening. It changes some settings, named by their path
 * in launcher.json (`"theme.accent"`, `"home.favorites"`), during set hours or when the person picks it.
 */
@Serializable
data class ProfileConfig(
    val name: String,
    /** "HH:MM". Without from and to, it's only used when picked on the phone. */
    val from: String? = null,
    val to: String? = null,
    /** mon to sun, weekdays or weekends. Empty means every day. */
    val days: List<String> = emptyList(),
    /** Settings to change, by path, with their values. */
    val set: Map<String, JsonElement> = emptyMap(),
)

object Profiles {
    /** Settings a profile can't change: the name, the language (read before the profile is), and profiles of its own. */
    private val FIXED = setOf("name", "profiles", "language")

    /** The profile in use: the one picked on the phone if any ("" means automatic), else the first whose hours match. */
    fun active(profiles: List<ProfileConfig>, picked: String, at: LocalDateTime): ProfileConfig? {
        if (picked.isNotEmpty()) profiles.firstOrNull { it.name == picked }?.let { return it }
        return profiles.firstOrNull { it.from != null && it.to != null && Schedule.isActive(it.from, it.to, it.days, at) }
    }

    /** [config] with [profile]'s settings. */
    fun apply(config: JsonObject, profile: ProfileConfig?): JsonObject =
        profile?.set?.entries?.fold(config) { json, (path, value) -> PhoneSettings.withValue(json, path, value) } ?: config

    /** Problems with the profiles themselves; what their settings do is checked by applying them (see parseConfig). */
    fun validate(profiles: List<ProfileConfig>): List<String> = buildList {
        profiles.forEachIndexed { i, profile ->
            val name = "profiles[${i + 1}]"
            if (profile.name.isBlank()) add("$name needs a name")
            if (profile.set.isEmpty()) add("$name (${profile.name}) doesn't set anything")
            if ((profile.from == null) != (profile.to == null)) add("$name (${profile.name}) needs both from and to, or neither")
            if (profile.from != null && profile.to != null) addAll(Schedule.validate(name, profile.from, profile.to, profile.days))
            else if (profile.days.isNotEmpty()) add("$name (${profile.name}): days need from and to")
            profile.set.keys.filter { it.substringBefore('.') in FIXED }.forEach { add("$name (${profile.name}) can't change $it") }
        }
        profiles.groupBy { it.name.trim().lowercase() }.filter { it.value.size > 1 }.forEach {
            add("profiles: two are called \"${it.value.first().name}\"")
        }
    }
}
