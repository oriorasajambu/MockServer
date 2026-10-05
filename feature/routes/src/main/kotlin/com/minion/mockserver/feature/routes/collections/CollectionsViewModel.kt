package com.minion.mockserver.feature.routes.collections

import androidx.lifecycle.viewModelScope
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.CorsConfig
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.usecase.collection.DeleteCollectionUseCase
import com.minion.mockserver.core.domain.usecase.collection.GetCollectionsUseCase
import com.minion.mockserver.core.domain.usecase.collection.SaveCollectionUseCase
import com.minion.mockserver.core.domain.usecase.collection.ToggleCollectionUseCase
import com.minion.mockserver.core.domain.usecase.route.GetRoutesUseCase
import com.minion.mockserver.core.domain.usecase.route.ToggleRouteUseCase
import com.minion.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The collection list. */
@HiltViewModel
internal class CollectionsViewModel @Inject constructor(
    private val getCollections: GetCollectionsUseCase,
    private val saveCollection: SaveCollectionUseCase,
    private val deleteCollection: DeleteCollectionUseCase,
    private val toggleCollection: ToggleCollectionUseCase,
    private val getRoutes: GetRoutesUseCase,
    private val toggleRoute: ToggleRouteUseCase,
) : MviViewModel<CollectionsState, CollectionsIntent, CollectionsEffect>(CollectionsState()) {

    init {
        viewModelScope.launch {
            combine(getCollections(), getRoutes.all(), ::Pair).collect { (collections, routes) ->
                val byCollection = routes.groupBy { it.collectionId }
                reduce {
                    copy(
                        collections = collections,
                        routeCounts = byCollection.mapValues { (_, its) -> its.size },
                        needsBodyCounts = routes.filter { it.needsBody }
                            .groupingBy { it.collectionId }
                            .eachCount(),
                        // Whatever order the repository returns, which is creation order. The
                        // preview is a sample rather than a ranking — there is no "most
                        // important route", and sorting by one would only make the card's
                        // contents shuffle as routes are edited.
                        previewRoutes = byCollection.mapValues { (_, its) ->
                            its.take(PREVIEW_ROUTE_COUNT)
                        },
                        enabledRouteCount = routes.count { it.isEnabled },
                        totalRouteCount = routes.size,
                        isLoading = false,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: CollectionsIntent) {
        when (intent) {
            is CollectionsIntent.ToggleRoute -> viewModelScope.launch {
                toggleRoute(intent.id, intent.isEnabled)
            }

            CollectionsIntent.NewCollection -> reduce { copy(editing = CollectionsState.Draft()) }

            is CollectionsIntent.EditCollection -> {
                val collection = currentState.collections.firstOrNull { it.id == intent.id } ?: return
                reduce { copy(editing = collection.toDraft()) }
            }

            is CollectionsIntent.OpenCollection -> viewModelScope.launch {
                emitEffect(CollectionsEffect.NavigateToCollection(intent.id))
            }

            is CollectionsIntent.ToggleCollection -> viewModelScope.launch {
                toggleCollection(intent.id, intent.isEnabled)
            }

            is CollectionsIntent.DeleteCollection -> viewModelScope.launch {
                deleteCollection(intent.id)
            }

            is CollectionsIntent.UpdateDraftName -> editDraft { copy(name = intent.name, error = null) }
            is CollectionsIntent.UpdateDraftBasePath ->
                editDraft { copy(basePath = intent.basePath, error = null) }

            is CollectionsIntent.UpdateDraftCors -> editDraft { copy(corsEnabled = intent.enabled) }
            is CollectionsIntent.UpdateDraftOrigins ->
                editDraft { copy(allowedOrigins = intent.origins, error = null) }

            is CollectionsIntent.UpdateDraftCredentials ->
                editDraft { copy(allowCredentials = intent.allow, error = null) }

            CollectionsIntent.SaveDraft -> save()
            CollectionsIntent.DismissDraft -> reduce { copy(editing = null) }
        }
    }

    private fun save() {
        val draft = currentState.editing ?: return
        viewModelScope.launch {
            when (val result = saveCollection(draft.toCollection())) {
                is AppResult.Success -> reduce { copy(editing = null) }
                // The error stays on the draft rather than becoming a toast: it names a field, and
                // the sheet is still open with that field in it.
                is AppResult.Failure -> editDraft { copy(error = result.error) }
            }
        }
    }

    private fun editDraft(transform: CollectionsState.Draft.() -> CollectionsState.Draft) {
        reduce { copy(editing = editing?.transform()) }
    }

    private fun MockCollection.toDraft() = CollectionsState.Draft(
        id = id,
        name = name,
        basePath = basePath,
        corsEnabled = cors.isEnabled,
        allowedOrigins = cors.allowedOrigins.joinToString(SEPARATOR),
        allowCredentials = cors.allowCredentials,
    )

    private fun CollectionsState.Draft.toCollection() = MockCollection(
        id = id,
        name = name,
        basePath = basePath,
        order = currentState.collections.size,
        cors = CorsConfig(
            isEnabled = corsEnabled,
            allowedOrigins = allowedOrigins.split(SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf(CorsConfig.WILDCARD) },
            allowCredentials = allowCredentials,
        ),
    )

    private companion object {
        const val SEPARATOR = ","

        /** How many route lines a card previews. Three fits without the card outgrowing a thumb. */
        const val PREVIEW_ROUTE_COUNT = 3
    }
}
