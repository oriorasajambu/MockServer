package com.rio.mockserver.core.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.rio.mockserver.core.common.dispatcher.IoDispatcher
import com.rio.mockserver.core.data.util.NetworkInterfaceUtil
import com.rio.mockserver.core.domain.model.NetworkAddress
import com.rio.mockserver.core.domain.repository.NetworkAddressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * Re-resolves the device's addresses on every connectivity change (SPEC §8.5).
 *
 * A `Flow` rather than a one-shot read, because the IP changes the instant Wi-Fi flips or a
 * hotspot comes up — and a stale address on the server screen costs someone ten minutes of typing
 * it into a laptop and wondering why nothing connects.
 *
 * The callback fires for *transitions*, so the flow emits once immediately on collection too;
 * otherwise a screen opened while the network is already settled would show nothing until
 * something changed.
 */
@Singleton
internal class NetworkAddressRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkInterfaceUtil: NetworkInterfaceUtil,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NetworkAddressRepository {

    override fun observeAddresses(): Flow<List<NetworkAddress>> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = resend()
            override fun onLost(network: Network) = resend()
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = resend()

            private fun resend() {
                trySend(networkInterfaceUtil.addresses())
            }
        }

        // The first emission, before any transition — see the class KDoc.
        trySend(networkInterfaceUtil.addresses())

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // registerNetworkCallback throws if the process has already registered too many; failing
        // to observe changes is far better than crashing the screen that shows the address.
        val registered = runCatching {
            connectivityManager?.registerNetworkCallback(request, callback)
        }.isSuccess

        awaitClose {
            if (registered) {
                runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
            }
        }
    }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)
}
