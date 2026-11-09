package io.github.medtahiri.launcher.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import androidx.compose.runtime.staticCompositionLocalOf
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * An installed icon pack, like those made for Nova or other launchers: an app that maps other apps to icons of its own,
 * in an appfilter.xml. Apps it has no icon for keep their own.
 */
class IconPack private constructor(val packageName: String, private val res: Resources, private val icons: Map<String, String>) {

    /** The pack's icon for this app, or null if it has none. Call off the main thread. */
    fun iconFor(app: AppEntry): Drawable? {
        val name = icons[app.info.componentName.flattenToString()] ?: return null
        @SuppressLint("DiscouragedApi") // packs are only reachable by name
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    companion object {
        /** The intents icon packs announce themselves with, from the launchers that made the format common. */
        val INTENTS = listOf("org.adw.launcher.THEMES", "com.gau.go.launcherex.theme", "com.novalauncher.THEME")

        private var loaded: IconPack? = null

        /** The pack, loaded once and kept. Null if it isn't installed or can't be read. Call off the main thread. */
        @Synchronized
        fun load(context: Context, packageName: String): IconPack? {
            if (packageName.isEmpty()) return null
            loaded?.takeIf { it.packageName == packageName }?.let { return it }
            return try {
                val res = context.packageManager.getResourcesForApplication(packageName)
                IconPack(packageName, res, readAppFilter(res, packageName)).also { loaded = it }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            } catch (e: Exception) { // a broken pack mustn't take the launcher down
                Log.w("IconPack", "Can't read the icon pack $packageName", e)
                null
            }
        }

        /** Forgets the loaded pack, so an updated one is read again. */
        @Synchronized
        fun forget() { loaded = null }

        @SuppressLint("DiscouragedApi")
        private fun readAppFilter(res: Resources, packageName: String): Map<String, String> {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            val parser: XmlPullParser = if (id != 0) {
                res.getXml(id)
            } else {
                Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), null) }
            }
            val icons = mutableMapOf<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val component = componentKey(parser.getAttributeValue(null, "component") ?: continue) ?: continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                icons.putIfAbsent(component, drawable) // the first entry wins, as in other launchers
            }
            return icons
        }

        /** `ComponentInfo{com.app/com.app.Main}` as `com.app/com.app.Main`, with a short `.Main` class made full. */
        fun componentKey(value: String): String? {
            val inner = value.trim().removePrefix("ComponentInfo{").removeSuffix("}")
            val pkg = inner.substringBefore('/', "")
            val cls = inner.substringAfter('/', "")
            if (pkg.isEmpty() || cls.isEmpty()) return null
            return "$pkg/" + if (cls.startsWith('.')) pkg + cls else cls
        }

        /** The icon packs installed, as label and package name. */
        fun installed(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            return INTENTS.flatMap { pm.queryIntentActivities(Intent(it), 0) }
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .map { pm.getApplicationLabel(it).toString() to it.packageName }
                .sortedBy { it.first.lowercase() }
        }

        /** Debug builds only: the icon packs on the phone, for tools/apps.sh --icon-packs. */
        fun writeListForTools(context: Context) {
            File(context.filesDir, "icon-packs.tsv").writeText(installed(context).joinToString("") { "${it.first}\t${it.second}\n" })
        }
    }
}

/** The package of the icon pack the home screen uses, or "" for the apps' own icons. */
val LocalIconPack = staticCompositionLocalOf { "" }
