package com.minion.scaffold.core.ui.error

import androidx.annotation.StringRes
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.ui.R

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
 */
@StringRes
fun DomainError.toMessageRes(): Int = when (this) {
    DomainError.NoInternet -> R.string.error_no_internet
    DomainError.Timeout -> R.string.error_timeout
    DomainError.Unauthorized -> R.string.error_unauthorized
    DomainError.NotFound -> R.string.error_not_found
    is DomainError.Server -> R.string.error_server
    DomainError.EmptyCache -> R.string.error_empty_cache
    DomainError.DiskFull -> R.string.error_disk_full
    is DomainError.Validation -> when (field) {
        DomainError.Validation.Field.EMAIL -> R.string.error_invalid_email
        DomainError.Validation.Field.PASSWORD -> R.string.error_invalid_password
        DomainError.Validation.Field.NAME -> R.string.error_invalid_name
    }
    DomainError.Unknown -> R.string.error_unknown
}
