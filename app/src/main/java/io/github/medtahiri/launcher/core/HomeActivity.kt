package io.github.medtahiri.launcher.core

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.graphics.Color
import io.github.medtahiri.launcher.home.HomeScreen
import io.github.medtahiri.launcher.theme.LauncherTheme
import io.github.medtahiri.launcher.theme.usesDarkText
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.medtahiri.launcher.home.PhoneControls
import io.github.medtahiri.launcher.theme.WallpaperAccent
import io.github.medtahiri.launcher.widgets.AppWidgets
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import io.github.medtahiri.launcher.widgets.WidgetRegistry

private val prettyJson = Json { prettyPrint = true }
private val fullJson = Json { encodeDefaults = true }

class HomeActivity : ComponentActivity() {
    private var drawerOpen by mutableStateOf(false)
    private var isDefaultLauncher by mutableStateOf(true)
    private var homePresses by mutableIntStateOf(0)
    private var resumes by mutableIntStateOf(0)
    /** "Show" on apps hidden for focus: lasts until the person leaves the launcher. */
    private var showHiddenApps by mutableStateOf(false)
    private var packagedText = ""
    private var wallpaperListener: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashGuard.install(this)
        // Always starts fresh: a home screen opens on its first page, and what must last is in preferences.
        super.onCreate(null)

        if (CrashGuard.crashedTooOften(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        // Debug builds only: how tools and agents test the path into safe mode.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        check(!(debuggable && intent.getBooleanExtra("crash_test", false))) { "crash_test" }

        packagedText = assets.open("launcher.json").bufferedReader().use { it.readText() }
        val (loaded, hasPhoneChanges) = loadConfig()
        val (parsedWithProfile, activeProfile) = withProfile(loaded)
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = when (parsedWithProfile.config.theme.mode) {
            "light" -> false
            "auto" -> systemDark
            else -> true
        }
        val accentSetting = parsedWithProfile.config.theme.accent
        val parsed = if (accentSetting == "wallpaper") {
            val background = parsedWithProfile.config.theme.background
            val darkBackground = if (background.startsWith("#")) WallpaperAccent.luminance(background.removePrefix("#").toInt(16)) < 0.5 else dark
            watchWallpaper()
            val accent = WallpaperAccent.resolve(this, darkBackground) ?: "#FFFFFF"
            parsedWithProfile.copy(config = parsedWithProfile.config.copy(theme = parsedWithProfile.config.theme.copy(accent = accent)))
        } else {
            parsedWithProfile
        }
        val barStyle = if (usesDarkText(parsed.config.theme, dark)) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        NotificationDots.setEnabled(this, parsed.config.home.notificationDots)
        Gestures.setEnabled(this, Gestures.needsService(parsed.config.gestures))
        AppWidgets.cleanUp(this, WidgetRegistry.androidSlots(parsed.config.home.widgets))
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        setContent {
            LauncherTheme(parsed.config.theme) {
                HomeScreen(
                    baseConfig = parsed.config,
                    configProblems = parsed.problems,
                    drawerOpen = drawerOpen,
                    onDrawerOpenChange = { drawerOpen = it },
                    isDefaultLauncher = isDefaultLauncher,
                    homePresses = homePresses,
                    resumes = resumes,
                    showHiddenApps = showHiddenApps,
                    onShowHiddenApps = { showHiddenApps = true },
                    controls = PhoneControls(
                        hasPhoneChanges = hasPhoneChanges,
                        onSaveSettings = ::saveSettings,
                        onResetSettings = {
                            PhoneSettings.reset(this)
                            recreate()
                        },
                        activeProfile = activeProfile,
                        pickedProfile = PickedProfile.get(this),
                        onProfileDue = ::recreate,
                        accentSetting = accentSetting,
                        onRestored = ::recreate,
                    ),
                )
            }
        }
    }

    /**
     * launcher.json with the changes made on this phone (see [PhoneSettings]), and whether there are any. A phone change
     * that no longer makes a valid config is ignored rather than breaking the launcher.
     */
    private fun loadConfig(): Pair<ParsedConfig, Boolean> {
        val packaged = runCatching { Json.parseToJsonElement(packagedText).jsonObject }.getOrNull()
        val saved = packaged?.let { PhoneSettings.current(it, PhoneSettings.load(this)) }
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (packaged != null && saved != null && saved.isNotEmpty()) {
            val effective = PhoneSettings.apply(packaged, saved)
            // Debug builds only: for tools/phone-settings.sh, so an agent can copy them into launcher.json.
            if (debuggable) File(filesDir, "launcher-effective.json").writeText(prettyJson.encodeToString(JsonObject.serializer(), effective))
            val withPhone = parseConfig(effective.toString(), WidgetRegistry.types, WidgetRegistry::validate)
            if (withPhone.problems.isEmpty()) return withPhone to true
        } else if (debuggable) {
            File(filesDir, "launcher-effective.json").delete()
        }
        return parseConfig(packagedText, WidgetRegistry.types, WidgetRegistry::validate) to false
    }

    /** The config with the profile in use now (picked on the phone, or by its hours), and that profile's name. */
    private fun withProfile(parsed: ParsedConfig): Pair<ParsedConfig, String?> {
        if (parsed.problems.isNotEmpty() || parsed.config.profiles.isEmpty()) return parsed to null
        val profile = Profiles.active(parsed.config.profiles, PickedProfile.get(this), LocalDateTime.now()) ?: return parsed to null
        val json = fullJson.encodeToJsonElement(LauncherConfig.serializer(), parsed.config).jsonObject
        val applied = parseConfig(Profiles.apply(json, profile).toString(), WidgetRegistry.types, WidgetRegistry::validate)
        return if (applied.problems.isEmpty()) applied to profile.name else parsed to null
    }

    /** With the accent taken from the wallpaper, a new wallpaper means a new accent. */
    private fun watchWallpaper() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 || wallpaperListener != null) return
        val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) recreate()
        }
        WallpaperManager.getInstance(this).addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        wallpaperListener = listener
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            (wallpaperListener as? WallpaperManager.OnColorsChangedListener)?.let {
                WallpaperManager.getInstance(this).removeOnColorsChangedListener(it)
            }
        }
        super.onDestroy()
    }

    /** From the Customize screen: keeps the changes and the profile picked, then starts again with them. */
    private fun saveSettings(changes: Map<String, JsonElement>, profile: String) {
        PickedProfile.set(this, profile)
        val packaged = runCatching { Json.parseToJsonElement(packagedText).jsonObject }.getOrNull() ?: return
        var saved = PhoneSettings.current(packaged, PhoneSettings.load(this))
        changes.forEach { (path, value) -> saved = PhoneSettings.record(packaged, saved, path, value) }
        PhoneSettings.save(this, saved)
        recreate()
    }

    override fun onResume() {
        super.onResume()
        resumes++
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        isDefaultLauncher = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    /** The Home button while the launcher is open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerOpen = false
        homePresses++
    }

    override fun onStart() {
        super.onStart()
        AppWidgets.startListening(this)
    }

    override fun onStop() {
        super.onStop()
        drawerOpen = false
        showHiddenApps = false
        OpenGate.waiting.value = null
        AppWidgets.stopListening(this)
    }

    /** An Android widget's own setup screen closed. Android only reports it here, not through the newer result API. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AppWidgets.onActivityResult(this, requestCode, resultCode)
    }
}
