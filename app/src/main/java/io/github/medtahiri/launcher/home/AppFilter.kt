package io.github.medtahiri.launcher.home

import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.LauncherConfig
import java.text.Normalizer

/** Which apps the drawer shows. The place for rules such as hiding apps during certain hours. */
fun visibleInDrawer(apps: List<AppEntry>, config: LauncherConfig, query: String): List<AppEntry> =
    apps.filter { app ->
        app.packageName !in config.drawer.hidden &&
            app.packageName !in config.hiddenNow &&
            (config.drawer.only.isEmpty() || app.packageName in config.drawer.only) &&
            matchesSearch(app.label, query)
    }

/** Whether a label matches what was typed, ignoring case and accents: "telephone" finds "Téléphone". */
fun matchesSearch(label: String, query: String): Boolean =
    plainText(label).contains(plainText(query.trim()))

private fun plainText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** The home screen's favorites, in launcher.json's order. A package with several icons shows its first. */
fun favorites(apps: List<AppEntry>, config: LauncherConfig): List<AppEntry> =
    config.home.favorites.filter { it !in config.hiddenNow }.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }

/** A folder from launcher.json, with the apps of it that are installed. */
data class Folder(val name: String, val apps: List<AppEntry>)

/** The folders that have at least one installed app, in launcher.json's order. */
fun folders(apps: List<AppEntry>, config: LauncherConfig): List<Folder> =
    config.home.folders
        .map { folder -> Folder(folder.name.trim(), folder.apps.distinct().filter { it !in config.hiddenNow }.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }) }
        .filter { it.apps.isNotEmpty() }
