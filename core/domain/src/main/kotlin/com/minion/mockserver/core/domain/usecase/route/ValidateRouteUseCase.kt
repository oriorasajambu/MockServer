package com.minion.mockserver.core.domain.usecase.route

import com.minion.mockserver.core.common.config.AppConstants
import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.domain.exception.MockServerException
import com.minion.mockserver.core.domain.model.MockResponse
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.PathPattern
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.model.StringOp
import com.minion.mockserver.core.domain.util.JsonTree
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything wrong with a route, as the editor should show it (SPEC §10.2).
 *
 * Two lists, not one, because they behave differently: an [error] blocks saving and a [warning]
 * does not. The distinction is whether the route would *work*. A body that is not valid JSON
 * under `Content-Type: application/json` will break the client, so it blocks; a placeholder that
 * might produce invalid JSON at runtime might also be exactly what the author meant, so it warns.
 */
data class RouteValidation(
    val errors: List<DomainError> = emptyList(),
    val warnings: List<Warning> = emptyList(),
) {

    /** Whether Save should be enabled. */
    val canSave: Boolean get() = errors.isEmpty()

    /** Something worth saying that is not worth blocking on. */
    sealed interface Warning {

        /** A placeholder sits inside a JSON string with no quotes around it (SPEC §5.1). */
        data class UnquotedPlaceholder(val placeholder: String) : Warning

        /** A header the writer or Ktor owns, which will be ignored (SPEC §5.2). */
        data class ReservedHeader(val name: String) : Warning

        /** A delay long enough to look like a hang (SPEC §6.2). */
        data class LongDelay(val millis: Long) : Warning

        /** The route references a path capture the pattern does not define. */
        data class UnknownPathPlaceholder(val name: String) : Warning
    }
}

/**
 * Validates a route as it is edited.
 *
 * Runs on every keystroke behind a debounce, so it allocates only when there is something to
 * report and never parses the body twice.
 */
@Singleton
class ValidateRouteUseCase @Inject constructor() {

    operator fun invoke(route: MockRoute): RouteValidation {
        val errors = mutableListOf<DomainError>()
        val warnings = mutableListOf<RouteValidation.Warning>()

        if (route.name.isBlank()) {
            errors += DomainError.Validation(DomainError.Validation.Field.ROUTE_NAME)
        }

        val pattern = try {
            PathPattern.parse(route.pathPattern)
        } catch (e: MockServerException.InvalidPathPattern) {
            errors += DomainError.InvalidPathPattern(e.pattern, e.problem)
            null
        }

        if (route.response.statusCode !in MIN_STATUS..MAX_STATUS) {
            errors += DomainError.Validation(DomainError.Validation.Field.STATUS_CODE)
        }

        // Invalid JSON blocks saving only when the response claims to be JSON. A route serving
        // XML, CSV or plain text is not wrong for failing a JSON parse (SPEC §10.2).
        if (route.response.declaresJson &&
            route.response.body.isNotBlank() &&
            !JsonTree.isValid(stripPlaceholders(route.response.body))
        ) {
            errors += DomainError.Validation(DomainError.Validation.Field.RESPONSE_BODY)
        }

        route.matchers.forEach { matcher -> validateMatcher(matcher)?.let { errors += it } }

        route.response.headers.keys
            .filter { it.lowercase() in RESERVED }
            .forEach { warnings += RouteValidation.Warning.ReservedHeader(it) }

        route.response.delayMillisOverride
            ?.takeIf { it > AppConstants.Chaos.DELAY_WARNING_MILLIS }
            ?.let { warnings += RouteValidation.Warning.LongDelay(it) }

        if (pattern != null) {
            warnings += unknownPathPlaceholders(route.response.body, pattern.paramNames)
        }
        warnings += unquotedPlaceholders(route.response)

        return RouteValidation(errors = errors, warnings = warnings)
    }

    private fun validateMatcher(matcher: RequestMatcher): DomainError? = when (matcher) {
        is RequestMatcher.Header ->
            invalidIf(matcher.name.isBlank() || (matcher.op.needsValue && matcher.value.isEmpty()))

        is RequestMatcher.Query ->
            invalidIf(matcher.name.isBlank() || (matcher.op.needsValue && matcher.value.isEmpty()))

        is RequestMatcher.BodySubset -> invalidIf(!JsonTree.isValid(matcher.json))
    }

    private fun invalidIf(condition: Boolean): DomainError? =
        if (condition) DomainError.Validation(DomainError.Validation.Field.MATCHER) else null

    /**
     * Placeholders naming a path capture the pattern does not have.
     *
     * A warning rather than an error: the author may be mid-edit, and an unresolvable placeholder
     * renders as an empty string rather than failing the request (SPEC §5.1).
     */
    private fun unknownPathPlaceholders(
        body: String,
        paramNames: List<String>,
    ): List<RouteValidation.Warning> = PLACEHOLDER.findAll(body)
        .map { it.groupValues[1].trim() }
        .filter { it.startsWith(PATH_PREFIX) }
        .map { it.removePrefix(PATH_PREFIX) }
        .filter { it !in paramNames }
        .distinct()
        .map { RouteValidation.Warning.UnknownPathPlaceholder(it) }
        .toList()

    /**
     * Placeholders that sit in a JSON value position without quotes.
     *
     * Substitution is string-level and post-serialisation, so `{"id": {{path.id}}}` produces
     * invalid JSON the moment the id is not numeric. Warned about, not blocked, because for a
     * numeric id it is exactly right — and blocking would make the common case unexpressible.
     */
    private fun unquotedPlaceholders(
        response: MockResponse,
    ): List<RouteValidation.Warning> {
        if (!response.declaresJson) return emptyList()
        return PLACEHOLDER.findAll(response.body)
            .filter { match ->
                val before = response.body.charBefore(match.range.first)
                val after = response.body.charAt(match.range.last + 1)
                before != '"' || after != '"'
            }
            .map { RouteValidation.Warning.UnquotedPlaceholder(it.groupValues[1].trim()) }
            .distinct()
            .toList()
    }

    /** Replaces every placeholder with a JSON string so the rest of the body can be parsed. */
    private fun stripPlaceholders(body: String): String =
        PLACEHOLDER.replace(body) { match ->
            val start = body.charBefore(match.range.first)
            // Already inside a string literal: substitute nothing, so the quotes still balance.
            if (start == '"') "" else "\"\""
        }

    private val StringOp.needsValue: Boolean
        get() = this == StringOp.EQUALS || this == StringOp.CONTAINS

    private fun String.charBefore(index: Int): Char? = getOrNull(index - 1)

    private fun String.charAt(index: Int): Char? = getOrNull(index)

    private companion object {
        const val MIN_STATUS = 100
        const val MAX_STATUS = 599
        const val PATH_PREFIX = "path."

        /**
         * Both braces escaped on both sides.
         *
         * A bare `}` is a literal on the JVM's regex engine and a syntax error on Android's ICU
         * one, so the unescaped form compiled in every unit test and threw
         * `PatternSyntaxException` on a device — in a `companion object` initialiser, which turns
         * it into a crash the moment the route editor is opened.
         */
        val PLACEHOLDER = Regex("""\{\{([^}]*)\}\}""")
        val RESERVED = AppConstants.Request.RESERVED_RESPONSE_HEADERS
    }
}
