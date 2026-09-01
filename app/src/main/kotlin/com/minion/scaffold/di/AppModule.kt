package com.minion.scaffold.di

import com.minion.scaffold.BuildConfig
import com.minion.scaffold.core.common.dispatcher.DefaultDispatcher
import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.core.common.dispatcher.MainDispatcher
import com.minion.scaffold.core.network.BaseUrl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Bindings that only the application can make: the ones that depend on which app this is, or on
 * the platform itself.
 *
 * The base URL is here rather than in `:core:network` because `BuildConfig` belongs to `:app`. A
 * core module reading the application's `BuildConfig` is how it quietly stops being reusable.
 *
 * Feature bindings do NOT belong here. A repository implementation is bound by an `internal`
 * `@Module` inside the module that owns it — a central DI module would have to depend on every
 * feature, inverting the dependency direction the module graph exists to enforce.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @BaseUrl
    fun provideBaseUrl(): String = BuildConfig.BASE_URL

    /**
     * The dispatchers, injected rather than referenced directly.
     *
     * A repository that calls `Dispatchers.IO` itself cannot be tested deterministically — the
     * work escapes onto a real thread pool and `runTest` has nothing to advance. Injected, a test
     * substitutes a `TestDispatcher` and controls it.
     */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}
