package com.minion.mockserver.feature.settings.chaos

import androidx.lifecycle.viewModelScope
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.ChaosProfile
import com.minion.mockserver.core.domain.model.DelaySpec
import com.minion.mockserver.core.domain.usecase.chaos.DeleteChaosProfileUseCase
import com.minion.mockserver.core.domain.usecase.chaos.GetChaosProfilesUseCase
import com.minion.mockserver.core.domain.usecase.chaos.SaveChaosProfileUseCase
import com.minion.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The chaos profile editor. */
@HiltViewModel
internal class ChaosEditorViewModel @Inject constructor(
    private val getProfiles: GetChaosProfilesUseCase,
    private val saveProfile: SaveChaosProfileUseCase,
    private val deleteProfile: DeleteChaosProfileUseCase,
) : MviViewModel<ChaosEditorState, ChaosEditorIntent, ChaosEditorEffect>(ChaosEditorState()) {

    init {
        viewModelScope.launch {
            getProfiles().collect { profiles -> reduce { copy(profiles = profiles) } }
        }
    }

    override fun onIntent(intent: ChaosEditorIntent) {
        when (intent) {
            ChaosEditorIntent.NewProfile ->
                reduce { copy(editing = ChaosProfile(id = "", name = "")) }

            is ChaosEditorIntent.EditProfile -> reduce {
                copy(editing = profiles.firstOrNull { it.id == intent.id })
            }

            is ChaosEditorIntent.DeleteProfile -> viewModelScope.launch {
                deleteProfile(intent.id)
            }

            is ChaosEditorIntent.UpdateName -> edit { copy(name = intent.name) }

            is ChaosEditorIntent.UpdateFixedDelay -> edit {
                copy(delay = intent.millis?.let { DelaySpec.Fixed(it) } ?: DelaySpec.None)
            }

            is ChaosEditorIntent.UpdateErrorProbability ->
                edit { copy(errorInjection = errorInjection.copy(probability = intent.probability)) }

            is ChaosEditorIntent.UpdateResetProbability ->
                edit { copy(connectionReset = connectionReset.copy(probability = intent.probability)) }

            is ChaosEditorIntent.UpdateResetBytes ->
                edit { copy(connectionReset = connectionReset.copy(dropAfterBytes = intent.bytes)) }

            is ChaosEditorIntent.UpdateMalformedProbability ->
                edit { copy(malformedBody = malformedBody.copy(probability = intent.probability)) }

            is ChaosEditorIntent.UpdateMalformedPercent ->
                edit { copy(malformedBody = malformedBody.copy(truncateAtPercent = intent.percent)) }

            ChaosEditorIntent.Save -> save()
            ChaosEditorIntent.Cancel -> reduce { copy(editing = null, error = null) }
        }
    }

    private fun save() {
        val profile = currentState.editing ?: return
        viewModelScope.launch {
            when (val result = saveProfile(profile)) {
                is AppResult.Success -> reduce { copy(editing = null, error = null) }
                is AppResult.Failure -> reduce { copy(error = result.error) }
            }
        }
    }

    private fun edit(transform: ChaosProfile.() -> ChaosProfile) {
        reduce { copy(editing = editing?.transform(), error = null) }
    }
}
