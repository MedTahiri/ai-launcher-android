package io.github.medtahiri.launcher.core

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Settings changed on the phone, in the Customize screen, kept on top of the packaged launcher.json.
 *
 * Each change remembers the launcher.json value it replaced. When a new build's launcher.json changes that same setting,
 * the newer one wins and the phone's change is dropped, so an agent's edit is never silently ignored. Settings it didn't
 * touch keep the phone's value.
 */
object PhoneSettings {
    private const val FILE = "phone-settings.json"

    /** The value at a dotted path like `theme.accent`, or JsonNull when it isn't set. */
    fun valueAt(obj: JsonObject, path: String): JsonElement =
        path.split('.').fold<String, JsonElement?>(obj) { node, key -> (node as? JsonObject)?.get(key) } ?: JsonNull

    fun withValue(obj: JsonObject, path: String, value: JsonElement): JsonObject {
        val key = path.substringBefore('.')
        val rest = path.substringAfter('.', "")
        val child = if (rest.isEmpty()) value else withValue(obj[key] as? JsonObject ?: JsonObject(emptyMap()), rest, value)
        return JsonObject(obj + (key to child))
    }

    /** launcher.json with the phone's changes that still apply. */
    fun apply(packaged: JsonObject, saved: JsonObject): JsonObject =
        current(packaged, saved).entries.fold(packaged) { config, (path, change) ->
            withValue(config, path, change.jsonObject["value"] ?: JsonNull)
        }

    /** The changes whose launcher.json value hasn't changed since. */
    fun current(packaged: JsonObject, saved: JsonObject): JsonObject = JsonObject(
        saved.filter { (path, change) -> (change as? JsonObject)?.get("base") == valueAt(packaged, path) },
    )

    /** [saved] with [path] set to [value]. Setting it back to launcher.json's value removes the change. */
    fun record(packaged: JsonObject, saved: JsonObject, path: String, value: JsonElement): JsonObject {
        val base = valueAt(packaged, path)
        if (value == base) return JsonObject(saved - path)
        return JsonObject(saved + (path to buildJsonObject { put("value", value); put("base", base) }))
    }

    /** The settings the Customize screen can change. */
    val CUSTOMIZABLE = listOf(
        "language", "home.style", "theme.mode", "theme.background", "theme.accent", "theme.font", "theme.textScale",
        "home.columns", "home.iconSize", "home.showLabels", "home.favorites", "home.notificationDots",
    )

    private val encoder = Json { encodeDefaults = true }

    /** The customizable settings that differ between two configs, by path, with their new values. */
    fun changes(before: LauncherConfig, after: LauncherConfig): Map<String, JsonElement> {
        val a = encoder.encodeToJsonElement(LauncherConfig.serializer(), before).jsonObject
        val b = encoder.encodeToJsonElement(LauncherConfig.serializer(), after).jsonObject
        return CUSTOMIZABLE.filter { valueAt(a, it) != valueAt(b, it) }.associateWith { valueAt(b, it) }
    }

    fun load(context: Context): JsonObject = runCatching {
        Json.parseToJsonElement(File(context.filesDir, FILE).readText()).jsonObject
    }.getOrDefault(JsonObject(emptyMap()))

    fun save(context: Context, saved: JsonObject) {
        File(context.filesDir, FILE).writeText(saved.toString())
    }

    fun reset(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
