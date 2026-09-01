package com.rio.mockserver.core.domain.exception

import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.error.DomainErrorAware

/**
 * The app's own typed failures, as exceptions.
 *
 * Exceptions and [DomainError] are not competing ideas here: an exception is how a failure
 * *travels* out of a deep call — a bind attempt, a pattern parse, an import traversal — and
 * [DomainError] is how it is *reported*. Each case below answers for itself through
 * [DomainErrorAware], so `safeCall { }` converts it without a `when` that would have to know
 * every type in the app.
 *
 * Nothing above the data layer catches these. A `MockServerException` reaching a ViewModel means
 * something bypassed the boundary.
 */
sealed class MockServerException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause), DomainErrorAware {

    /**
     * The requested port and all [attempts] fallback candidates were refused (SPEC §8.4).
     *
     * Carries the *requested* port rather than the last one tried, because that is the number the
     * user typed and the one the error message has to name.
     */
    class PortUnavailable(
        val requestedPort: Int,
        val attempts: Int,
    ) : MockServerException("Ports $requestedPort..${requestedPort + attempts} are all in use") {
        override val domainError: DomainError = DomainError.PortUnavailable(requestedPort, attempts)
    }

    /** The socket could not be opened for a reason other than the port being taken. */
    class BindFailed(
        cause: Throwable? = null,
    ) : MockServerException("Could not bind the server socket", cause) {
        override val domainError: DomainError = DomainError.BindFailed
    }

    /** A path pattern the matcher cannot compile (SPEC §4.2). */
    class InvalidPathPattern(
        val pattern: String,
        val problem: DomainError.InvalidPathPattern.Problem,
    ) : MockServerException("Invalid path pattern '$pattern': $problem") {
        override val domainError: DomainError = DomainError.InvalidPathPattern(pattern, problem)
    }

    /**
     * An import file that could not be read.
     *
     * [location] is a JSON pointer (`/paths/~1users/get`) or `line:column`, never a stack trace:
     * SPEC §9.1 requires a parse failure to point at the offending place in the user's file.
     */
    class ImportParseError(
        val location: String?,
        detail: String,
        cause: Throwable? = null,
    ) : MockServerException("Import failed${location?.let { " at $it" }.orEmpty()}: $detail", cause) {
        override val domainError: DomainError = DomainError.ImportParseError(location)
    }

    /** The picked file is not an OpenAPI, Postman or HAR document (SPEC §9.1). */
    class UnsupportedImportFormat(
        detail: String,
    ) : MockServerException("Unsupported import format: $detail") {
        override val domainError: DomainError = DomainError.UnsupportedImportFormat
    }
}
