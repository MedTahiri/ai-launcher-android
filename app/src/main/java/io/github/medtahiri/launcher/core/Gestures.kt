package io.github.medtahiri.launcher.core

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Gestures on empty home screen space (launcher.json `gestures`). Most need nothing; pulling down notifications or
 * quick settings and locking the screen need Accessibility access, which only [GestureService] uses, for those three
 * buttons. The service is off in the manifest and only turned on when launcher.json uses one of those actions.
 */
object Gestures {
    private val SYSTEM = setOf("notifications", "quick_settings", "lock")

    fun needsService(config: GesturesConfig) = config.all.values.any { it in SYSTEM }

    fun setEnabled(context: Context, enabled: Boolean) {
        val component = ComponentName(context, GestureService::class.java)
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (context.packageManager.getComponentEnabledSetting(component) != state) {
            context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }

    /** Runs a system action, or explains how to allow it and opens the screen to do that. */
    fun runSystem(context: Context, action: String, appName: String) {
        val service = GestureService.instance
        if (service == null) {
            Toast.makeText(context, "To use this gesture, turn on $appName in Accessibility", Toast.LENGTH_LONG).show()
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val global = when (action) {
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else {
                Toast.makeText(context, "Locking by gesture needs Android 9 or newer", Toast.LENGTH_SHORT).show()
                return
            }
            else -> return
        }
        service.performGlobalAction(global)
    }

    fun isSystem(action: String) = action in SYSTEM
}

/** Only presses three system buttons for gestures. It reads nothing on the screen. */
class GestureService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile var instance: GestureService? = null
    }
}
