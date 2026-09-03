package com.minion.mockserver.feature.routes.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.model.RoutingSnapshot
import com.minion.mockserver.core.domain.usecase.collection.DeleteCollectionUseCase
import com.minion.mockserver.core.domain.usecase.collection.GetCollectionUseCase
import com.minion.mockserver.core.domain.usecase.collection.SaveCollectionUseCase
import com.minion.mockserver.core.domain.usecase.importexport.ExportCollectionJsonUseCase
import com.minion.mockserver.core.domain.usecase.importexport.ExportPostmanCollectionUseCase
import com.minion.mockserver.core.domain.usecase.route.DeleteRouteUseCase
import com.minion.mockserver.core.domain.usecase.route.DetectShadowedRoutesUseCase
import com.minion.mockserver.core.domain.usecase.route.DuplicateRouteUseCase
import com.minion.mockserver.core.domain.usecase.route.GetRoutesUseCase
import com.minion.mockserver.core.domain.usecase.route.ToggleRouteUseCase
import com.minion.mockserver.core.navigation.CollectionDetailRoute
import com.minion.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The routes inside one collection. */
@HiltViewModel
internal class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRoutes: GetRoutesUseCase,
    private val toggleRoute: ToggleRouteUseCase,
    private val duplicateRoute: DuplicateRouteUseCase,
    private val deleteRoute: DeleteRouteUseCase,
    private val detectShadowed: DetectShadowedRoutesUseCase,
    private val exportJson: ExportCollectionJsonUseCase,
    private val exportPostman: ExportPostmanCollectionUseCase,
    private val saveCollection: SaveCollectionUseCase,
    private val deleteCollection: DeleteCollectionUseCase,
    getCollection: GetCollectionUseCase,
) : MviViewModel<CollectionDetailState, CollectionDetailIntent, CollectionDetailEffect>(
    CollectionDetailState(),
) {

    private val collectionId = savedStateHandle.toRoute<CollectionDetailRoute>().collectionId

    /**
     * The destination a Postman export was heading for when the loss warning interrupted it.
     *
     * Held rather than passed through the dialog, because the dialog's confirm button knows only
     * that the user said yes — not which of the two buttons opened it.
     */
    private var pendingPostmanDestination: ExportDestination? = null

    /**
     * The collection as last loaded.
     *
     * Rename writes the whole aggregate back, so it needs the CORS config and creation time it is
     * not editing. Rebuilding one from the two fields in state would silently reset both.
     */
    private var loaded: MockCollection? = null

    init {
        viewModelScope.launch {
            combine(
                getCollection(collectionId).filterNotNull(),
                getRoutes(collectionId),
            ) { collection, routes ->
                // Shadowing is decided against a snapshot built from *this* collection alone.
                // Cross-collection shadowing exists, but reporting it here would put a chip on a
                // route because of a rule in a collection the user is not looking at.
                val snapshot = RoutingSnapshot.build(listOf(collection), routes)
                Triple(collection, routes, detectShadowed(snapshot))
            }.collect { (collection, routes, shadowed) ->
                loaded = collection
                reduce {
                    copy(
                        collectionName = collection.name,
                        basePath = collection.basePath,
                        routes = routes,
                        shadowedBy = shadowed,
                        isLoading = false,
                    )
                }
            }
        }
    }

    override fun onIntent(intent: CollectionDetailIntent) {
        when (intent) {
            CollectionDetailIntent.NewRoute -> emit(CollectionDetailEffect.NavigateToEditor(null))
            is CollectionDetailIntent.OpenRoute ->
                emit(CollectionDetailEffect.NavigateToEditor(intent.id))

            is CollectionDetailIntent.ToggleRoute -> viewModelScope.launch {
                toggleRoute(intent.id, intent.isEnabled)
            }

            is CollectionDetailIntent.DuplicateRoute -> viewModelScope.launch {
                duplicateRoute(intent.id)
            }

            is CollectionDetailIntent.DeleteRoute -> viewModelScope.launch { deleteRoute(intent.id) }

            CollectionDetailIntent.OpenMenu -> reduce { copy(isMenuOpen = true) }
            CollectionDetailIntent.DismissMenu -> reduce { copy(isMenuOpen = false) }

            CollectionDetailIntent.StartRename -> reduce {
                copy(isMenuOpen = false, renameDraft = collectionName, renameError = null)
            }

            is CollectionDetailIntent.UpdateRenameDraft -> reduce {
                copy(renameDraft = intent.name, renameError = null)
            }

            CollectionDetailIntent.DismissRename -> reduce {
                copy(renameDraft = null, renameError = null)
            }

            CollectionDetailIntent.ConfirmRename -> viewModelScope.launch {
                val current = loaded ?: return@launch
                val name = currentState.renameDraft ?: return@launch
                when (val result = saveCollection(current.copy(name = name))) {
                    // The collection flow this screen already collects delivers the new name, so
                    // there is nothing to write into state here beyond closing the dialog.
                    is AppResult.Success -> reduce { copy(renameDraft = null, renameError = null) }
                    is AppResult.Failure -> reduce { copy(renameError = result.error) }
                }
            }

            is CollectionDetailIntent.SetAllRoutesEnabled -> viewModelScope.launch {
                reduce { copy(isMenuOpen = false) }
                // One at a time rather than a bulk save: ToggleRouteUseCase is the only path that
                // recompiles the routing snapshot, and writing the rows directly would leave the
                // running server serving the old set until something else touched it.
                currentState.routes.forEach { route ->
                    toggleRoute(route.id, intent.isEnabled)
                }
            }

            CollectionDetailIntent.ImportIntoCollection -> {
                reduce { copy(isMenuOpen = false) }
                emit(CollectionDetailEffect.NavigateToImport(collectionId))
            }

            CollectionDetailIntent.StartDelete -> reduce {
                copy(isMenuOpen = false, isConfirmingDelete = true)
            }

            CollectionDetailIntent.DismissDelete -> reduce { copy(isConfirmingDelete = false) }

            CollectionDetailIntent.ConfirmDelete -> viewModelScope.launch {
                reduce { copy(isConfirmingDelete = false) }
                when (val result = deleteCollection(collectionId)) {
                    is AppResult.Success -> emitEffect(CollectionDetailEffect.NavigateBack)
                    is AppResult.Failure ->
                        emitEffect(CollectionDetailEffect.ShowError(result.error))
                }
            }

            is CollectionDetailIntent.Export -> when (intent.format) {
                ExportFormat.NATIVE_JSON -> viewModelScope.launch {
                    deliver(exportJson(collectionId), NATIVE_SUFFIX, intent.destination)
                }

                ExportFormat.POSTMAN -> viewModelScope.launch {
                    // The warning names the matchers and chaos overrides this file will not
                    // carry. Shown before the write, because a Postman collection that quietly
                    // dropped half a rule set is discovered days later, in someone else's app.
                    val warnings = exportPostman.lossWarnings(collectionId)
                    if (warnings.isNotEmpty()) {
                        pendingPostmanDestination = intent.destination
                        emitEffect(CollectionDetailEffect.ConfirmLossyExport(warnings))
                        return@launch
                    }
                    writePostman(intent.destination)
                }
            }
        }
    }

    /** Called after the user has seen what a Postman export will drop and chosen to continue. */
    fun confirmPostmanExport() {
        val destination = pendingPostmanDestination ?: return
        pendingPostmanDestination = null
        viewModelScope.launch { writePostman(destination) }
    }

    private suspend fun writePostman(destination: ExportDestination) {
        deliver(exportPostman(collectionId), POSTMAN_SUFFIX, destination)
    }

    /**
     * Sends one serialised export to [destination].
     *
     * The two destinations differ only in the last step, so the read and the failure branch stay
     * in one place rather than being written twice and drifting apart.
     */
    private suspend fun deliver(
        result: AppResult<String>,
        fileSuffix: String,
        destination: ExportDestination,
    ) {
        when (result) {
            is AppResult.Success -> {
                val fileName = currentState.collectionName + fileSuffix
                emitEffect(
                    when (destination) {
                        ExportDestination.SHARE ->
                            CollectionDetailEffect.ShareText(result.data, fileName)

                        ExportDestination.FILE ->
                            CollectionDetailEffect.SaveToFile(fileName, result.data)
                    },
                )
            }

            is AppResult.Failure -> emitEffect(CollectionDetailEffect.ShowError(result.error))
        }
    }

    private fun emit(effect: CollectionDetailEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }

    private companion object {
        const val NATIVE_SUFFIX = ".json"
        const val POSTMAN_SUFFIX = ".postman_collection.json"
    }
}
