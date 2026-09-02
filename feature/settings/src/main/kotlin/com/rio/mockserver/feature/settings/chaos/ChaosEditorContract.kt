package com.rio.mockserver.feature.settings.chaos

import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.mvi.UiEffect
import com.rio.mockserver.core.common.mvi.UiIntent
import com.rio.mockserver.core.common.mvi.UiState
import com.rio.mockserver.core.domain.model.ChaosProfile

/** The chaos profile editor (SPEC §6). */
internal data class ChaosEditorState(
    val profiles: List<ChaosProfile> = emptyList(),

    /** The profile being edited, or `null` when the list is showing. */
    val editing: ChaosProfile? = null,

    val error: DomainError? = null,
) : UiState

internal sealed interface ChaosEditorIntent : UiIntent {
    data object NewProfile : ChaosEditorIntent
    data class EditProfile(val id: String) : ChaosEditorIntent
    data class DeleteProfile(val id: String) : ChaosEditorIntent
    data class UpdateName(val name: String) : ChaosEditorIntent
    data class UpdateFixedDelay(val millis: Long?) : ChaosEditorIntent
    data class UpdateErrorProbability(val probability: Double) : ChaosEditorIntent
    data class UpdateResetProbability(val probability: Double) : ChaosEditorIntent
    data class UpdateResetBytes(val bytes: Int) : ChaosEditorIntent
    data class UpdateMalformedProbability(val probability: Double) : ChaosEditorIntent
    data class UpdateMalformedPercent(val percent: Int) : ChaosEditorIntent
    data object Save : ChaosEditorIntent
    data object Cancel : ChaosEditorIntent
}

internal sealed interface ChaosEditorEffect : UiEffect {
    data class ShowError(val error: DomainError) : ChaosEditorEffect
}
