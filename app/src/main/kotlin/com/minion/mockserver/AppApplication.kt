package com.minion.mockserver

import android.app.Application
import android.util.Log
import com.minion.mockserver.core.common.util.Logger
import com.minion.mockserver.core.data.repository.RoutingSnapshotCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The Hilt entry point, and the two things that genuinely cannot be lazy.
 *
 * Everything else stays out of here: work in `onCreate` runs before the first frame on every cold
 * start, so anything put here is paid for by every launch, including launches that never use it.
 *
 * The two exceptions earn their place:
 *
 * - The [Logger] sink, because a log line written before it is installed is silently discarded,
 *   and the first thing worth logging is usually something that happened during startup.
 * - [RoutingSnapshotCoordinator], because it is what keeps a *running* server in step with what is
 *   saved. The server can be running with no activity alive at all — that is the point of the
 *   foreground service — so starting the coordinator from an activity or a ViewModel would mean a
 *   route edited from the notification's process never reached the engine.
 */
@HiltAndroidApp
class AppApplication : Application() {

    @Inject lateinit var snapshotCoordinator: RoutingSnapshotCoordinator

    override fun onCreate() {
        super.onCreate()
        installLogger()
        snapshotCoordinator.start()
    }

    /**
     * Debug builds only.
     *
     * A log line surviving into a release build of a *developer tool* is likely to be printing
     * somebody's request bodies to logcat, where any app with READ_LOGS could read them.
     */
    private fun installLogger() {
        if (!BuildConfig.DEBUG) return
        Logger.sink = Logger.Sink { level, tag, message, throwable ->
            when (level) {
                Logger.Level.DEBUG -> Log.d(tag, message)
                Logger.Level.WARN -> Log.w(tag, message, throwable)
                Logger.Level.ERROR -> Log.e(tag, message, throwable)
            }
        }
    }
}
