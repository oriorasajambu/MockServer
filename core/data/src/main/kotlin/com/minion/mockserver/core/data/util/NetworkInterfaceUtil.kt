package com.minion.mockserver.core.data.util

import com.minion.mockserver.core.domain.model.NetworkAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates and ranks the addresses a client could reach this device on (SPEC §8.5).
 *
 * Ranking rather than listing, because on a phone with Wi-Fi, a hotspot and a VPN up at once,
 * three of the four addresses are wrong and picking the right one is the entire job of the server
 * screen. The order is the one SPEC §8.5 fixes:
 *
 * 1. `wlan*` — Wi-Fi, the usual case
 * 2. `ap*` / `swlan*` — the phone's own hotspot
 * 3. `rndis*` — USB tethering
 * 4. anything else that is up and not loopback
 * 5. `tun*` — VPN, always last, because a LAN client cannot route to it
 *
 * IPv4 only. A link-local IPv6 address is technically reachable and practically unusable: nobody
 * is typing `http://[fe80::1%wlan0]:8080` into a laptop.
 */
@Singleton
class NetworkInterfaceUtil @Inject constructor() {

    /** Every usable address, best first. Empty means localhost-only. */
    fun addresses(): List<NetworkAddress> = try {
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            .orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                networkInterface.inetAddresses.asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                    .mapNotNull { address ->
                        address.hostAddress?.let { host ->
                            NetworkAddress(
                                interfaceName = networkInterface.name,
                                hostAddress = host,
                                kind = classify(networkInterface.name),
                            )
                        }
                    }
            }
            .sortedBy { it.kind.ordinal }
            .toList()
    } catch (_: Exception) {
        // Enumeration throws on some devices when the radio is mid-transition. An empty list is
        // the honest answer: the UI then shows "No network — localhost only" rather than a stale
        // address someone would type into a laptop and wait on.
        emptyList()
    }

    private fun classify(name: String): NetworkAddress.Kind = when {
        name.startsWith(WIFI_PREFIX) -> NetworkAddress.Kind.WIFI
        name.startsWith(HOTSPOT_PREFIX) || name.startsWith(SAMSUNG_HOTSPOT_PREFIX) ->
            NetworkAddress.Kind.HOTSPOT

        name.startsWith(USB_PREFIX) -> NetworkAddress.Kind.USB_TETHER
        name.startsWith(VPN_PREFIX) || name.startsWith(PPP_PREFIX) -> NetworkAddress.Kind.VPN
        else -> NetworkAddress.Kind.OTHER
    }

    private companion object {
        const val WIFI_PREFIX = "wlan"
        const val HOTSPOT_PREFIX = "ap"
        const val SAMSUNG_HOTSPOT_PREFIX = "swlan"
        const val USB_PREFIX = "rndis"
        const val VPN_PREFIX = "tun"
        const val PPP_PREFIX = "ppp"
    }
}
