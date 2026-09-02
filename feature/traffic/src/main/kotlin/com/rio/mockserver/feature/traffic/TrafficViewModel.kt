package com.rio.mockserver.feature.traffic

import androidx.lifecycle.viewModelScope
import com.rio.mockserver.core.common.config.AppConstants
import com.rio.mockserver.core.domain.repository.MockServerRepository
import com.rio.mockserver.core.domain.usecase.server.ObserveServerAddressesUseCase
import com.rio.mockserver.core.domain.usecase.settings.GetServerSettingsUseCase
import com.rio.mockserver.core.domain.usecase.traffic.ClearTrafficUseCase
import com.rio.mockserver.core.domain.usecase.traffic.ExportTrafficAsHarUseCase
import com.rio.mockserver.core.domain.usecase.traffic.FormatEntryAsCurlUseCase
import com.rio.mockserver.core.domain.usecase.traffic.ObserveTrafficUseCase
import com.rio.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The Traffic tab. */
@OptIn(FlowPreview::class)
@HiltViewModel
internal class TrafficViewModel @Inject constructor(
    private val observeTraffic: ObserveTrafficUseCase,
    private val clearTraffic: ClearTrafficUseCase,
    private val exportHar: ExportTrafficAsHarUseCase,
    private val formatAsCurl: FormatEntryAsCurlUseCase,
    private val observeAddresses: ObserveServerAddressesUseCase,
    private val getSettings: GetServerSettingsUseCase,
    private val serverRepository: MockServerRepository,
) : MviViewModel<TrafficState, TrafficIntent, TrafficEffect>(TrafficState()) {

    /**
     * The filter the *use case* sees, separate from the one in state.
     *
     * State updates on every keystroke so the field stays responsive; this one is debounced, so
     * the list is refiltered at most once per 300 ms rather than per character.
     */
    private val activeFilter = MutableStateFlow(ObserveTrafficUseCase.Filter())

    init {
        observeEntries()
        observeBaseUrl()
    }

    override fun onIntent(intent: TrafficIntent) {
        when (intent) {
            is TrafficIntent.UpdateSearch -> {
                reduce { copy(searchQuery = intent.query) }
                activeFilter.value = activeFilter.value.copy(query = intent.query)
            }

            is TrafficIntent.ToggleMethodFilter -> updateFilter { current ->
                current.copy(methods = current.methods.toggle(intent.method))
            }

            is TrafficIntent.ToggleStatusClassFilter -> updateFilter { current ->
                current.copy(statusClasses = current.statusClasses.toggle(intent.statusClass))
            }

            is TrafficIntent.SetUnmatchedFilter -> updateFilter { current ->
                current.copy(unmatchedOnly = intent.unmatchedOnly)
            }

            TrafficIntent.ClearFilters -> {
                reduce { copy(searchQuery = "") }
                updateFilter { ObserveTrafficUseCase.Filter() }
            }

            TrafficIntent.ToggleFilterSheet ->
                reduce { copy(isFilterSheetOpen = !isFilterSheetOpen) }

            // Following is paused by the list itself, on the first scroll away from the top.
            TrafficIntent.PauseFollow -> reduce { copy(isFollowing = false) }
            TrafficIntent.ResumeFollow -> reduce { copy(isFollowing = true, newEntryCount = 0) }

            TrafficIntent.ClearLog -> viewModelScope.launch {
                clearTraffic()
                reduce { copy(newEntryCount = 0) }
            }

            TrafficIntent.ExportHar -> exportSession()
            is TrafficIntent.OpenEntry -> viewModelScope.launch {
                emitEffect(TrafficEffect.NavigateToDetail(intent.entryId))
            }

            is TrafficIntent.CreateRuleFrom -> viewModelScope.launch {
                emitEffect(TrafficEffect.NavigateToRouteEditor(intent.entryId))
            }

            is TrafficIntent.CopyAsCurl -> copyAsCurl(intent.entryId)
        }
    }

    private fun observeEntries() {
        viewModelScope.launch {
            observeTraffic.filtered(
                activeFilter.debounce(AppConstants.Traffic.SEARCH_DEBOUNCE_MILLIS)
                    .distinctUntilChanged(),
            ).collect { entries ->
                reduce {
                    copy(
                        entries = entries,
                        // While following is paused, the pill counts what arrived behind the
                        // user's back rather than the list moving under their finger.
                        newEntryCount = if (isFollowing) {
                            0
                        } else {
                            (entries.size - this.entries.size).coerceAtLeast(0) + newEntryCount
                        },
                    )
                }
            }
        }

        viewModelScope.launch {
            observeTraffic.droppedCount().collect { dropped ->
                reduce { copy(droppedCount = dropped) }
            }
        }
    }

    /** The address exports are written against — whichever one the server screen is showing. */
    private fun observeBaseUrl() {
        viewModelScope.launch {
            combine(
                observeAddresses(),
                getSettings(),
                serverRepository.status,
            ) { addresses, settings, status ->
                val port = (status as? com.rio.mockserver.core.domain.model.ServerStatus.Running)
                    ?.address?.port
                    ?: settings.port
                addresses.firstOrNull()?.toBaseUrl(port)
                    ?: "http://${AppConstants.Server.LOOPBACK_HOST}:$port"
            }.collect { baseUrl -> reduce { copy(baseUrl = baseUrl) } }
        }
    }

    private fun exportSession() {
        val entries = currentState.entries
        if (entries.isEmpty()) return
        viewModelScope.launch {
            // The *filtered* set: exporting a session usually means exporting the ten requests
            // you were looking at, not the five hundred you were not.
            val har = exportHar(entries, currentState.baseUrl)
            emitEffect(TrafficEffect.ShareText(har, HAR_SUBJECT))
        }
    }

    private fun copyAsCurl(entryId: String) {
        val entry = currentState.entries.firstOrNull { it.id == entryId } ?: return
        viewModelScope.launch {
            emitEffect(TrafficEffect.CopiedToClipboard(formatAsCurl(entry, currentState.baseUrl)))
        }
    }

    private fun updateFilter(transform: (ObserveTrafficUseCase.Filter) -> ObserveTrafficUseCase.Filter) {
        val updated = transform(activeFilter.value)
        activeFilter.value = updated
        reduce { copy(filter = updated) }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (contains(value)) this - value else this + value

    private companion object {
        const val HAR_SUBJECT = "Mock server session.har"
    }
}
