package io.github.medtahiri.launcher.core

import io.github.medtahiri.launcher.R
import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Backs up what the person set on the phone, to move it to a new phone or a reinstall: Customize changes, the picked
 * profile, and the glass style's icon order and dock. Widgets can't move: Android ties them to this install.
 */
object Backup {
    private const val FORMAT = "ai-launcher-backup"
    private val prettyJson = Json { prettyPrint = true }

    private fun strings(context: Context, prefsName: String): JsonObject {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return JsonObject(prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to JsonPrimitive(it) } }.toMap())
    }

    fun export(context: Context): String = prettyJson.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", JsonPrimitive(FORMAT))
            put("version", JsonPrimitive(1))
            put("phoneSettings", PhoneSettings.load(context))
            put("profile", JsonPrimitive(PickedProfile.get(context)))
            put("glass", strings(context, "glass_layout"))
        },
    )

    /** Restores a backup. Returns why it can't, as a string resource, or null when done. */
    fun import(context: Context, text: String): Int? {
        val backup = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return R.string.backup_not_backup
        if (backup["format"]?.jsonPrimitive?.content != FORMAT) return R.string.backup_not_backup
        (backup["phoneSettings"] as? JsonObject)?.let { PhoneSettings.save(context, it) }
        backup["profile"]?.jsonPrimitive?.content?.let { PickedProfile.set(context, it) }
        (backup["glass"] as? JsonObject)?.let { glass ->
            val edit = context.getSharedPreferences("glass_layout", Context.MODE_PRIVATE).edit().clear()
            glass.forEach { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { edit.putString(k, it.content) } }
            edit.apply()
        }
        return null
    }
}

/** The profile picked on the phone, or "" for automatic. */
object PickedProfile {
    private fun prefs(context: Context) = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun get(context: Context): String = prefs(context).getString("profile", "").orEmpty()
    fun set(context: Context, name: String) = prefs(context).edit().putString("profile", name).apply()
}
