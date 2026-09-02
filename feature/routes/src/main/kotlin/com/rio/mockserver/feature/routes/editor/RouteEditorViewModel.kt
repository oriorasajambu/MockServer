package com.rio.mockserver.feature.routes.editor

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rio.mockserver.core.common.config.AppConstants
import com.rio.mockserver.core.common.error.DomainError
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.MockCollection
import com.rio.mockserver.core.domain.model.MockRoute
import com.rio.mockserver.core.domain.model.PathNormaliser
import com.rio.mockserver.core.domain.model.RoutingSnapshot
import com.rio.mockserver.core.domain.model.ServerStatus
import com.rio.mockserver.core.domain.repository.CollectionRepository
import com.rio.mockserver.core.domain.repository.MockServerRepository
import com.rio.mockserver.core.domain.repository.RouteRepository
import com.rio.mockserver.core.domain.usecase.route.CreateRouteFromTrafficUseCase
import com.rio.mockserver.core.domain.usecase.route.DetectShadowedRoutesUseCase
import com.rio.mockserver.core.domain.usecase.route.SaveRouteUseCase
import com.rio.mockserver.core.domain.usecase.route.ValidateRouteUseCase
import com.rio.mockserver.core.domain.usecase.server.ObserveServerAddressesUseCase
import com.rio.mockserver.core.domain.usecase.settings.GetServerSettingsUseCase
import com.rio.mockserver.core.domain.usecase.traffic.ObserveTrafficUseCase
import com.rio.mockserver.core.data.util.LoopbackRequester
import com.rio.mockserver.core.domain.util.IdGenerator
import com.rio.mockserver.core.navigation.RouteEditorRoute
import com.rio.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The route editor.
 *
 * Everything the screen shows about *consequences* — the specificity score, whether the rule is
 * shadowed, what URL it answers — is recomputed on every edit rather than on save. A rule you
 * only discover is shadowed after saving is a rule you have already stopped thinking about.
 */
@HiltViewModel
internal class RouteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    private val collectionRepository: CollectionRepository,
    private val saveRoute: SaveRouteUseCase,
    private val validateRoute: ValidateRouteUseCase,
    private val detectShadowed: DetectShadowedRoutesUseCase,
    private val createFromTraffic: CreateRouteFromTrafficUseCase,
    private val observeTraffic: ObserveTrafficUseCase,
    private val observeAddresses: ObserveServerAddressesUseCase,
    private val getSettings: GetServerSettingsUseCase,
    private val serverRepository: MockServerRepository,
    private val idGenerator: IdGenerator,
    private val loopbackRequester: LoopbackRequester,
) : MviViewModel<RouteEditorState, RouteEditorIntent, RouteEditorEffect>(RouteEditorState()) {

    private val args = savedStateHandle.toRoute<RouteEditorRoute>()

    private var collection: MockCollection? = null
    private var siblingRoutes: List<MockRoute> = emptyList()

    /** `http://192.168.1.45:8080` — the address half of the live URL preview. */
    private var baseUrl: String = ""

    /** The port the loopback test request goes to. */
    private var boundPort: Int = AppConstants.Server.DEFAULT_PORT

    /**
     * Where this route will be saved.
     *
     * Usually the route argument, but a draft built from a traffic entry arrives without one —
     * see [prefillFromTraffic].
     */
    private var targetCollectionId: String = args.collectionId

    init {
        // observeContext is started by loadRoute, not alongside it: a draft built from a traffic
        // entry works out its own collection, and observing before that resolves would subscribe
        // to the empty id and never re-subscribe.
        loadRoute()
    }

    override fun onIntent(intent: RouteEditorIntent) {
        when (intent) {
            is RouteEditorIntent.UpdateName -> editRoute { copy(name = intent.name) }
            is RouteEditorIntent.UpdateMethod -> editRoute { copy(method = intent.method) }
            is RouteEditorIntent.UpdatePath -> editRoute { copy(pathPattern = intent.path) }
            is RouteEditorIntent.UpdateStatus ->
                editRoute { copy(response = response.copy(statusCode = intent.statusCode)) }

            is RouteEditorIntent.UpdateBody -> {
                reduce { copy(bodyText = intent.body, hasUnsavedChanges = true) }
                editRoute { copy(response = response.copy(body = intent.body.text)) }
            }

            is RouteEditorIntent.UpdateHeader -> editRoute {
                copy(response = response.copy(headers = response.headers + (intent.name to intent.value)))
            }

            is RouteEditorIntent.RemoveHeader -> editRoute {
                copy(response = response.copy(headers = response.headers - intent.name))
            }

            is RouteEditorIntent.UpdateDelay ->
                editRoute { copy(response = response.copy(delayMillisOverride = intent.millis)) }

            is RouteEditorIntent.AddMatcher ->
                editRoute { copy(matchers = matchers + intent.matcher) }

            is RouteEditorIntent.RemoveMatcher -> editRoute {
                copy(matchers = matchers.filterIndexed { index, _ -> index != intent.index })
            }

            is RouteEditorIntent.ApplyAlternate -> applyAlternate(intent.statusCode)

            is RouteEditorIntent.ToggleSection -> reduce {
                copy(
                    expandedSections = if (intent.section in expandedSections) {
                        expandedSections - intent.section
                    } else {
                        expandedSections + intent.section
                    },
                )
            }

            RouteEditorIntent.PasteFromClipboard -> emit(RouteEditorEffect.ReadClipboard)

            RouteEditorIntent.PrettifyBody -> {
                val prettified = com.rio.mockserver.core.domain.util.JsonTree
                    .prettify(currentState.bodyText.text)
                onIntent(RouteEditorIntent.UpdateBody(TextFieldValue(prettified)))
            }

            RouteEditorIntent.TestRoute -> testRoute()
            RouteEditorIntent.Save -> save()
            RouteEditorIntent.AttemptBack -> emit(
                if (currentState.hasUnsavedChanges) {
                    RouteEditorEffect.ShowUnsavedDialog
                } else {
                    RouteEditorEffect.NavigateBack
                },
            )
        }
    }

    /** Called by the screen once it has read the clipboard, which needs a `Context`. */
    fun onClipboardRead(text: String?) {
        if (text.isNullOrBlank()) return
        onIntent(RouteEditorIntent.UpdateBody(TextFieldValue(text)))
    }

    /** Called on entry, so the Paste button can preview what it would paste. */
    fun onClipboardPreview(text: String?) {
        reduce { copy(clipboardPreview = text?.take(PREVIEW_LENGTH)) }
    }

    private fun loadRoute() {
        viewModelScope.launch {
            val existing = args.routeId?.let { id ->
                (routeRepository.get(id) as? AppResult.Success)?.data
            }

            val draft = when {
                existing != null -> existing
                args.prefillFromEntryId != null -> prefillFromTraffic(args.prefillFromEntryId!!)
                else -> MockRoute(
                    id = idGenerator.newId(),
                    collectionId = args.collectionId,
                    name = "",
                )
            }

            reduce {
                copy(
                    route = draft,
                    bodyText = TextFieldValue(draft.response.body),
                    isNew = existing == null,
                )
            }
            observeContext()
            revalidate()
        }
    }

    /**
     * Builds a draft from an unmatched request (SPEC §4.5).
     *
     * The deliberate replacement for traffic recording: point a client at the phone, let it fail,
     * and build the mock from the failure.
     */
    private suspend fun prefillFromTraffic(entryId: String): MockRoute {
        val entry = observeTraffic().first().firstOrNull { it.id == entryId }
            ?: return MockRoute(id = idGenerator.newId(), collectionId = args.collectionId, name = "")

        val collections = collectionRepository.observeCollections().first()
        val draft = createFromTraffic(entry, collections)

        // The route arrives from Traffic with no collection id — Traffic has no idea which
        // collection a failed request belongs in. The draft's own answer (the collection whose
        // base path is the longest prefix of the path) is the better one, and falls back to the
        // first collection so the editor always has somewhere to save to.
        targetCollectionId = args.collectionId.ifBlank {
            draft.collection?.id ?: collections.firstOrNull()?.id.orEmpty()
        }
        return draft.route.copy(collectionId = targetCollectionId)
    }

    private fun observeContext() {
        viewModelScope.launch {
            combine(
                collectionRepository.observeCollection(targetCollectionId),
                routeRepository.observeRoutes(targetCollectionId),
                observeAddresses(),
                getSettings(),
                serverRepository.status,
            ) { collection, routes, addresses, settings, status ->
                this@RouteEditorViewModel.collection = collection
                this@RouteEditorViewModel.siblingRoutes = routes
                boundPort = (status as? ServerStatus.Running)?.address?.port ?: settings.port
                val host = addresses.firstOrNull()?.hostAddress ?: AppConstants.Server.LOOPBACK_HOST
                "http://$host:$boundPort"
            }.collect { resolved ->
                baseUrl = resolved
                reduce { copy(resolvedUrlPreview = previewUrl(route)) }
                revalidate()
            }
        }
    }

    private fun editRoute(transform: MockRoute.() -> MockRoute) {
        reduce { copy(route = route.transform(), hasUnsavedChanges = true) }
        revalidate()
    }

    /**
     * Recomputes validation, score and shadowing.
     *
     * Cheap enough to run on every keystroke: compiling one pattern and scoring a handful of
     * matchers is microseconds, and the alternative — a debounce — makes the score badge lag
     * behind the field it describes.
     */
    private fun revalidate() {
        val route = currentState.route
        val currentCollection = collection ?: return

        val validation = validateRoute(route)
        val snapshot = RoutingSnapshot.build(
            collections = listOf(currentCollection),
            routes = siblingRoutes.filter { it.id != route.id } + route,
        )
        val compiled = snapshot.routes.firstOrNull { it.route.id == route.id }

        reduce {
            copy(
                validation = validation,
                specificityScore = compiled?.staticScore ?: 0,
                isShadowed = detectShadowed(snapshot).containsKey(route.id),
                resolvedUrlPreview = previewUrl(route),
            )
        }
    }

    /** The full URL a client would call, base path included. */
    private fun previewUrl(route: MockRoute): String {
        val basePath = collection?.basePath ?: PathNormaliser.ROOT
        return baseUrl.trimEnd('/') + PathNormaliser.join(basePath, route.pathPattern)
    }

    private fun applyAlternate(statusCode: Int) {
        val alternate = currentState.alternateResponses.firstOrNull { it.statusCode == statusCode }
            ?: return
        reduce {
            copy(
                route = route.copy(response = alternate.response),
                bodyText = TextFieldValue(alternate.response.body),
                hasUnsavedChanges = true,
            )
        }
        revalidate()
    }

    private fun save() {
        if (!currentState.canSave) return
        reduce { copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = saveRoute(currentState.route)) {
                is AppResult.Success -> {
                    reduce { copy(isSaving = false, hasUnsavedChanges = false) }
                    emitEffect(RouteEditorEffect.NavigateBack)
                }

                is AppResult.Failure -> {
                    reduce { copy(isSaving = false) }
                    emitEffect(RouteEditorEffect.ShowError(result.error))
                }
            }
        }
    }

    /**
     * Fires a loopback request through the running server and opens the resulting entry.
     *
     * Through the real socket rather than by calling the pipeline directly, because what this
     * button is for is proving the rule works *end to end* — including the parts a unit test
     * cannot see.
     */
    private fun testRoute() {
        viewModelScope.launch {
            if (!serverRepository.status.value.isRunning) {
                emitEffect(RouteEditorEffect.ShowError(DomainError.BindFailed))
                return@launch
            }

            // Saved first, because the engine serves the snapshot rather than the editor's draft —
            // testing an unsaved rule would test the previous one and report success.
            if (currentState.hasUnsavedChanges) {
                when (val result = saveRoute(currentState.route)) {
                    is AppResult.Failure -> {
                        emitEffect(RouteEditorEffect.ShowError(result.error))
                        return@launch
                    }

                    is AppResult.Success -> reduce { copy(hasUnsavedChanges = false) }
                }
            }

            val before = observeTraffic().first().firstOrNull()?.id
            val path = PathNormaliser.join(
                collection?.basePath ?: PathNormaliser.ROOT,
                // A pattern is not a URL: a `{id}` capture has to be given a value, and a `**`
                // tail needs a segment, or the request would not match the rule under test.
                currentState.route.pathPattern.toConcretePath(),
            )

            loopbackRequester.send(
                port = boundPort,
                path = path,
                method = currentState.route.method
                    .takeIf { it.isExplicit }?.name
                    ?: DEFAULT_TEST_METHOD,
            )

            // The entry the request produced. Read after the send, so it is the new one.
            val newest = observeTraffic().first().firstOrNull()
            if (newest != null && newest.id != before) {
                emitEffect(RouteEditorEffect.NavigateToTrafficEntry(newest.id))
            }
        }
    }

    /** Substitutes a sample value for every capture, so the test URL is addressable. */
    private fun String.toConcretePath(): String = PathNormaliser.segments(this)
        .map { segment ->
            when {
                segment == AppConstants.Matching.TAIL_WILDCARD -> SAMPLE_TAIL
                segment.startsWith('{') && segment.endsWith('}') -> SAMPLE_PARAM
                else -> segment
            }
        }
        .joinToString(separator = "/", prefix = "/")
        .ifBlank { PathNormaliser.ROOT }

    private fun emit(effect: RouteEditorEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }

    private companion object {
        const val PREVIEW_LENGTH = 40
        const val DEFAULT_TEST_METHOD = "GET"

        /** What a `{param}` becomes in the test URL. */
        const val SAMPLE_PARAM = "1"

        /** What a `**` tail becomes. One segment, since the tail requires at least one. */
        const val SAMPLE_TAIL = "sample"
    }
}
