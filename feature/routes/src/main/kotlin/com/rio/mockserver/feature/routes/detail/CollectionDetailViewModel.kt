package com.rio.mockserver.feature.routes.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.domain.model.RoutingSnapshot
import com.rio.mockserver.core.domain.repository.CollectionRepository
import com.rio.mockserver.core.domain.usecase.importexport.ExportCollectionJsonUseCase
import com.rio.mockserver.core.domain.usecase.importexport.ExportPostmanCollectionUseCase
import com.rio.mockserver.core.domain.usecase.route.DeleteRouteUseCase
import com.rio.mockserver.core.domain.usecase.route.DetectShadowedRoutesUseCase
import com.rio.mockserver.core.domain.usecase.route.DuplicateRouteUseCase
import com.rio.mockserver.core.domain.usecase.route.GetRoutesUseCase
import com.rio.mockserver.core.domain.usecase.route.ToggleRouteUseCase
import com.rio.mockserver.core.navigation.CollectionDetailRoute
import com.rio.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

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
    collectionRepository: CollectionRepository,
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

    init {
        viewModelScope.launch {
            combine(
                collectionRepository.observeCollection(collectionId).filterNotNull(),
                getRoutes(collectionId),
            ) { collection, routes ->
                // Shadowing is decided against a snapshot built from *this* collection alone.
                // Cross-collection shadowing exists, but reporting it here would put a chip on a
                // route because of a rule in a collection the user is not looking at.
                val snapshot = RoutingSnapshot.build(listOf(collection), routes)
                Triple(collection, routes, detectShadowed(snapshot))
            }.collect { (collection, routes, shadowed) ->
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
