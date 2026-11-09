package io.github.medtahiri.launcher.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Notification dots: which apps have notifications, so their icons can show a dot.
 *
 * Android only tells a launcher about notifications if the person allows it in Settings > Notification access, which
 * also lets it read what they say. This keeps nothing but the package names, in memory. The service is off in the
 * manifest and only turned on when launcher.json asks for dots, so without them the launcher isn't even listed there.
 */
object NotificationDots {
    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    private val _connected = MutableStateFlow(false)

    /** Packages with at least one notification that may show a dot. */
    val packages: StateFlow<Set<String>> = _packages

    /** Whether Android is telling the launcher about notifications now, which means the person allowed it. */
    val connected: StateFlow<Boolean> = _connected

    internal fun update(packages: Set<String>) { _packages.value = packages }
    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) _packages.value = emptySet()
    }

    /** Turns the listening service on or off to match launcher.json. Turning it off also stops Android sending to it. */
    fun setEnabled(context: Context, enabled: Boolean) {
        val component = ComponentName(context, NotificationDotsService::class.java)
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val pm = context.packageManager
        if (pm.getComponentEnabledSetting(component) != state) {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
        if (!enabled) setConnected(false)
    }

    /** Whether the person has allowed notification access in Settings. */
    fun allowed(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
}

/** Which packages show a dot on this screen: empty unless launcher.json turns dots on. */
val LocalNotificationDots = staticCompositionLocalOf<Set<String>> { emptySet() }

/** The dot, at an icon's top corner. */
@Composable
fun NotificationDot(modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(
        modifier
            .size(size)
            .border(1.5.dp, Color.Black.copy(alpha = 0.45f), CircleShape) // stands out on any wallpaper
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

class NotificationDotsService : NotificationListenerService() {
    override fun onListenerConnected() {
        NotificationDots.setConnected(true)
        refresh()
    }

    override fun onListenerDisconnected() = NotificationDots.setConnected(false)

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = currentRanking
        val packages = active.filter { sbn ->
            val r = Ranking()
            // Apps and people can turn dots off per channel; ongoing ones, like music playing, aren't news.
            (ranking == null || !ranking.getRanking(sbn.key, r) || r.canShowBadge()) && !sbn.isOngoing
        }.map { it.packageName }.toSet()
        NotificationDots.update(packages)
    }
}
