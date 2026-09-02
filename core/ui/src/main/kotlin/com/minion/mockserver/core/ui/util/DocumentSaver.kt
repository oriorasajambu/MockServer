package com.minion.mockserver.core.ui.util

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.minion.mockserver.core.common.error.DomainError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Writes an export to a file the user picks (SPEC §9.2, §7.4).
 *
 * The share sheet is the other half of exporting and is not a substitute for this one: a share
 * hands the text to another app, while this writes a real file the user can point Charles,
 * Postman or a devtools importer at. A 500-entry HAR is not something anyone wants to receive as
 * the body of a message.
 *
 * `CREATE_DOCUMENT` through the storage-access framework, so there is no storage permission
 * involved at all — the user chooses the destination (Downloads by default on most devices) and
 * the app receives a URI it may write to once.
 *
 * The content cannot travel through the launcher, whose input is the suggested file name, so it
 * is held here between `save` and the picker returning. Held in a plain field rather than in
 * state: nothing draws it, and putting a possibly-megabyte string into composition would make
 * every recomposition carry it.
 */
@Stable
class DocumentSaver internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val launch: (String) -> Unit,
    private val onSaved: (String) -> Unit,
    private val onFailed: (DomainError) -> Unit,
) {

    private var pendingContent: String? = null
    private var pendingName: String = ""

    /**
     * Opens the system's file creator with [fileName] suggested, then writes [content] to
     * whatever the user picks.
     *
     * Silent when the user backs out, which is the correct reading of a cancelled picker: they
     * decided not to save, and a toast saying so is noise.
     */
    fun save(fileName: String, content: String) {
        pendingContent = content
        pendingName = fileName
        launch(fileName)
    }

    /** Called by the launcher's callback. `null` means the user cancelled. */
    internal fun onDestinationPicked(uri: android.net.Uri?) {
        val content = pendingContent
        val name = pendingName
        pendingContent = null
        if (uri == null || content == null) return

        scope.launch {
            val error = withContext(Dispatchers.IO) { write(uri, content) }
            if (error == null) onSaved(name) else onFailed(error)
        }
    }

    /** @return the error to report, or `null` when the write succeeded. */
    private fun write(uri: android.net.Uri, content: String): DomainError? = try {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: DomainError.FileAccess
        null
    } catch (_: java.io.FileNotFoundException) {
        DomainError.FileAccess
    } catch (e: IOException) {
        // ENOSPC surfaces as a plain IOException, and "your device is out of storage" is the one
        // write failure the user can actually do something about.
        if (e.message?.contains(NO_SPACE_MARKER, ignoreCase = true) == true) {
            DomainError.DiskFull
        } else {
            DomainError.FileAccess
        }
    } catch (_: SecurityException) {
        DomainError.FileAccess
    }

    private companion object {
        const val NO_SPACE_MARKER = "ENOSPC"
    }
}

/**
 * A [DocumentSaver] bound to this composition.
 *
 * One launcher, one MIME type: every format this app writes — the native export, a Postman
 * collection, a HAR capture — is JSON, so a second contract would buy nothing.
 *
 * @param onSaved called with the file name the user chose
 * @param onFailed called when the write failed, never when the user simply cancelled
 */
@Composable
fun rememberDocumentSaver(
    onSaved: (String) -> Unit,
    onFailed: (DomainError) -> Unit,
): DocumentSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Read through `rememberUpdatedState`, not captured: the saver outlives a recomposition and
    // the picker returns long after it, so a directly-captured lambda would still be holding the
    // Resources and callbacks from whichever composition happened to create the saver.
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnFailed by rememberUpdatedState(onFailed)

    // Captured by the launcher's callback before the saver exists, so the two are tied together
    // after both are constructed rather than at construction.
    val holder = remember { arrayOfNulls<DocumentSaver>(1) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JSON_MIME_TYPE),
    ) { uri -> holder[0]?.onDestinationPicked(uri) }

    return remember(context, scope, launcher) {
        DocumentSaver(
            context = context,
            scope = scope,
            launch = launcher::launch,
            onSaved = { fileName -> currentOnSaved(fileName) },
            onFailed = { error -> currentOnFailed(error) },
        ).also { holder[0] = it }
    }
}

/** Everything this app exports is JSON — the native format, Postman v2.1 and HAR 1.2 alike. */
private const val JSON_MIME_TYPE = "application/json"
