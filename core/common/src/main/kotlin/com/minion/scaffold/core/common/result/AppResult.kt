package com.minion.scaffold.core.common.result

import com.minion.scaffold.core.common.error.DomainError

/**
 * The carrier every repository and use case returns.
 *
 * `kotlin.Result` can only hold a `Throwable`, which is exactly what forces an `else` branch at
 * every call site. Parameterising failure over [DomainError] instead keeps `when` exhaustive, so
 * adding an error case is a compile error everywhere it must be handled.
 *
 * [Failure] is `AppResult<Nothing>`, so it satisfies any `AppResult<T>` without a cast — which is
 * why [map] can return `this` directly.
 */
sealed interface AppResult<out T> {

    /** The operation produced [data]. */
    data class Success<T>(val data: T) : AppResult<T>

    /** The operation failed with [error]. */
    data class Failure(val error: DomainError) : AppResult<Nothing>
}

/** Transforms a success value, leaving a failure untouched. */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

/** Chains another fallible operation onto a success. The first failure short-circuits. */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}

/** Runs [action] on success and returns the receiver, so calls chain. */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Success) action(data)
}

/** Runs [action] on failure and returns the receiver, so calls chain. */
inline fun <T> AppResult<T>.onFailure(action: (DomainError) -> Unit): AppResult<T> = apply {
    if (this is AppResult.Failure) action(error)
}

/** The success value, or `null`. For the cases where branching on failure adds nothing. */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

/** The success value, or [fallback]. */
fun <T> AppResult<T>.getOrDefault(fallback: T): T = getOrNull() ?: fallback
