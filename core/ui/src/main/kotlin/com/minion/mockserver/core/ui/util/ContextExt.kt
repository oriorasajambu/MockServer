package com.minion.mockserver.core.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * The handful of platform calls a screen legitimately makes.
 *
 * Wrapped rather than called inline so a ViewModel never needs a `Context` to express "copy this"
 * — it emits a side effect, and the screen's collector calls one of these.
 */

/** Copies [text] to the clipboard under [label]. */
fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * The clipboard's current text, or `null`.
 *
 * Read on entering the route editor so the Paste button can show what it would paste (SPEC
 * §10.2). On Android 12+ this shows the system's clipboard-access toast, which is correct: the
 * app is reading the clipboard, and the user should see that it did.
 */
fun Context.clipboardText(): String? {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
}

/** Opens the share sheet with [text]. */
fun Context.shareText(text: String, subject: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = MIME_PLAIN_TEXT
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    startActivity(Intent.createChooser(intent, subject))
}

/** A short toast. For confirmations that do not deserve a snackbar's real estate. */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private const val MIME_PLAIN_TEXT = "text/plain"
