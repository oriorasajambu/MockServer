package com.rio.mockserver.core.data.di

import com.rio.mockserver.core.data.repository.ChaosRepositoryImpl
import com.rio.mockserver.core.data.repository.CollectionRepositoryImpl
import com.rio.mockserver.core.data.repository.NetworkAddressRepositoryImpl
import com.rio.mockserver.core.data.repository.RouteRepositoryImpl
import com.rio.mockserver.core.data.repository.ServerSettingsRepositoryImpl
import com.rio.mockserver.core.data.repository.TrafficRepositoryImpl
import com.rio.mockserver.core.domain.repository.ChaosRepository
import com.rio.mockserver.core.domain.repository.CollectionRepository
import com.rio.mockserver.core.domain.repository.ExportGateway
import com.rio.mockserver.core.domain.repository.ImportGateway
import com.rio.mockserver.core.domain.repository.MockServerRepository
import com.rio.mockserver.core.domain.repository.NetworkAddressRepository
import com.rio.mockserver.core.domain.repository.RouteRepository
import com.rio.mockserver.core.domain.repository.ServerSettingsRepository
import com.rio.mockserver.core.domain.repository.TrafficRepository
import com.rio.mockserver.core.importexport.exporter.ExportGatewayImpl
import com.rio.mockserver.core.importexport.importer.ImportGatewayImpl
import com.rio.mockserver.core.server.MockServerRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds every domain repository interface to its implementation.
 *
 * All `@Singleton`, and deliberately so for three of them: the traffic log *is* its buffer, the
 * server repository delegates to a single engine holding a socket, and a second instance of
 * either would mean two logs or two servers with the UI watching the wrong one.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCollectionRepository(impl: CollectionRepositoryImpl): CollectionRepository

    @Binds
    @Singleton
    abstract fun bindRouteRepository(impl: RouteRepositoryImpl): RouteRepository

    @Binds
    @Singleton
    abstract fun bindChaosRepository(impl: ChaosRepositoryImpl): ChaosRepository

    @Binds
    @Singleton
    abstract fun bindTrafficRepository(impl: TrafficRepositoryImpl): TrafficRepository

    @Binds
    @Singleton
    abstract fun bindServerSettingsRepository(
        impl: ServerSettingsRepositoryImpl,
    ): ServerSettingsRepository

    @Binds
    @Singleton
    abstract fun bindNetworkAddressRepository(
        impl: NetworkAddressRepositoryImpl,
    ): NetworkAddressRepository

    @Binds
    @Singleton
    abstract fun bindMockServerRepository(impl: MockServerRepositoryImpl): MockServerRepository

    @Binds
    @Singleton
    abstract fun bindImportGateway(impl: ImportGatewayImpl): ImportGateway

    @Binds
    @Singleton
    abstract fun bindExportGateway(impl: ExportGatewayImpl): ExportGateway
}
