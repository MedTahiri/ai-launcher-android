package io.github.medtahiri.launcher.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.Locale

/**
 * The launcher's own language, from launcher.json's `language` (or the Customize screen). "auto" follows the phone.
 * Applied to the activity before anything is drawn, with the right-to-left layout that Arabic needs.
 */
object Language {
    /** [base] in the launcher's language. */
    fun wrap(base: Context): Context {
        val tag = setting(base)
        val locale = if (tag == "auto") Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
        Locale.setDefault(locale) // dates and numbers follow it too
        if (tag == "auto") return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** launcher.json's language, with a change made on the phone. Profiles can't change it: see Profiles. */
    private fun setting(context: Context): String = runCatching {
        val text = LiveConfig.read(context) ?: context.assets.open("launcher.json").bufferedReader().use { it.readText() }
        val packaged = Json.parseToJsonElement(text).jsonObject
        val effective = PhoneSettings.apply(packaged, PhoneSettings.current(packaged, PhoneSettings.load(context)))
        (effective["language"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "auto"
}
