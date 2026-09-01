package com.minion.scaffold.core.common.error

/**
 * Every way an operation can fail, as a closed set.
 *
 * Deliberately not `Throwable`. A `Throwable` is not exhaustive, so `when (error)` always needs
 * an `else` and a new failure mode falls silently into the generic branch instead of producing a
 * compile error at every call site that must handle it. It also carries `message`, which is
 * developer text — frequently `null`, frequently `"Failed to connect to /10.0.2.2:8080"` — and
 * putting that in state means untranslated internals rendered in the UI.
 *
 * Keep this set small and *meaningful to the user*, not a mirror of HTTP status codes. The test
 * for whether a failure deserves its own type: would the UI show a different message, or offer a
 * different action? [NoInternet] gets a retry button, [Unauthorized] navigates to login,
 * [Validation] highlights a field. Everything that behaves identically collapses into [Unknown].
 *
 * Feature-specific errors extend the hierarchy locally rather than bloating this type:
 *
 * ```kotlin
 * internal sealed interface CheckoutError : DomainError {
 *     data object CartEmpty : CheckoutError
 *     data class PaymentDeclined(val reason: String) : CheckoutError
 * }
 * ```
 */
sealed interface DomainError {

    /** No route to the server: airplane mode, no DNS, connection refused. */
    data object NoInternet : DomainError

    /** The request was sent but the server did not answer in time. */
    data object Timeout : DomainError

    /** 401 or 403. The UI's cue to send the user back to authentication. */
    data object Unauthorized : DomainError

    /** 404. The resource is gone, not the connection. */
    data object NotFound : DomainError

    /**
     * 5xx. Carries [code] because "server error" and "service unavailable" sometimes warrant
     * different copy, and because it is the one class of failure worth logging verbatim.
     */
    data class Server(val code: Int) : DomainError

    /** A read found nothing cached and there was no network to fall back to. */
    data object EmptyCache : DomainError

    /** A write failed because the device is out of space. Retrying will not help. */
    data object DiskFull : DomainError

    /**
     * Input the domain rejected. Carries which [field] failed so the UI can highlight it rather
     * than showing a form-level banner.
     */
    data class Validation(val field: Field) : DomainError {

        /** The inputs this app validates. Extend as the domain grows. */
        enum class Field { EMAIL, PASSWORD, NAME }
    }

    /** Everything else. Reaching this branch is not a bug; adding cases to avoid it is the job. */
    data object Unknown : DomainError
}
