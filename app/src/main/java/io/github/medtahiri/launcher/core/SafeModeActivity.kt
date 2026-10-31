package io.github.medtahiri.launcher.core

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * What the phone falls back to when the launcher keeps crashing. Plain Android views, with no Compose, config or
 * widgets, so that nothing a customization changes can break it. Keep it that way.
 */
class SafeModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        list.addView(TextView(this).apply {
            text = "Safe mode"
            textSize = 28f
            tag = "safe-mode"
        })
        list.addView(TextView(this).apply {
            text = "The home screen stopped working several times, so this simple one opened instead. You can still open your apps below, or choose another home screen in settings."
            setPadding(0, padding / 2, 0, padding)
        })
        list.addView(button("Try the launcher again") {
            CrashGuard.clear(this)
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        })
        list.addView(button("Open launcher settings") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) })

        val launcherApps = getSystemService(LauncherApps::class.java)
        launcherApps.profiles
            .flatMap { launcherApps.getActivityList(null, it) }
            .filter { it.applicationInfo.packageName != packageName }
            .sortedBy { it.label.toString().lowercase() }
            .forEach { app ->
                list.addView(button(app.label.toString()) {
                    launcherApps.startMainActivity(app.componentName, app.user, null, null)
                })
            }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true // here, not on the list: it would replace the list's padding
            addView(list)
        })
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        gravity = Gravity.CENTER
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    @Deprecated("The home screen stays open: Back has nowhere to go.")
    override fun onBackPressed() {}
}
