package com.rio.mockserver.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.rio.mockserver.core.common.dispatcher.ApplicationScope
import com.rio.mockserver.core.data.preferences.ServerSettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * The preferences DataStore.
 *
 * Built through the factory rather than the `preferencesDataStore` property delegate, because the
 * delegate ties the instance to a `Context` receiver — which makes it awkward to inject and easy
 * to construct twice, and DataStore throws if two instances open the same file.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile(ServerSettingsDataStore.FILE_NAME)
    }
}
