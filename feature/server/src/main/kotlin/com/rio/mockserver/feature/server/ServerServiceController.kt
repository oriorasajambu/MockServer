package com.rio.mockserver.feature.server

import android.content.Context
import com.rio.mockserver.core.data.service.MockServerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts and stops the foreground service.
 *
 * A seam, so [ServerViewModel] has no `Context` and stays testable. The service, not the
 * ViewModel, issues the actual bind: the engine has to outlive this screen, and a start issued
 * from `viewModelScope` would leave a socket open with nothing keeping the process alive.
 */
@Singleton
internal class ServerServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Starts the service, which starts the server with the persisted settings. */
    fun start() = MockServerService.start(context)

    /** Asks the service to stop the server and itself. */
    fun stop() = MockServerService.stop(context)
}
