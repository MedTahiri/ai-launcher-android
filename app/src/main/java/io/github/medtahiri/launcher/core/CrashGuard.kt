package io.github.medtahiri.launcher.core

import android.content.Context
import java.io.File

/**
 * The way out of a broken launcher. A launcher that crashes on start leaves a phone without a home screen, so each
 * crash is written down, and after [LIMIT] within [WINDOW_MS] the home screen opens safe mode instead.
 */
object CrashGuard {
    private const val LIMIT = 3
    private const val WINDOW_MS = 5 * 60_000L
    private var installed = false

    private fun file(context: Context) = File(context.filesDir, "crashes.txt")

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file(app).appendText("${System.currentTimeMillis()}\n") }
            previous?.uncaughtException(thread, error)
        }
    }

    fun crashedTooOften(context: Context): Boolean {
        val since = System.currentTimeMillis() - WINDOW_MS
        val recent = runCatching { file(context).readLines() }.getOrDefault(emptyList())
            .mapNotNull { it.toLongOrNull() }
            .count { it >= since }
        return recent >= LIMIT
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}
