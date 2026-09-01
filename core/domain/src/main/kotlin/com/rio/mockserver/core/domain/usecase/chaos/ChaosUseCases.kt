package com.rio.mockserver.core.domain.usecase.chaos

import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.ChaosProfile
import com.rio.mockserver.core.domain.model.ConnectionResetSpec
import com.rio.mockserver.core.domain.model.DelaySpec
import com.rio.mockserver.core.domain.model.ErrorInjection
import com.rio.mockserver.core.domain.model.MalformedBodySpec
import com.rio.mockserver.core.domain.repository.ChaosRepository
import com.rio.mockserver.core.domain.repository.ServerSettingsRepository
import com.rio.mockserver.core.domain.util.IdGenerator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/** Every saved chaos profile, for the picker and the profile editor. */
class GetChaosProfilesUseCase @Inject constructor(
    private val repository: ChaosRepository,
) {
    operator fun invoke(): Flow<List<ChaosProfile>> = repository.observeProfiles()
}

/** Validates and saves a chaos profile. */
class SaveChaosProfileUseCase @Inject constructor(
    private val repository: ChaosRepository,
    private val idGenerator: IdGenerator,
) {

    suspend operator fun invoke(profile: ChaosProfile): AppResult<ChaosProfile> {
        validate(profile)?.let { return AppResult.Failure(it) }
        val prepared = profile.copy(id = profile.id.ifBlank { idGenerator.newId() })
        return when (val result = repository.save(prepared)) {
            is AppResult.Success -> AppResult.Success(prepared)
            is AppResult.Failure -> result
        }
    }

    /** The first problem with [profile], or `null`. Also drives the editor's inline errors. */
    fun validate(profile: ChaosProfile): DomainError? = when {
        !profile.errorInjection.probability.isProbability() ||
            !profile.connectionReset.probability.isProbability() ||
            !profile.malformedBody.probability.isProbability() ->
            DomainError.Validation(DomainError.Validation.Field.CHAOS_PROBABILITY)

        !profile.delay.isWithinCeiling() ->
            DomainError.Validation(DomainError.Validation.Field.CHAOS_DELAY)

        else -> null
    }

    private fun Double.isProbability(): Boolean = this in 0.0..1.0 && !isNaN()

    private fun DelaySpec.isWithinCeiling(): Boolean = when (this) {
        DelaySpec.None -> true
        is DelaySpec.Fixed -> millis in 0L..CEILING
        is DelaySpec.Random -> minMillis >= 0L && maxMillis in minMillis..CEILING
    }

    private companion object {
        val CEILING = com.rio.mockserver.core.common.config.AppConstants.Chaos.MAX_DELAY_MILLIS
    }
}

/** Deletes a chaos profile. The built-in "None" profile is not deletable. */
class DeleteChaosProfileUseCase @Inject constructor(
    private val repository: ChaosRepository,
    private val settingsRepository: ServerSettingsRepository,
) {

    suspend operator fun invoke(id: String): AppResult<Unit> {
        if (id == ChaosProfile.NONE_ID) return AppResult.Failure(DomainError.NotFound)
        // Clear the global selection first, so the snapshot never references a profile that has
        // just stopped existing.
        settingsRepository.update { settings ->
            if (settings.globalChaosProfileId == id) settings.copy(globalChaosProfileId = null) else settings
        }
        return repository.delete(id)
    }
}

/**
 * Selects the profile that applies to every route without an override (SPEC §6.1).
 *
 * A settings write, so it flows through the snapshot rebuild and takes effect on the next request
 * without touching the socket.
 */
class SetGlobalChaosProfileUseCase @Inject constructor(
    private val settingsRepository: ServerSettingsRepository,
) {
    suspend operator fun invoke(profileId: String?): AppResult<Unit> =
        settingsRepository.update { it.copy(globalChaosProfileId = profileId) }
}

/**
 * Regenerates the chaos seed (SPEC §6.3).
 *
 * Persisted *and* pushed into the live RNG, in that order. Persisting alone would leave the
 * running server on the old sequence until the next restart, which is exactly when someone is
 * trying to reproduce a failure and needs the two to agree.
 */
class ResetChaosSeedUseCase @Inject constructor(
    private val settingsRepository: ServerSettingsRepository,
    private val chaosRandom: ChaosRandom,
) {

    suspend operator fun invoke(seed: Long = Random.nextLong()): AppResult<Long> =
        when (val result = settingsRepository.update { it.copy(chaosSeed = seed) }) {
            is AppResult.Success -> {
                chaosRandom.reseed(seed)
                AppResult.Success(seed)
            }

            is AppResult.Failure -> result
        }
}
