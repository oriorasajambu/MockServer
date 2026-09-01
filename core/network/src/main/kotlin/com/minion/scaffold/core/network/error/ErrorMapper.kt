package com.minion.scaffold.core.network.error

import com.minion.scaffold.core.common.error.DomainError
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Translates the exceptions the transport layer throws into the app's [DomainError] vocabulary.
 *
 * This is the boundary. Exceptions exist below it and nowhere above it — a `Throwable` reaching a
 * ViewModel means something bypassed [safeCall].
 *
 * Mapping status codes to *meanings* is the point. 401 and 403 collapse into
 * [DomainError.Unauthorized] because the UI does the same thing for both; every 5xx keeps its
 * code because that is the one class of failure worth reporting verbatim.
 */
fun Throwable.toDomainError(): DomainError = when (this) {
    is UnknownHostException,
    is ConnectException,
    -> DomainError.NoInternet

    is SocketTimeoutException -> DomainError.Timeout

    is HttpException -> when (val code = code()) {
        401, 403 -> DomainError.Unauthorized
        404 -> DomainError.NotFound
        in 500..599 -> DomainError.Server(code)
        else -> DomainError.Unknown
    }

    // Must come after the specific subtypes above — SocketTimeoutException and
    // UnknownHostException are both IOExceptions, and this branch would swallow them.
    is IOException -> DomainError.NoInternet

    else -> DomainError.Unknown
}
