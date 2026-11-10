package io.github.medtahiri.launcher.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log

/**
 * Says short things aloud with the phone's own voice, for `home.speakAppNames`. No permission needed. Some phones'
 * default voice refuses other apps (Samsung's does), so if it won't start, the next installed voice is tried.
 */
object Speech {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private val tried = mutableSetOf<String>()

    fun say(context: Context, text: String) {
        if (ready) {
            speak(text)
            return
        }
        pending = text
        if (tts == null && tried.isEmpty()) start(context.applicationContext, null)
    }

    private fun start(context: Context, engine: String?) {
        tried += engine ?: "default"
        var created: TextToSpeech? = null
        created = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS && created?.defaultEngine != null) {
                ready = true
                pending?.let { speak(it) }
                pending = null
            } else {
                val next = created?.engines?.map { it.name }?.firstOrNull { it !in tried }
                created?.shutdown()
                tts = null
                if (next != null) start(context, next) else Log.w("Speech", "No voice on this phone will speak for the launcher")
            }
        }, engine)
        tts = created
    }

    private fun speak(text: String) {
        tts?.language = java.util.Locale.getDefault() // the launcher's language, so "Opening" is said in it
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "launcher")
    }
}
