package com.minion.mockserver.feature.settings.chaos

import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.mvi.UiEffect
import com.minion.mockserver.core.common.mvi.UiIntent
import com.minion.mockserver.core.common.mvi.UiState
import com.minion.mockserver.core.domain.model.ChaosProfile

/** The chaos profile editor (SPEC §6). */
internal data class ChaosEditorState(
    val profiles: List<ChaosProfile> = emptyList(),

    /** The profile being edited, or `null` when the list is showing. */
    val editing: ChaosProfile? = null,

    /**
     * Which profile is live. `null` is None — no chaos at all (SPEC §6).
     *
     * Read from settings rather than held on the profile, because "active" is a property of the
     * server, not of the profile: two profiles cannot both believe they are the live one.
     */
    val activeProfileId: String? = null,

    /** Replaying this seed reproduces the same sequence of failures (SPEC §6.3). */
    val chaosSeed: Long = 0L,

    /** The help sheet that explains what any of this does. */
    val isExplainerOpen: Boolean = false,

    val error: DomainError? = null,
) : UiState

internal sealed interface ChaosEditorIntent : UiIntent {
    data object NewProfile : ChaosEditorIntent

    /** Makes this profile the live one. `null` selects None. */
    data class SelectProfile(val id: String?) : ChaosEditorIntent
    data object ShowExplainer : ChaosEditorIntent
    data object DismissExplainer : ChaosEditorIntent
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
