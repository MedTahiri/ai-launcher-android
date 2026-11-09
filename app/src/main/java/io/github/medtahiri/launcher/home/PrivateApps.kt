package io.github.medtahiri.launcher.home

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.LauncherConfig

/**
 * Asks for the phone's fingerprint, face or screen lock, then calls the returned function's [onUnlocked]. A phone with no
 * screen lock has nothing to ask for: it says so and shows the apps, since hiding them is all it can do.
 */
@Composable
fun rememberUnlock(title: String, onUnlocked: () -> Unit): () -> Unit {
    val activity = LocalActivity.current
    // Before Android 9, the screen lock's own screen, which reports back as an activity result.
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onUnlocked()
    }
    return remember(activity) {
        {
            val keyguard = activity?.getSystemService(KeyguardManager::class.java)
            when {
                activity == null -> Unit
                keyguard?.isDeviceSecure != true -> {
                    Toast.makeText(activity, "Set a screen lock to keep these apps private", Toast.LENGTH_LONG).show()
                    onUnlocked()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    val builder = BiometricPrompt.Builder(activity).setTitle(title)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        builder.setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        builder.setDeviceCredentialAllowed(true)
                    }
                    builder.build().authenticate(
                        CancellationSignal(),
                        activity.mainExecutor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onUnlocked()
                        },
                    )
                }
                else -> {
                    @Suppress("DEPRECATION")
                    keyguard.createConfirmDeviceCredentialIntent(title, null)?.let { confirm.launch(it) } ?: onUnlocked()
                }
            }
        }
    }
}

/** "Private apps": opens them after unlocking. Shows nothing when launcher.json has none. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrivateAppsButton(apps: List<AppEntry>, config: LauncherConfig, color: Color, modifier: Modifier = Modifier) {
    val private = remember(apps, config.drawer.privateApps) {
        config.drawer.privateApps.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    }
    if (private.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val unlock = rememberUnlock("Private apps") { open = true }
    TextButton(onClick = unlock, modifier = modifier.testTag("private-apps")) { Text("Private apps", color = color) }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("private-apps-open"),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Private apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                    private.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach {
                                AppIcon(it, 56, true, config.home.allowUninstall, Modifier.weight(1f), onOpened = { open = false })
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
