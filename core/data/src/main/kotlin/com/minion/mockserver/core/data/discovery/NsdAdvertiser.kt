package com.minion.mockserver.core.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.minion.mockserver.core.common.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advertises the server over mDNS/DNS-SD (SPEC §8.5).
 *
 * `_http._tcp` as `MockServer-<device model>`, so a laptop on the same network can reach it at
 * `mockserver-pixel-8.local` without anyone reading an IP off a phone screen — which is the whole
 * point, since that IP changes every time Wi-Fi reconnects.
 *
 * On a name collision Android appends a suffix and reports the name it actually registered, which
 * is why [registeredName] exists: showing the requested name when the system chose a different
 * one would send people to a host that does not resolve.
 */
@Singleton
class NsdAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _registeredName = MutableStateFlow<String?>(null)

    /** The name the system actually registered, or `null` when nothing is advertised. */
    val registeredName: StateFlow<String?> = _registeredName.asStateFlow()

    private var manager: NsdManager? = null
    private var listener: NsdManager.RegistrationListener? = null

    /** Registers the service on [port]. Safe to call when already registered. */
    fun register(port: Int) {
        if (listener != null) return

        val nsdManager = context.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = defaultServiceName()
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                _registeredName.value = info.serviceName
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Logger.w(TAG, "NSD registration failed with code $errorCode")
                _registeredName.value = null
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                _registeredName.value = null
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Logger.w(TAG, "NSD unregistration failed with code $errorCode")
                _registeredName.value = null
            }
        }

        // Registration throws on devices where multicast is unavailable. Discovery is a
        // convenience layered on an address that is already shown and copyable, so it fails quiet.
        runCatching {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            manager = nsdManager
            listener = registrationListener
        }.onFailure { Logger.w(TAG, "NSD unavailable: ${it.message}") }
    }

    /** Unregisters. Safe to call when nothing is registered. */
    fun unregister() {
        val nsdManager = manager
        val registrationListener = listener
        manager = null
        listener = null
        _registeredName.value = null
        if (nsdManager != null && registrationListener != null) {
            runCatching { nsdManager.unregisterService(registrationListener) }
        }
    }

    /**
     * `MockServer-Pixel-8`.
     *
     * Sanitised to the characters DNS-SD instance names survive: a model containing a space or a
     * slash registers under a name the system silently rewrites, and the UI would then advertise
     * a host that does not resolve.
     */
    private fun defaultServiceName(): String {
        val model = Build.MODEL.orEmpty()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString(separator = "")
            .trim('-')
            .ifBlank { "device" }
        return "$SERVICE_PREFIX-$model"
    }

    private companion object {
        const val TAG = "NsdAdvertiser"
        const val SERVICE_TYPE = "_http._tcp"
        const val SERVICE_PREFIX = "MockServer"
    }
}
