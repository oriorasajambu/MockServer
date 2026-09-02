package com.rio.mockserver.feature.settings

import android.content.Context
import com.rio.mockserver.core.data.service.MockServerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Starts and stops the foreground service, so [SettingsViewModel] needs no `Context`. */
@Singleton
internal class SettingsServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start() = MockServerService.start(context)

    fun stop() = MockServerService.stop(context)
}
