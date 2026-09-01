package com.rio.mockserver.core.ui.error

import androidx.annotation.StringRes
import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.ui.R

/**
 * Turns a [DomainError] into the string resource that describes it to the user.
 *
 * This is the only place in the app where an error becomes text, and `:core:ui` is the only layer
 * allowed to touch `R.string` at all. The domain does not know what language the user reads, and
 * a ViewModel that builds a message has made itself untestable without a `Context`.
 *
 * The `when` has no `else` on purpose: [DomainError] is sealed, so adding a case here becomes a
 * compile error rather than a silently generic message.
 *
 * Resolve it in the composable — `stringResource(error.toMessageRes())` — not in the ViewModel.
 * Several of these strings take format arguments; [toMessageArgs] supplies them in order.
 */
@StringRes
fun DomainError.toMessageRes(): Int = when (this) {
    is DomainError.PortUnavailable -> R.string.error_port_unavailable
    DomainError.BindFailed -> R.string.error_bind_failed
    is DomainError.InvalidPathPattern -> when (problem) {
        DomainError.InvalidPathPattern.Problem.UNNAMED_PARAM -> R.string.error_pattern_unnamed_param
        DomainError.InvalidPathPattern.Problem.TAIL_NOT_LAST -> R.string.error_pattern_tail_not_last
        DomainError.InvalidPathPattern.Problem.UNBALANCED_BRACES -> R.string.error_pattern_unbalanced
        DomainError.InvalidPathPattern.Problem.DUPLICATE_PARAM -> R.string.error_pattern_duplicate_param
    }
    is DomainError.ImportParseError ->
        if (location == null) R.string.error_import_parse else R.string.error_import_parse_at
    DomainError.UnsupportedImportFormat -> R.string.error_import_unsupported
    DomainError.Persistence -> R.string.error_persistence
    DomainError.DiskFull -> R.string.error_disk_full
    DomainError.FileAccess -> R.string.error_file_access
    DomainError.NotFound -> R.string.error_not_found
    is DomainError.Validation -> when (field) {
        DomainError.Validation.Field.PORT -> R.string.error_invalid_port
        DomainError.Validation.Field.COLLECTION_NAME -> R.string.error_invalid_collection_name
        DomainError.Validation.Field.BASE_PATH -> R.string.error_invalid_base_path
        DomainError.Validation.Field.ROUTE_NAME -> R.string.error_invalid_route_name
        DomainError.Validation.Field.STATUS_CODE -> R.string.error_invalid_status_code
        DomainError.Validation.Field.RESPONSE_BODY -> R.string.error_invalid_response_body
        DomainError.Validation.Field.MATCHER -> R.string.error_invalid_matcher
        DomainError.Validation.Field.CHAOS_PROBABILITY -> R.string.error_invalid_probability
        DomainError.Validation.Field.CHAOS_DELAY -> R.string.error_invalid_delay
        DomainError.Validation.Field.CORS_CREDENTIALS_WILDCARD -> R.string.error_cors_credentials_wildcard
        DomainError.Validation.Field.LOG_CAPACITY -> R.string.error_invalid_log_capacity
        DomainError.Validation.Field.API_KEY_HEADER -> R.string.error_invalid_api_key_header
    }
    DomainError.Unknown -> R.string.error_unknown
}

/**
 * The format arguments [toMessageRes] expects, in order — empty for the messages that take none.
 *
 * Kept beside the mapping rather than at each call site so a string that gains a placeholder
 * cannot end up with the argument supplied in one screen and missing in another.
 */
fun DomainError.toMessageArgs(): Array<Any> = when (this) {
    is DomainError.PortUnavailable -> arrayOf(requestedPort, requestedPort + attempts)
    is DomainError.InvalidPathPattern -> arrayOf(pattern)
    is DomainError.ImportParseError -> location?.let { arrayOf<Any>(it) } ?: emptyArray()
    else -> emptyArray()
}
