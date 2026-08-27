package com.hermes.companion.di

import android.content.Context
import com.hermes.companion.data.repo.ActivityRepository
import com.hermes.companion.data.repo.CompanionData
import com.hermes.companion.data.repo.ConnectionSupervisor
import com.hermes.companion.data.repo.ConversationRepository
import com.hermes.companion.data.repo.DiscoveryRepository
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.data.repo.NodeConnectionManager
import com.hermes.companion.data.repo.NodeRepository
import com.hermes.companion.data.repo.OutboxRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the application-lifetime coroutine scope that owns run observation. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/**
 * The application-scoped object graph. Per-gateway objects (backends, brokers,
 * supervisors) are still owned dynamically by [CompanionData]/BackendRegistry —
 * Hilt only provides the singletons and the repository ports the UI talks to.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideCompanionData(
        @ApplicationContext context: Context,
        @AppScope scope: CoroutineScope,
    ): CompanionData = CompanionData(context, scope)

    @Provides
    fun provideFleetRepository(data: CompanionData): FleetRepository = data.fleet

    @Provides
    fun provideConversationRepository(data: CompanionData): ConversationRepository = data.conversations

    @Provides
    fun provideActivityRepository(data: CompanionData): ActivityRepository = data.activity

    @Provides
    fun provideNodeRepository(data: CompanionData): NodeRepository = data.node

    @Provides
    fun provideDiscoveryRepository(data: CompanionData): DiscoveryRepository = data.discovery

    @Provides
    fun provideOutboxRepository(data: CompanionData): OutboxRepository = data.outbox

    @Provides
    @Singleton
    fun provideConnectionSupervisor(data: CompanionData): ConnectionSupervisor = data.supervisor

    @Provides
    @Singleton
    fun provideNodeConnectionManager(data: CompanionData): NodeConnectionManager = data.nodeConnections
}
