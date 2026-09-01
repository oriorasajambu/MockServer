package com.rio.mockserver.core.domain.usecase.request

import com.rio.mockserver.core.domain.model.MockRequest
import com.rio.mockserver.core.domain.model.MockResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Substitutes `{{...}}` placeholders in a response (SPEC §5.1).
 *
 * Three sources and no more: path captures, query parameters and request headers. No fakers, no
 * scripting, no randomness — a mock whose output you cannot predict is a mock you cannot write an
 * assertion against.
 *
 * | Placeholder | Resolves to |
 * |---|---|
 * | `{{path.id}}` | the named capture from the route's pattern |
 * | `{{query.page}}` | the first value of that query parameter |
 * | `{{header.Authorization}}` | the first value of that header, looked up case-insensitively |
 * | `{{path}}` / `{{method}}` | the whole request path / method, for the unmatched fallback |
 *
 * Substitution is string-level and happens *after* the body was authored, so a value containing a
 * quote can produce invalid JSON. That is a deliberate trade: the editor warns at authoring time
 * (SPEC §5.1) and the request still gets an answer. Escaping on the author's behalf would make
 * `{{path.id}}` unusable for numbers, which is the common case.
 */
@Singleton
class ResolvePlaceholdersUseCase @Inject constructor() {

    /** A rendered string, and the placeholders in it that resolved to nothing. */
    data class Resolved(
        val value: String,

        /** Placeholder keys with no source, e.g. `path.userId`. Flagged on the traffic entry. */
        val unresolved: List<String>,
    )

    /** A whole response with its body and header *values* rendered. */
    data class RenderedResponse(
        val statusCode: Int,
        val headers: Map<String, String>,
        val body: String,
        val unresolvedPlaceholders: List<String>,
    )

    /** Renders one template. */
    operator fun invoke(
        template: String,
        request: MockRequest,
        pathCaptures: Map<String, String> = emptyMap(),
    ): Resolved {
        if (OPEN !in template) return Resolved(template, emptyList())

        val out = StringBuilder(template.length)
        val unresolved = LinkedHashSet<String>()
        var index = 0

        while (index < template.length) {
            when {
                // `\{{` escapes a literal `{{`. The backslash is consumed; the braces are not.
                template[index] == ESCAPE && template.startsWith(OPEN, index + 1) -> {
                    out.append(OPEN)
                    index += 1 + OPEN.length
                }

                template.startsWith(OPEN, index) -> {
                    val close = template.indexOf(CLOSE, index + OPEN.length)
                    if (close < 0) {
                        // An unterminated `{{` is text, not a placeholder. Swallowing the rest of
                        // the body because someone forgot a brace would be far worse than
                        // rendering it verbatim.
                        out.append(template, index, template.length)
                        index = template.length
                    } else {
                        val key = template.substring(index + OPEN.length, close).trim()
                        val value = resolve(key, request, pathCaptures)
                        if (value == null) {
                            unresolved += key
                        } else {
                            out.append(value)
                        }
                        index = close + CLOSE.length
                    }
                }

                else -> {
                    out.append(template[index])
                    index++
                }
            }
        }

        return Resolved(out.toString(), unresolved.toList())
    }

    /**
     * Renders [response]'s body and header values.
     *
     * Header *names* and the status code are left alone: a placeholder in a header name would
     * produce a header nobody can look up, and one in a status code has no sensible failure mode.
     */
    fun render(
        response: MockResponse,
        request: MockRequest,
        pathCaptures: Map<String, String> = emptyMap(),
    ): RenderedResponse {
        val body = invoke(response.body, request, pathCaptures)
        val unresolved = LinkedHashSet(body.unresolved)

        val headers = if (response.headers.isEmpty()) {
            emptyMap()
        } else {
            response.headers.mapValues { (_, value) ->
                invoke(value, request, pathCaptures).also { unresolved += it.unresolved }.value
            }
        }

        return RenderedResponse(
            statusCode = response.statusCode,
            headers = headers,
            body = body.value,
            unresolvedPlaceholders = unresolved.toList(),
        )
    }

    /**
     * Resolves one key, or `null` when nothing supplies it.
     *
     * `null` renders as an empty string rather than failing the request. A mock server that 500s
     * because you typo'd a placeholder is hostile — but the miss is recorded on the traffic entry
     * and shown as a warning chip, so it is visible rather than merely silent.
     */
    private fun resolve(
        key: String,
        request: MockRequest,
        pathCaptures: Map<String, String>,
    ): String? = when {
        key == WHOLE_PATH -> request.path
        key == WHOLE_METHOD -> request.method
        key.startsWith(PATH_PREFIX) -> pathCaptures[key.removePrefix(PATH_PREFIX)]
        key.startsWith(QUERY_PREFIX) -> request.queryValue(key.removePrefix(QUERY_PREFIX))
        key.startsWith(HEADER_PREFIX) -> request.header(key.removePrefix(HEADER_PREFIX))
        else -> null
    }

    private companion object {
        const val OPEN = "{{"
        const val CLOSE = "}}"
        const val ESCAPE = '\\'
        const val WHOLE_PATH = "path"
        const val WHOLE_METHOD = "method"
        const val PATH_PREFIX = "path."
        const val QUERY_PREFIX = "query."
        const val HEADER_PREFIX = "header."
    }
}
