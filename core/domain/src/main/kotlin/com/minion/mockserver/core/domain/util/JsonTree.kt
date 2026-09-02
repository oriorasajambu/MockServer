package com.minion.mockserver.core.domain.util

import com.minion.mockserver.core.domain.util.JsonTree.Permissive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The one JSON configuration the app parses and prints with.
 *
 * Lenient on the way in and pretty on the way out, deliberately: input is whatever a developer
 * pasted off a terminal, and output is read by a human on a phone screen.
 */
object JsonTree {

    /**
     * Parser for user-supplied JSON — matcher fragments, response bodies, import files.
     *
     * `isLenient` accepts unquoted keys and single quotes, which is what pasting from a log
     * produces. `ignoreUnknownKeys` matters for the import DTOs, where a spec carries fifty
     * fields this app has no interest in and refusing to parse because of them would be absurd.
     */
    val Permissive: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowTrailingComma = true
        coerceInputValues = false
        explicitNulls = false
        encodeDefaults = true
    }

    /** [Permissive], plus indentation. Used for exports and the editor's Format button. */
    val Pretty: Json = Json(Permissive) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Parses [text], or `null` when it is not JSON at all. Never throws. */
    fun parseOrNull(text: String?): JsonElement? {
        if (text.isNullOrBlank()) return null
        return try {
            Permissive.parseToJsonElement(text)
        } catch (_: Exception) {
            null
        }
    }

    /** Whether [text] parses as JSON. The editor's validity check. */
    fun isValid(text: String?): Boolean = parseOrNull(text) != null

    /** Re-indents [text], or returns it unchanged when it does not parse. */
    fun prettify(text: String): String {
        val element = parseOrNull(text) ?: return text
        return Pretty.encodeToString(JsonElement.serializer(), element)
    }
}
