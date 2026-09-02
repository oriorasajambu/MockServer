package com.minion.mockserver.core.common.error

import com.minion.mockserver.core.common.result.AppResult
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and converts any failure into a typed [AppResult.Failure].
 *
 * Use this instead of `runCatching`. `runCatching` catches `Throwable`, which includes
 * [CancellationException] — so when a `viewModelScope` is cancelled the coroutine's cancellation
 * signal becomes a failure and structured concurrency quietly breaks: the caller sees a failure
 * instead of a cancellation, and cleanup may not run. It is the most damaging of the reasons not
 * to use it because it is invisible until a screen is closed mid-request.
 *
 * The rethrow below is the line `runCatching` is missing, and its position matters — it must come
 * before the general `Throwable` branch.
 *
 * The request path does **not** use this. There, `respondCatching` in the server module is the
 * boundary: a throwing handler has to become a 500 on the wire plus a logged traffic entry, not
 * an `AppResult` nobody is holding (SPEC §2.3).
 */
suspend fun <T> safeCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppResult.Failure(e.toDomainError())
    }

/**
 * Translates an exception into the app's [DomainError] vocabulary.
 *
 * This is the boundary. Exceptions exist below it and nowhere above it — a `Throwable` reaching a
 * ViewModel means something bypassed [safeCall].
 *
 * A [DomainErrorAware] exception answers for itself, which is what keeps the app's own typed
 * failures (`MockServerException` in `:core:domain`) out of this `when` and this module free of
 * dependencies on the layers above it.
 */
fun Throwable.toDomainError(): DomainError = when (this) {
    is DomainErrorAware -> domainError

    // "No space left on device" is an IOException whose only signal is its message. Checking it
    // is unpleasant but the alternative — telling someone their export "failed" when the fix is
    // to free 40 MB — is worse.
    is IOException -> when {
        message?.contains(NO_SPACE_MARKER, ignoreCase = true) == true -> DomainError.DiskFull
        this is FileNotFoundException -> DomainError.FileAccess
        else -> DomainError.FileAccess
    }

    else -> DomainError.Unknown
}

private const val NO_SPACE_MARKER = "No space left on device"
