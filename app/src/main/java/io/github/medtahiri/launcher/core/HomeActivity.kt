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
import io.github.medtahiri.launcher.widgets.WidgetRegistry

class HomeActivity : ComponentActivity() {
    private var drawerOpen by mutableStateOf(false)
    private var isDefaultLauncher by mutableStateOf(true)
    private var homePresses by mutableIntStateOf(0)

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

        val parsed = parseConfig(assets.open("launcher.json").bufferedReader().use { it.readText() }, WidgetRegistry.types, WidgetRegistry::validate)
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val dark = when (parsed.config.theme.mode) {
            "light" -> false
            "auto" -> systemDark
            else -> true
        }
        val barStyle = if (usesDarkText(parsed.config.theme, dark)) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        setContent {
            LauncherTheme(parsed.config.theme) {
                HomeScreen(
                    config = parsed.config,
                    configProblems = parsed.problems,
                    drawerOpen = drawerOpen,
                    onDrawerOpenChange = { drawerOpen = it },
                    isDefaultLauncher = isDefaultLauncher,
                    homePresses = homePresses,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        isDefaultLauncher = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    /** The Home button while the launcher is open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerOpen = false
        homePresses++
    }

    override fun onStop() {
        super.onStop()
        drawerOpen = false
    }
}
