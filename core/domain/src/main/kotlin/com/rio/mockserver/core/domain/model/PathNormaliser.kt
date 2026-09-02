package com.rio.mockserver.core.domain.model

/**
 * The one place a path is turned into something comparable (SPEC §4.2).
 *
 * Applied to patterns at compile time and to request paths at the adapter, so both sides of a
 * comparison have been through identical rules. Skipping it on either side is how `/users/`
 * stops matching `/users` in production and nowhere else.
 *
 * Rules, in the order they apply:
 * 1. The query string and fragment are dropped — they are never part of path matching.
 * 2. The path is split on `/`; empty segments vanish, which collapses `//users` and strips a
 *    trailing slash in one step.
 * 3. Each segment is percent-decoded exactly once, *after* the split. Decoding first would let a
 *    literal `%2F` in a segment invent a path boundary that the client never sent.
 * 4. An empty result becomes `/`.
 */
object PathNormaliser {

    /** The normalised form of [raw]: always leading-slash, never trailing-slash. */
    fun normalise(raw: String): String {
        val parts = segments(raw)
        return if (parts.isEmpty()) ROOT else parts.joinToString(separator = "/", prefix = "/")
    }

    /** The decoded segments of [raw], with empties removed. `/` yields an empty list. */
    fun segments(raw: String): List<String> {
        val withoutQuery = raw.substringBefore('?').substringBefore('#')
        if (withoutQuery.isEmpty() || withoutQuery == ROOT) return emptyList()

        val result = ArrayList<String>(4)
        var start = 0
        while (start <= withoutQuery.length) {
            val end = withoutQuery.indexOf('/', start).let { if (it == -1) withoutQuery.length else it }
            if (end > start) result += percentDecode(withoutQuery.substring(start, end))
            if (end == withoutQuery.length) break
            start = end + 1
        }
        return result
    }

    /**
     * The path component of an absolute or relative URL, normalised.
     *
     * Import formats record what a client asked for, which is a full URL — the scheme and host
     * name a server this app is standing in for, and carrying them into a pattern produces a
     * route nothing can address. A URL with no scheme is treated as already being a path.
     */
    fun fromUrl(url: String): String {
        val afterScheme = url.substringAfter(SCHEME_SEPARATOR, missingDelimiterValue = "")
        val path = if (afterScheme.isEmpty()) {
            url
        } else {
            "/" + afterScheme.substringAfter('/', missingDelimiterValue = "")
        }
        return normalise(path)
    }

    /**
     * Joins a collection base path to a route pattern.
     *
     * Both sides are normalised, so `/api/v1/` + `users` and `api/v1` + `/users` produce the same
     * `/api/v1/users`. Callers are not required to get the slashes right, because on a phone
     * keyboard they will not.
     */
    fun join(basePath: String, routePattern: String): String {
        val base = segments(basePath)
        val route = segments(routePattern)
        val all = base + route
        return if (all.isEmpty()) ROOT else all.joinToString(separator = "/", prefix = "/")
    }

    /**
     * Decodes `%XX` escapes as UTF-8.
     *
     * Hand-rolled rather than `java.net.URLDecoder`, which decodes `+` as a space. That is correct
     * for a form body and wrong for a path: `/a+b` addresses a resource literally named `a+b`.
     *
     * A malformed escape (`%`, `%A`, `%ZZ`) is left as the literal text it is rather than
     * throwing. A client that sends one still deserves to see which route it missed.
     */
    fun percentDecode(segment: String): String {
        if ('%' !in segment) return segment

        val out = StringBuilder(segment.length)
        // At most one byte per three characters, so this is never reallocated.
        val decoded = ByteArray(segment.length / 3 + 1)
        var index = 0

        while (index < segment.length) {
            if (segment[index] == '%' && isEscapeAt(segment, index)) {
                // Consume the whole run of escapes at once. A multi-byte UTF-8 character arrives
                // as several escapes (`%E2%9C%93`), and decoding them one at a time would produce
                // three replacement characters instead of one tick.
                var length = 0
                while (index < segment.length && segment[index] == '%' && isEscapeAt(segment, index)) {
                    decoded[length++] = ((hexValue(segment[index + 1]) shl 4) or hexValue(segment[index + 2])).toByte()
                    index += 3
                }
                out.append(String(decoded, 0, length, Charsets.UTF_8))
            } else {
                // Appended as a character, not as bytes, so a surrogate pair survives intact.
                out.append(segment[index])
                index++
            }
        }
        return out.toString()
    }

    private fun isEscapeAt(segment: String, index: Int): Boolean =
        index + 2 <= segment.lastIndex &&
            hexValue(segment[index + 1]) >= 0 &&
            hexValue(segment[index + 2]) >= 0

    private fun hexValue(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    /** The path every empty or all-slashes input normalises to. */
    const val ROOT = "/"

    private const val SCHEME_SEPARATOR = "://"
}
