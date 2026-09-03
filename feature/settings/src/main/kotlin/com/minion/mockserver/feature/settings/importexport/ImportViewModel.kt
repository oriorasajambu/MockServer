package com.minion.mockserver.feature.settings.importexport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.minion.mockserver.core.common.result.AppResult
import com.minion.mockserver.core.domain.model.MockCollection
import com.minion.mockserver.core.domain.usecase.collection.GetCollectionsUseCase
import com.minion.mockserver.core.domain.usecase.importexport.CommitImportUseCase
import com.minion.mockserver.core.domain.usecase.importexport.DetectImportConflictsUseCase
import com.minion.mockserver.core.domain.usecase.importexport.ParseImportUseCase
import com.minion.mockserver.core.domain.usecase.route.GetRoutesUseCase
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.navigation.ImportRoute
import com.minion.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The import flow. */
@HiltViewModel
internal class ImportViewModel @Inject constructor(
    private val parseImport: ParseImportUseCase,
    private val detectConflicts: DetectImportConflictsUseCase,
    private val commitImport: CommitImportUseCase,
    private val getCollections: GetCollectionsUseCase,
    private val getRoutes: GetRoutesUseCase,
    private val idGenerator: IdGenerator,
    savedStateHandle: SavedStateHandle,
) : MviViewModel<ImportState, ImportIntent, ImportEffect>(ImportState()) {

    init {
        // Arriving from a collection's menu, the destination is already decided.
        savedStateHandle.toRoute<ImportRoute>().collectionId?.let { id ->
            reduce { copy(targetCollectionId = id) }
        }
        viewModelScope.launch {
            getCollections().collect { collections -> reduce { copy(collections = collections) } }
        }
    }

    override fun onIntent(intent: ImportIntent) {
        when (intent) {
            ImportIntent.PickFile -> emit(ImportEffect.OpenFilePicker)
            is ImportIntent.FileRead -> parse(intent.content, intent.fileName)

            is ImportIntent.ToggleRoute -> reduce {
                copy(
                    selectedRouteIds = if (intent.routeId in selectedRouteIds) {
                        selectedRouteIds - intent.routeId
                    } else {
                        selectedRouteIds + intent.routeId
                    },
                )
            }

            ImportIntent.SelectAll -> reduce {
                copy(selectedRouteIds = preview?.routes?.map { it.route.id }?.toSet().orEmpty())
            }

            ImportIntent.SelectNone -> reduce { copy(selectedRouteIds = emptySet()) }

            is ImportIntent.SelectTargetCollection -> {
                reduce { copy(targetCollectionId = intent.collectionId) }
                refreshConflicts()
            }

            is ImportIntent.UpdateNewCollectionName ->
                reduce { copy(newCollectionName = intent.name) }

            is ImportIntent.UpdateNewCollectionBasePath -> {
                reduce { copy(newCollectionBasePath = intent.basePath) }
                refreshConflicts()
            }

            is ImportIntent.ResolveConflict -> reduce {
                copy(
                    conflicts = conflicts.map { conflict ->
                        if (conflict.incoming.route.id == intent.routeId) {
                            conflict.copy(resolution = intent.resolution)
                        } else {
                            conflict
                        }
                    },
                )
            }

            ImportIntent.Commit -> commit()
        }
    }

    private fun parse(content: String, fileName: String?) {
        reduce { copy(isParsing = true, error = null) }
        viewModelScope.launch {
            when (val result = parseImport(content, fileName)) {
                is AppResult.Success -> {
                    val preview = result.data
                    reduce {
                        copy(
                            isParsing = false,
                            preview = preview,
                            // Everything checked by default: unchecking the two you do not want
                            // is far less work than checking the forty you do.
                            selectedRouteIds = preview.routes.map { it.route.id }.toSet(),
                            newCollectionName = preview.suggestedCollectionName,
                            newCollectionBasePath = preview.suggestedBasePath,
                        )
                    }
                    refreshConflicts()
                }

                is AppResult.Failure -> {
                    reduce { copy(isParsing = false, error = result.error) }
                    emitEffect(ImportEffect.ShowError(result.error))
                }
            }
        }
    }

    private fun refreshConflicts() {
        val preview = currentState.preview ?: return
        val targetId = currentState.targetCollectionId
        viewModelScope.launch {
            // A new collection has no existing routes, so nothing can collide with the import.
            val target = targetId?.let { id -> currentState.collections.firstOrNull { it.id == id } }
            if (target == null) {
                reduce { copy(conflicts = emptyList()) }
                return@launch
            }

            val existing = getRoutes(target.id).first()
            reduce { copy(conflicts = detectConflicts(preview, target, existing)) }
        }
    }

    private fun commit() {
        val preview = currentState.preview ?: return
        reduce { copy(isCommitting = true) }

        viewModelScope.launch {
            val target = currentState.targetCollectionId
                ?.let { id -> currentState.collections.firstOrNull { it.id == id } }
                ?: MockCollection(
                    id = idGenerator.newId(),
                    name = currentState.newCollectionName,
                    basePath = currentState.newCollectionBasePath,
                )

            val plan = CommitImportUseCase.Plan(
                collection = target,
                routes = preview.routes.map { imported ->
                    imported.copy(isSelected = imported.route.id in currentState.selectedRouteIds)
                },
                conflicts = currentState.conflicts,
            )

            when (val result = commitImport(plan)) {
                is AppResult.Success -> {
                    reduce { copy(isCommitting = false) }
                    emitEffect(ImportEffect.ShowImported(result.data))
                    emitEffect(ImportEffect.NavigateToCollection(target.id))
                }

                is AppResult.Failure -> {
                    reduce { copy(isCommitting = false, error = result.error) }
                    emitEffect(ImportEffect.ShowError(result.error))
                }
            }
        }
    }

    private fun emit(effect: ImportEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }
}
