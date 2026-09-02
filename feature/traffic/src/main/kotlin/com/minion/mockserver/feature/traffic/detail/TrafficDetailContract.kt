package com.minion.mockserver.feature.traffic.detail

import com.minion.mockserver.core.common.mvi.UiEffect
import com.minion.mockserver.core.common.mvi.UiIntent
import com.minion.mockserver.core.common.mvi.UiState
import com.minion.mockserver.core.domain.model.TrafficEntry

/** One traffic entry, request and response (SPEC §7.3). */
internal data class TrafficDetailState(
    val entry: TrafficEntry? = null,

    /** Request or Response. A tab rather than one long scroll: they are compared, not read. */
    val selectedTab: Tab = Tab.REQUEST,

    /** Pretty-printed or raw. Raw matters when the point is what the bytes actually were. */
    val isPrettyPrinted: Boolean = true,

    val baseUrl: String = "",
) : UiState {

    enum class Tab { REQUEST, RESPONSE }

    /** True once the entry has been looked up and was not there — the log is a ring buffer. */
    val isMissing: Boolean get() = entry == null
}

internal sealed interface TrafficDetailIntent : UiIntent {
    data class SelectTab(val tab: TrafficDetailState.Tab) : TrafficDetailIntent
    data object TogglePrettyPrint : TrafficDetailIntent
    data object CopyBody : TrafficDetailIntent
    data object CopyAsCurl : TrafficDetailIntent
    data object ShareTranscript : TrafficDetailIntent
    data object CreateRuleFromThis : TrafficDetailIntent
}

internal sealed interface TrafficDetailEffect : UiEffect {
    data class CopiedToClipboard(val text: String) : TrafficDetailEffect
    data class ShareText(val text: String, val subject: String) : TrafficDetailEffect
    data class NavigateToRouteEditor(val entryId: String) : TrafficDetailEffect
}
