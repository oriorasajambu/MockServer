package com.minion.mockserver.core.domain.usecase.settings

import com.minion.mockserver.core.common.config.AppConstants
import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.ServerSettings
import com.minion.mockserver.core.domain.repository.ServerSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/** The persisted settings, for the Settings tab and the snapshot builder. */
class GetServerSettingsUseCase @Inject constructor(
    private val repository: ServerSettingsRepository,
) {
    operator fun invoke(): Flow<ServerSettings> = repository.observeSettings()

    suspend fun once(): ServerSettings = repository.get()
}

/**
 * Validates and applies a settings change.
 *
 * Validation lives here rather than in the ViewModel so that the notification's Pause action and
 * the settings screen cannot disagree about what a legal value is.
 */
class UpdateServerSettingsUseCase @Inject constructor(
    private val repository: ServerSettingsRepository,
) {

    suspend operator fun invoke(transform: (ServerSettings) -> ServerSettings): AppResult<Unit> {
        val candidate = transform(repository.get())
        validate(candidate)?.let { return AppResult.Failure(it) }
        return repository.update(transform)
    }

    /** The first problem with [settings], or `null`. Also drives the editor's inline errors. */
    fun validate(settings: ServerSettings): DomainError? = when {
        settings.port !in AppConstants.Server.MIN_PORT..AppConstants.Server.MAX_PORT ->
            DomainError.Validation(DomainError.Validation.Field.PORT)

        settings.logCapacity !in
            AppConstants.Traffic.MIN_LOG_CAPACITY..AppConstants.Traffic.MAX_LOG_CAPACITY ->
            DomainError.Validation(DomainError.Validation.Field.LOG_CAPACITY)

        settings.apiKeyEnabled && !settings.apiKeyHeaderName.isHttpToken() ->
            DomainError.Validation(DomainError.Validation.Field.API_KEY_HEADER)

        else -> null
    }

    /**
     * Whether a string is a legal HTTP header name.
     *
     * A header name with a space or a colon in it is silently dropped by most clients, so the
     * guard would appear to be off rather than misconfigured — which is the worst of both.
     */
    private fun String.isHttpToken(): Boolean =
        isNotBlank() && all { it.isLetterOrDigit() || it in HEADER_NAME_SYMBOLS }

    private companion object {
        const val HEADER_NAME_SYMBOLS = "!#\$%&'*+-.^_`|~"
    }
}

/**
 * Generates a new API key (SPEC §8.6).
 *
 * `Random`, not `SecureRandom`: this is a guard against the other laptop on the coffee-shop
 * Wi-Fi, not a credential, and the server screen says as much rather than implying more. Using a
 * cryptographic generator here would suggest a security property the design does not have.
 */
class GenerateApiKeyUseCase @Inject constructor() {

    operator fun invoke(random: Random = Random.Default): String =
        (1..AppConstants.Server.API_KEY_LENGTH)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString(separator = "")

    private companion object {
        // No 0/O or 1/l: the key gets read off a phone screen and typed into a laptop.
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    }
}
