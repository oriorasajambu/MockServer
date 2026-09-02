package com.rio.mockserver.feature.routes.editor

import androidx.compose.ui.text.input.TextFieldValue
import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.mvi.UiEffect
import com.rio.mockserver.core.common.mvi.UiIntent
import com.rio.mockserver.core.common.mvi.UiState
import com.rio.mockserver.core.domain.model.AlternateResponse
import com.rio.mockserver.core.domain.model.HttpMethodFilter
import com.rio.mockserver.core.domain.model.MockRoute
import com.rio.mockserver.core.domain.model.RequestMatcher
import com.rio.mockserver.core.domain.usecase.route.RouteValidation

/**
 * The route editor's contract (SPEC §10.2).
 *
 * Phone authoring lives or dies here, and the state reflects that. Three fields exist purely to
 * make the screen answer questions before the user has to go looking:
 * [specificityScore] says how this rule ranks, [resolvedUrlPreview] says what URL it actually
 * answers, and [validation] says what will break — all live, none behind a save.
 */
internal data class RouteEditorState(
    val route: MockRoute = MockRoute(id = "", collectionId = "", name = ""),

    /** The body, as a `TextFieldValue` so the symbol row can insert at the cursor. */
    val bodyText: TextFieldValue = TextFieldValue(""),

    /** Errors block Save; warnings do not. */
    val validation: RouteValidation = RouteValidation(),

    /** The live "Specificity: 34" badge. */
    val specificityScore: Int = 0,

    /** `http://192.168.1.45:8080/api/v1/users/{id}` — the whole URL this rule answers. */
    val resolvedUrlPreview: String = "",

    /** True when another route always beats this one, whatever the request (SPEC §4.3). */
    val isShadowed: Boolean = false,

    /** Drives the "discard changes?" dialog on back. */
    val hasUnsavedChanges: Boolean = false,

    /** Clipboard contents on entry, previewed on the Paste button when it looks like JSON. */
    val clipboardPreview: String? = null,

    /** Other statuses an import found for the same operation, offered as one-tap swaps. */
    val alternateResponses: List<AlternateResponse> = emptyList(),

    /** Which collapsible section is expanded. All start open; a wizard would hide the whole rule. */
    val expandedSections: Set<Section> = Section.entries.toSet(),

    val isNew: Boolean = true,
    val isSaving: Boolean = false,
) : UiState {

    /** The editor's four sections, in one scroll so the whole rule stays reviewable. */
    enum class Section { REQUEST, MATCHERS, RESPONSE, CHAOS }

    val canSave: Boolean get() = validation.canSave && !isSaving
}

internal sealed interface RouteEditorIntent : UiIntent {
    data class UpdateName(val name: String) : RouteEditorIntent
    data class UpdateMethod(val method: HttpMethodFilter) : RouteEditorIntent
    data class UpdatePath(val path: String) : RouteEditorIntent
    data class UpdateStatus(val statusCode: Int) : RouteEditorIntent
    data class UpdateBody(val body: TextFieldValue) : RouteEditorIntent
    data class UpdateHeader(val name: String, val value: String) : RouteEditorIntent
    data class RemoveHeader(val name: String) : RouteEditorIntent
    data class UpdateDelay(val millis: Long?) : RouteEditorIntent
    data class AddMatcher(val matcher: RequestMatcher) : RouteEditorIntent
    data class RemoveMatcher(val index: Int) : RouteEditorIntent
    data class ApplyAlternate(val statusCode: Int) : RouteEditorIntent
    data class ToggleSection(val section: RouteEditorState.Section) : RouteEditorIntent
    data object PasteFromClipboard : RouteEditorIntent
    data object PrettifyBody : RouteEditorIntent
    data object TestRoute : RouteEditorIntent
    data object Save : RouteEditorIntent
    data object AttemptBack : RouteEditorIntent
}

internal sealed interface RouteEditorEffect : UiEffect {
    data object NavigateBack : RouteEditorEffect
    data object ShowUnsavedDialog : RouteEditorEffect

    /** "Test this route" fires a loopback request and drops the user at the resulting entry. */
    data class NavigateToTrafficEntry(val entryId: String) : RouteEditorEffect
    data object ReadClipboard : RouteEditorEffect
    data class ShowError(val error: DomainError) : RouteEditorEffect
}
