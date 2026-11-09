package io.github.medtahiri.launcher.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.medtahiri.launcher.core.WidgetConfig

private val PHONE = Regex("\\+?[0-9 ()-]{3,20}")
private val TILE_COLORS = listOf(0xFF5C6BC0, 0xFF26A69A, 0xFFEF6C00, 0xFF8E24AA, 0xFF2E7D32, 0xFFC2185B, 0xFF0277BD)

/** Opens the phone's dialer, WhatsApp or messages for [phone]. Dialing needs one more tap: no call permission. */
fun contact(context: Context, phone: String, action: String) {
    val digits = phone.filter { it.isDigit() || it == '+' }
    val intent = when (action) {
        "whatsapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${digits.removePrefix("+")}"))
        "message" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits"))
        else -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "No app on this phone can do that", Toast.LENGTH_SHORT).show() }
}

/**
 * A big tile for one person: tap to call, or to open their WhatsApp chat or messages. For people who find the phone app
 * hard to use. Options: `name`, `phone`, and `action`: call (default), whatsapp or message.
 */
object ContactWidget : LauncherWidget {
    override val type = "contact"
    private val OPTIONS = setOf("name", "phone", "action")

    override fun validate(config: WidgetConfig): List<String> = buildList {
        (config.options.keys - OPTIONS).forEach { add("unknown option \"$it\". Options: ${OPTIONS.sorted().joinToString()}") }
        if (config.string("name", "").isBlank()) add("name is needed, like \"Mom\"")
        if (!PHONE.matches(config.string("phone", ""))) add("phone must be a phone number, like +212 600 000000")
        if (config.string("action", "call") !in setOf("call", "whatsapp", "message")) add("action must be call, whatsapp or message")
    }

    override fun isWide(config: WidgetConfig) = false

    @Composable
    override fun Content(config: WidgetConfig) {
        val context = LocalContext.current
        val name = config.string("name", "")
        val action = config.string("action", "call")
        val verb = when (action) { "whatsapp" -> "WhatsApp"; "message" -> "Message"; else -> "Call" }
        val color = Color(TILE_COLORS[name.hashCode().mod(TILE_COLORS.size)])
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
                .clickable(onClickLabel = "$verb $name") { contact(context, config.string("phone", ""), action) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(name, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                Text(verb, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * A large red button that opens the dialer with a number to call for help, ready to press Call. Options: `phone`, and
 * `label` (default "Call for help"). It dials, not calls, so a stray tap never calls anyone.
 */
object HelpButtonWidget : LauncherWidget {
    override val type = "help_button"
    private val OPTIONS = setOf("phone", "label")

    override fun validate(config: WidgetConfig): List<String> = buildList {
        (config.options.keys - OPTIONS).forEach { add("unknown option \"$it\". Options: ${OPTIONS.sorted().joinToString()}") }
        if (!PHONE.matches(config.string("phone", ""))) add("phone must be a phone number, like +212 600 000000")
    }

    override fun isWide(config: WidgetConfig) = true

    @Composable
    override fun Content(config: WidgetConfig) {
        val context = LocalContext.current
        val label = config.string("label", "Call for help")
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFC62828))
                .clickable(onClickLabel = label) { contact(context, config.string("phone", ""), "call") }
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}
