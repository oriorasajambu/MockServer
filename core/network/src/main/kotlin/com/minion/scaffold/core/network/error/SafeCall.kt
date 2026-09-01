package com.minion.scaffold.core.network.error

import com.minion.scaffold.core.common.result.AppResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and converts any failure into a typed [AppResult.Failure].
 *
 * Use this instead of `runCatching`. `runCatching` catches `Throwable`, which includes
 * [CancellationException] — so when a `viewModelScope` is cancelled the coroutine's cancellation
 * signal becomes a `Result.failure` and structured concurrency quietly breaks: the caller sees a
 * failure instead of a cancellation, and cleanup may not run. It is the most damaging of the
 * reasons not to use it because it is invisible until a screen is closed mid-request.
 *
 * The rethrow below is the line `runCatching` is missing, and its position matters — it must come
 * before the general `Throwable` branch.
 *
 * ```kotlin
 * internal class UserRepositoryImpl @Inject constructor(private val api: UserApi) : UserRepository {
 *     override suspend fun getUsers(): AppResult<List<User>> = safeCall {
 *         api.getUsers().map { it.toDomain() }
 *     }
 * }
 * ```
 */
suspend fun <T> safeCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppResult.Failure(e.toDomainError())
    }
