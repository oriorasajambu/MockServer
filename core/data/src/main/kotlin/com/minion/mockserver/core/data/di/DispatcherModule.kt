package com.minion.mockserver.core.data.di

import com.minion.mockserver.core.common.dispatcher.ApplicationScope
import com.minion.mockserver.core.common.dispatcher.DefaultDispatcher
import com.minion.mockserver.core.common.dispatcher.IoDispatcher
import com.minion.mockserver.core.common.dispatcher.MainDispatcher
import com.minion.mockserver.core.domain.util.IdGenerator
import com.minion.mockserver.core.domain.util.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Dispatchers, the application scope, and the two ambient values the domain refuses to reach for
 * on its own.
 *
 * `TimeProvider` and `IdGenerator` are bound here rather than being called directly, so that a
 * test asserting on a timestamp or a saved id does not have to accept whatever the wall clock and
 * a random UUID happened to produce.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    /**
     * The application-lifetime scope.
     *
     * `SupervisorJob`, so one failed child — a snapshot rebuild that threw on a malformed row —
     * does not cancel the traffic-log drain alongside it.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = TimeProvider.System

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = IdGenerator.Uuid
}
