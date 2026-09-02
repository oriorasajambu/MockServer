package com.minion.mockserver.core.common.error

/**
 * Every way an operation in this app can fail, as a closed set.
 *
 * Deliberately not `Throwable`. A `Throwable` is not exhaustive, so `when (error)` always needs
 * an `else` and a new failure mode falls silently into the generic branch instead of producing a
 * compile error at every call site that must handle it. It also carries `message`, which is
 * developer text — frequently `null`, frequently `"bind failed: EADDRINUSE"` — and putting that
 * in state means untranslated internals rendered in the UI.
 *
 * The set is shaped by *what the user is told and offered*, not by what threw. [PortUnavailable]
 * gets a "pick another port" action, [InvalidPathPattern] highlights the pattern field,
 * [ImportParseError] points at a location in the file. Anything that would produce identical
 * copy and identical actions collapses into [Unknown].
 *
 * This app makes no outbound HTTP calls (SPEC §2.3), so there is nothing here about connectivity
 * or HTTP status codes. A 503 the mock server *returns* is a successful operation, not a failure.
 */
sealed interface DomainError {

    /** The requested port and every fallback candidate were already bound (SPEC §8.4). */
    data class PortUnavailable(val requestedPort: Int, val attempts: Int) : DomainError

    /** The socket could not be opened for a reason other than the port being taken. */
    data object BindFailed : DomainError

    /** A path pattern the matcher cannot compile (SPEC §4.2). */
    data class InvalidPathPattern(val pattern: String, val problem: Problem) : DomainError {

        /** Why the pattern is unusable. Each maps to a specific hint under the pattern field. */
        enum class Problem {
            /** `{}` or `{ }` — a capture with no name to expose as `{{path.x}}`. */
            UNNAMED_PARAM,

            /** `**` appeared before the end. A tail wildcard can only be last. */
            TAIL_NOT_LAST,

            /** A `{` with no `}`, or a `}` with no `{`. */
            UNBALANCED_BRACES,

            /** Two captures in one pattern share a name, so `{{path.x}}` would be ambiguous. */
            DUPLICATE_PARAM,
        }
    }

    /**
     * An import file the parser could not read.
     *
     * [location] is a JSON pointer or `line:column`, never a stack trace — SPEC §9.1 is explicit
     * that a parse failure points at the offending place in the user's file.
     */
    data class ImportParseError(val location: String?) : DomainError

    /** The picked file is not a format this app reads (SPEC §9.1). */
    data object UnsupportedImportFormat : DomainError

    /** A read or write against Room or DataStore failed. */
    data object Persistence : DomainError

    /** A write failed because the device is out of space. Retrying will not help. */
    data object DiskFull : DomainError

    /** A file could not be read or written through the storage-access framework. */
    data object FileAccess : DomainError

    /** The collection, route or chaos profile being operated on no longer exists. */
    data object NotFound : DomainError

    /**
     * Input the domain rejected. Carries which [field] failed so the editor can highlight it
     * rather than showing a form-level banner.
     */
    data class Validation(val field: Field) : DomainError {

        /** The inputs this app validates. */
        enum class Field {
            /** Below 1024, above 65535, or not a number (SPEC §8.4). */
            PORT,

            /** Blank collection name. */
            COLLECTION_NAME,

            /** Blank base path, or one that normalises to nothing useful. */
            BASE_PATH,

            /** Blank route name. */
            ROUTE_NAME,

            /** Outside 100..599. */
            STATUS_CODE,

            /** Body is not valid JSON while `Content-Type` says it is (SPEC §10.2). */
            RESPONSE_BODY,

            /** A matcher with a blank name, or a `BodySubset` whose JSON does not parse. */
            MATCHER,

            /** A probability outside 0.0..1.0. */
            CHAOS_PROBABILITY,

            /** A delay outside 0..60_000 ms (SPEC §6.2). */
            CHAOS_DELAY,

            /** `allowCredentials = true` together with origin `*` — browsers reject it (SPEC §8.7). */
            CORS_CREDENTIALS_WILDCARD,

            /** A traffic-log capacity outside the supported range. */
            LOG_CAPACITY,

            /** A blank or non-token API-key header name. */
            API_KEY_HEADER,
        }
    }

    /** Everything else. Reaching this branch is not a bug; adding cases to avoid it is the job. */
    data object Unknown : DomainError
}

/**
 * Implemented by an exception that already knows which [DomainError] it means.
 *
 * This is how a typed exception thrown deep in the data layer reaches the UI as the right error
 * without `toDomainError()` having to know every exception type in the app — and without
 * `:core:common` having to depend on the modules that declare them, which would invert the
 * dependency this module exists at the bottom of.
 */
interface DomainErrorAware {

    /** What this failure means to the user. */
    val domainError: DomainError
}
