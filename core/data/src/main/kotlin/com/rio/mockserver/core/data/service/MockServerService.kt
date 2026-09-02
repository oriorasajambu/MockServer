package com.rio.mockserver.core.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rio.mockserver.core.common.dispatcher.IoDispatcher
import com.rio.mockserver.core.common.result.AppResult
import com.rio.mockserver.core.common.util.Logger
import com.rio.mockserver.core.data.R
import com.rio.mockserver.core.data.discovery.NsdAdvertiser
import com.rio.mockserver.core.domain.model.ServerStatus
import com.rio.mockserver.core.domain.repository.MockServerRepository
import com.rio.mockserver.core.domain.usecase.server.SetPauseModeUseCase
import com.rio.mockserver.core.domain.usecase.server.StartServerUseCase
import com.rio.mockserver.core.domain.usecase.server.StopServerUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while the server is bound (SPEC §8.1).
 *
 * That is its entire job. The engine is a `@Singleton` in `:core:server` reached through
 * [MockServerRepository]; this service owns the notification, the WifiLock and the start/stop
 * intents, and nothing binds to it. A `:server` process would force IPC for every state read and
 * duplicate the DI graph for no benefit.
 *
 * ### Why `specialUse` and not `dataSync`
 *
 * `dataSync` carries a cumulative ~6-hour daily runtime budget on Android 15+, which would kill
 * an all-day mock session partway through an afternoon with no explanation the user could act on.
 * `specialUse` has no such budget. Internal distribution means the justification in the manifest
 * is never reviewed, but it is accurate either way.
 *
 * ### `START_STICKY`
 *
 * A process the system reclaims is restarted and re-binds the last configuration, with the
 * notification saying so. SPEC §8.3's "manual start only" is about app launch — nothing opens a
 * port because the phone booted — and is not in tension with resuming a session the user did
 * start and the system interrupted.
 */
@AndroidEntryPoint
class MockServerService : Service() {

    @Inject lateinit var serverRepository: MockServerRepository

    @Inject lateinit var startServer: StartServerUseCase

    @Inject lateinit var stopServer: StopServerUseCase

    @Inject lateinit var setPauseMode: SetPauseModeUseCase

    @Inject lateinit var nsdAdvertiser: NsdAdvertiser

    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private lateinit var scope: CoroutineScope

    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        createNotificationChannel()
        observeForNotification()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    stopServer()
                    stopSelfSafely()
                }
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> scope.launch { setPauseMode(true) }
            ACTION_RESUME -> scope.launch { setPauseMode(false) }
            else -> startServing(restarted = intent == null)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWifiLock()
        nsdAdvertiser.unregister()
        scope.cancel()
        super.onDestroy()
    }

    private fun startServing(restarted: Boolean) {
        // The notification has to be posted within a few seconds of startForegroundService, and
        // before the bind attempt — which can take a moment when ten ports are refused in turn.
        promoteToForeground(buildNotification(status = null, requestCount = 0, restarted = restarted))

        scope.launch {
            when (val result = startServer()) {
                is AppResult.Success -> {
                    acquireWifiLock()
                    nsdAdvertiser.register(result.data.port)
                }

                is AppResult.Failure -> {
                    Logger.w(TAG, "server failed to start: ${result.error}")
                    // Nothing to keep alive. Staying foreground with a notification that says the
                    // server is running would be a lie the user has to go and disprove.
                    stopSelfSafely()
                }
            }
        }
    }

    /**
     * Redraws the notification at most once a second (SPEC §8.1).
     *
     * A per-request update stutters the system UI under load, and the counter is the one thing on
     * the notification that changes at request rate.
     */
    private fun observeForNotification() {
        scope.launch {
            combine(
                serverRepository.status,
                serverRepository.requestCount,
            ) { status, count -> status to count }
                .sample(NOTIFICATION_UPDATE_INTERVAL_MILLIS)
                .distinctUntilChanged()
                .collect { (status, count) ->
                    if (status is ServerStatus.Running) {
                        notificationManager()?.notify(
                            NOTIFICATION_ID,
                            buildNotification(status, count, restarted = false),
                        )
                    }
                }
        }
    }

    private fun promoteToForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun buildNotification(
        status: ServerStatus?,
        requestCount: Long,
        restarted: Boolean,
    ): Notification {
        val running = status as? ServerStatus.Running
        val title = running
            ?.let { getString(R.string.notification_running_title, it.address.port) }
            ?: getString(R.string.notification_starting_title)

        val text = when {
            restarted -> getString(R.string.notification_restarted_text)
            running != null -> resources.getQuantityString(
                R.plurals.notification_request_count,
                requestCount.toInt(),
                requestCount,
            )

            else -> getString(R.string.notification_starting_text)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            // Low priority and silent: this notification lives for hours and exists to keep the
            // process alive, not to be read.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setContentIntent(launchIntent())
            .addAction(0, getString(R.string.notification_action_stop), actionIntent(ACTION_STOP))
            .addAction(0, getString(R.string.notification_action_pause), actionIntent(ACTION_PAUSE))
            .build()
    }

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            REQUEST_LAUNCH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, MockServerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        notificationManager()?.createNotificationChannel(channel)
    }

    /**
     * Holds the Wi-Fi radio awake (SPEC §8.2).
     *
     * `WIFI_MODE_FULL_LOW_LATENCY`, not a `WakeLock`. Without the Wi-Fi lock the radio parks with
     * the screen off and connections stall or drop mid-request; with a partial wakelock on top,
     * the battery drains all afternoon for no measurable benefit, because the foreground service
     * already exempts the app from most Doze network restrictions.
     */
    private fun acquireWifiLock() {
        if (wifiLock != null) return
        val wifiManager = applicationContext.getSystemService(WifiManager::class.java) ?: return
        wifiLock = runCatching {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, WIFI_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun releaseWifiLock() {
        wifiLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wifiLock = null
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notificationManager(): NotificationManager? =
        getSystemService(NotificationManager::class.java)

    companion object {
        private const val TAG = "MockServerService"
        private const val CHANNEL_ID = "mock_server"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_LAUNCH = 100
        private const val WIFI_LOCK_TAG = "MockServer:wifi"

        /** SPEC §8.1: the request counter is rate-limited to 1 Hz. */
        private const val NOTIFICATION_UPDATE_INTERVAL_MILLIS = 1_000L

        /** Stops the server and the service. */
        const val ACTION_STOP = "com.rio.mockserver.action.STOP"

        /** Turns pause mode on without closing the socket. */
        const val ACTION_PAUSE = "com.rio.mockserver.action.PAUSE"

        /** Turns pause mode off. */
        const val ACTION_RESUME = "com.rio.mockserver.action.RESUME"

        /** Starts the service, which starts the server with the persisted settings. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, MockServerService::class.java))
        }

        /** Asks the service to stop the server and itself. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, MockServerService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
