package com.minion.mockserver.feature.routes.editor

import androidx.compose.ui.text.input.TextFieldValue
import com.minion.mockserver.core.common.error.DomainError
import com.minion.mockserver.core.common.mvi.UiEffect
import com.minion.mockserver.core.common.mvi.UiIntent
import com.minion.mockserver.core.common.mvi.UiState
import com.minion.mockserver.core.domain.model.AlternateResponse
import com.minion.mockserver.core.domain.model.HttpMethodFilter
import com.minion.mockserver.core.domain.model.MockRoute
import com.minion.mockserver.core.domain.model.RequestMatcher
import com.minion.mockserver.core.domain.usecase.route.RouteValidation

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

    /**
     * Path segments that look like ids, offered as captures (SPEC §4.5).
     *
     * Offered, never applied: `/users/42` might be the user with id 42 or a list that happens to
     * live under a numeric segment, and only the person writing the rule knows which. Populated
     * when the draft came from a request that missed, and empty otherwise.
     */
    val pathParameterSuggestions: List<PathParameterSuggestion> = emptyList(),

    /** Which of the three sections is showing. */
    val activeTab: Tab = Tab.REQUEST,

    /**
     * Tabs holding an edit that has not been saved.
     *
     * Splitting the form across tabs hides changes — edit the delay, switch to Response, and
     * nothing says the Chaos tab is no longer what it was. These drive the dots that say so.
     */
    val dirtyTabs: Set<Tab> = emptySet(),

    /** The last test fire, or `null` before the route has been fired at itself. */
    val testResult: TestResult? = null,

    val isNew: Boolean = true,
    val isSaving: Boolean = false,
) : UiState {

    /**
     * The editor's three sections.
     *
     * Matchers live under Request rather than in a fourth tab: they are part of deciding *what
     * this rule matches*, and a tab of their own would imply they are a separate concern from the
     * path they narrow.
     */
    enum class Tab { REQUEST, RESPONSE, CHAOS }

    /**
     * What the route answered when it was last fired at itself.
     *
     * [entryId] is kept so the bar can lead to the full request in the traffic log — the bar says
     * whether it worked, the entry says why not.
     */
    data class TestResult(
        val statusCode: Int,
        val durationMillis: Long,
        val byteCount: Int,
        val entryId: String,
    )

    /**
     * One id-shaped segment and the capture name offered for it.
     *
     * [segment] is carried alongside [index] so the suggestion can be discarded if the user has
     * edited the path in the meantime — applying it by position alone would rewrite whatever
     * happens to sit at that index now.
     */
    data class PathParameterSuggestion(
        val index: Int,
        val segment: String,
        val name: String,
    )

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

    /** Rewrites the segment at this index as `{name}`, and exposes it as `{{path.name}}`. */
    data class ParameteriseSegment(val index: Int) : RouteEditorIntent
    data class SelectTab(val tab: RouteEditorState.Tab) : RouteEditorIntent
    data object PasteFromClipboard : RouteEditorIntent
    data object PrettifyBody : RouteEditorIntent
    data object TestRoute : RouteEditorIntent

    /** Opens the traffic entry the last test produced. */
    data object OpenTestEntry : RouteEditorIntent
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
