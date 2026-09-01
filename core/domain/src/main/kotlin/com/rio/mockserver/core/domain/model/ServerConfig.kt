package com.rio.mockserver.core.domain.model

import com.rio.mockserver.core.common.config.AppConstants

/**
 * What the socket is opened with (SPEC §8.4).
 *
 * Captured when the engine starts and held for the lifetime of that run. Everything *here*
 * requires a restart to change, and everything hot lives in [RoutingSnapshot] instead — that
 * split is what makes "editing a route never restarts the engine" (SPEC §5.5) checkable rather
 * than a convention.
 */
data class ServerConfig(
    /** The port the user asked for. The port actually bound may differ — see [BoundAddress]. */
    val port: Int = AppConstants.Server.DEFAULT_PORT,

    /** `0.0.0.0`, so LAN clients and on-device `localhost` callers share one socket. */
    val bindAddress: String = AppConstants.Server.DEFAULT_BIND_ADDRESS,

    /** `null` disables the guard. When set, every mock route requires the header (SPEC §8.6). */
    val apiKey: ApiKeyConfig? = null,

    /** How many consecutive ports to try after the requested one is refused. */
    val portFallbackAttempts: Int = AppConstants.Server.PORT_FALLBACK_ATTEMPTS,

    /** Requests with a larger body are answered `413` (SPEC §5.4). */
    val maxRequestBodyBytes: Long = AppConstants.Request.MAX_BODY_BYTES,
) {

    /** The ports [port] may end up on, in the order they are tried. */
    val candidatePorts: List<Int>
        get() = (port..(port + portFallbackAttempts)).filter { it <= AppConstants.Server.MAX_PORT }
}

/**
 * The optional API key (SPEC §8.6).
 *
 * Not security — anything on the LAN can read the key off a request. It is a guard against the
 * *other* laptop on the coffee-shop Wi-Fi stumbling into your endpoints, and the server screen
 * says so in as many words rather than implying more.
 */
data class ApiKeyConfig(
    /** Default `X-Mock-Key`, configurable. */
    val headerName: String = AppConstants.Server.DEFAULT_API_KEY_HEADER,

    /** A random 16-character token by default, editable. */
    val value: String,
)

/** Where the server actually ended up listening. */
data class BoundAddress(
    /** The bind address, not a client-reachable one — see `ObserveServerAddressesUseCase`. */
    val host: String,

    /** The port that accepted the bind, which is not necessarily the one requested (SPEC §8.4). */
    val port: Int,
)

/**
 * The engine's lifecycle, as a closed set.
 *
 * [Running] carries [Running.requestedPort] alongside the bound one so the UI can say "port 8080
 * was in use — running on 8081" without having to remember what was asked for.
 */
sealed interface ServerStatus {

    /** Not listening. The state after a clean stop and on a fresh launch (SPEC §8.3). */
    data object Stopped : ServerStatus

    /** Binding. Brief, but long enough that the button needs a state for it. */
    data object Starting : ServerStatus

    /** Listening. */
    data class Running(
        val address: BoundAddress,
        val requestedPort: Int,
        val startedAt: Long,
    ) : ServerStatus {

        /** True when auto-fallback moved the server off the requested port. */
        val didFallBack: Boolean get() = address.port != requestedPort
    }

    /** Shutting down. */
    data object Stopping : ServerStatus

    /** The last start attempt failed. Carries the error so the screen can offer the right fix. */
    data class Failed(
        val error: com.rio.mockserver.core.common.error.DomainError,
    ) : ServerStatus

    /** Whether the engine is accepting connections right now. */
    val isRunning: Boolean get() = this is Running
}

/**
 * One address a client could reach the server on, and how likely that is to work (SPEC §8.5).
 *
 * Ranked rather than listed, because on a phone with Wi-Fi, a hotspot and a VPN up at once, three
 * of the four addresses are wrong and picking the right one is the entire job of this screen.
 */
data class NetworkAddress(
    /** The interface name, e.g. `wlan0`. Shown as a subtitle so the choice is explicable. */
    val interfaceName: String,

    /** The IPv4 literal. */
    val hostAddress: String,

    /** Which bucket this interface fell into. Lower ordinal ranks higher. */
    val kind: Kind,
) {

    /** Interface classes, in the order SPEC §8.5 ranks them. */
    enum class Kind {
        /** `wlan*` — the usual case. */
        WIFI,

        /** `ap*` / `swlan*` — the phone's own hotspot. */
        HOTSPOT,

        /** `rndis*` — USB tethering. */
        USB_TETHER,

        /** Anything unrecognised but up and non-loopback. */
        OTHER,

        /** `tun*` — always last: a LAN client cannot route to a VPN address. */
        VPN,
    }

    /** `http://<ip>:<port>` — what goes on the clipboard and into the QR code. */
    fun toBaseUrl(port: Int): String = "http://$hostAddress:$port"
}
