package com.rio.mockserver.core.data.di

import android.content.Context
import androidx.room.Room
import com.rio.mockserver.core.data.local.AppDatabase
import com.rio.mockserver.core.data.local.dao.ChaosProfileDao
import com.rio.mockserver.core.data.local.dao.CollectionDao
import com.rio.mockserver.core.data.local.dao.RouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The database and its DAOs.
 *
 * No `fallbackToDestructiveMigration`. When a migration is missing, Room throws at open time and
 * the bug gets fixed; with the fallback, it silently deletes every mock the user has built, and
 * nobody finds out until they look for a collection that is gone.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideCollectionDao(database: AppDatabase): CollectionDao = database.collectionDao()

    @Provides
    fun provideRouteDao(database: AppDatabase): RouteDao = database.routeDao()

    @Provides
    fun provideChaosProfileDao(database: AppDatabase): ChaosProfileDao = database.chaosProfileDao()
}
