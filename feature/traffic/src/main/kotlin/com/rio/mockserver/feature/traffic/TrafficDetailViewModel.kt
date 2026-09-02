package com.rio.mockserver.feature.traffic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rio.mockserver.core.common.config.AppConstants
import com.rio.mockserver.core.domain.model.ServerStatus
import com.rio.mockserver.core.domain.usecase.server.ObserveServerAddressesUseCase
import com.rio.mockserver.core.domain.usecase.server.ObserveServerStatusUseCase
import com.rio.mockserver.core.domain.usecase.settings.GetServerSettingsUseCase
import com.rio.mockserver.core.domain.usecase.traffic.FormatEntryAsCurlUseCase
import com.rio.mockserver.core.domain.usecase.traffic.FormatEntryAsTextUseCase
import com.rio.mockserver.core.domain.usecase.traffic.ObserveTrafficUseCase
import com.rio.mockserver.core.navigation.TrafficDetailRoute
import com.rio.mockserver.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** One traffic entry. */
@HiltViewModel
internal class TrafficDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeTraffic: ObserveTrafficUseCase,
    private val formatAsCurl: FormatEntryAsCurlUseCase,
    private val formatAsText: FormatEntryAsTextUseCase,
    private val observeAddresses: ObserveServerAddressesUseCase,
    private val getSettings: GetServerSettingsUseCase,
    private val observeServerStatus: ObserveServerStatusUseCase,
) : MviViewModel<TrafficDetailState, TrafficDetailIntent, TrafficDetailEffect>(TrafficDetailState()) {

    private val entryId = savedStateHandle.toRoute<TrafficDetailRoute>().entryId

    init {
        viewModelScope.launch {
            // Observed rather than read once: the log is a ring buffer, so an entry can age out
            // while its detail screen is open, and the screen has to be able to say so.
            observeTraffic().collect { entries ->
                reduce { copy(entry = entries.firstOrNull { it.id == entryId }) }
            }
        }

        viewModelScope.launch {
            combine(
                observeAddresses(),
                getSettings(),
                observeServerStatus(),
            ) { addresses, settings, status ->
                val port = (status as? ServerStatus.Running)?.address?.port ?: settings.port
                addresses.firstOrNull()?.toBaseUrl(port)
                    ?: "http://${AppConstants.Server.LOOPBACK_HOST}:$port"
            }.collect { baseUrl -> reduce { copy(baseUrl = baseUrl) } }
        }
    }

    override fun onIntent(intent: TrafficDetailIntent) {
        val entry = currentState.entry
        when (intent) {
            is TrafficDetailIntent.SelectTab -> reduce { copy(selectedTab = intent.tab) }
            TrafficDetailIntent.TogglePrettyPrint -> reduce { copy(isPrettyPrinted = !isPrettyPrinted) }

            TrafficDetailIntent.CopyBody -> {
                val body = when (currentState.selectedTab) {
                    TrafficDetailState.Tab.REQUEST -> entry?.requestBody
                    TrafficDetailState.Tab.RESPONSE -> entry?.responseBody
                }
                body?.let { emit(TrafficDetailEffect.CopiedToClipboard(it)) }
            }

            TrafficDetailIntent.CopyAsCurl -> entry?.let {
                emit(TrafficDetailEffect.CopiedToClipboard(formatAsCurl(it, currentState.baseUrl)))
            }

            TrafficDetailIntent.ShareTranscript -> entry?.let {
                emit(
                    TrafficDetailEffect.ShareText(
                        text = formatAsText(it, currentState.baseUrl),
                        subject = "${it.method} ${it.path}",
                    ),
                )
            }

            TrafficDetailIntent.CreateRuleFromThis ->
                emit(TrafficDetailEffect.NavigateToRouteEditor(entryId))
        }
    }

    private fun emit(effect: TrafficDetailEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }
}
