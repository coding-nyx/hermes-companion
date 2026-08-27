package com.hermes.companion.data.repo

import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.OutboundDao
import com.hermes.companion.data.db.OutboundEntity
import com.hermes.companion.data.db.GatewayDao
import com.hermes.companion.data.db.GatewayEntity
import com.hermes.companion.data.db.MessageDao
import com.hermes.companion.data.db.MessageEntity
import com.hermes.companion.data.db.ProfileDao
import com.hermes.companion.data.db.ProfileEntity
import com.hermes.companion.data.db.RunDao
import com.hermes.companion.data.db.RunEntity
import com.hermes.companion.data.db.SessionDao
import com.hermes.companion.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory doubles for the five DAOs, so repository behaviour can be tested
 * without a device or Robolectric. They enforce the same keys Room does.
 */
internal class FakeGatewayDao : GatewayDao {
    val rows = MutableStateFlow<List<GatewayEntity>>(emptyList())
    override fun observeAll(): Flow<List<GatewayEntity>> = rows.map { it.sortedBy { r -> r.label } }
    override suspend fun all() = rows.value.sortedBy { it.label }
    override suspend fun find(id: String) = rows.value.firstOrNull { it.id == id }
    override suspend fun upsert(gateway: GatewayEntity) {
        rows.value = rows.value.filterNot { it.id == gateway.id } + gateway
    }
    override suspend fun setHealth(id: String, health: String, error: String?, lastOkAt: Long?, staleSince: Long?) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(health = health, error = error, lastOkAt = lastOkAt, staleSince = staleSince) else it
        }
    }
    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

internal class FakeProfileDao : ProfileDao {
    val rows = MutableStateFlow<List<ProfileEntity>>(emptyList())
    override fun observeAll(): Flow<List<ProfileEntity>> = rows
    override suspend fun upsertAll(profiles: List<ProfileEntity>) {
        val keys = profiles.map { it.gatewayId to it.profileId }.toSet()
        rows.value = rows.value.filterNot { (it.gatewayId to it.profileId) in keys } + profiles
    }
    override suspend fun deleteForGateway(gatewayId: String) {
        rows.value = rows.value.filterNot { it.gatewayId == gatewayId }
    }
}

internal class FakeSessionDao : SessionDao {
    val rows = MutableStateFlow<List<SessionEntity>>(emptyList())
    override fun observeAll(): Flow<List<SessionEntity>> = rows
    override fun observeForProfile(gatewayId: String, profileId: String) =
        rows.map { list -> list.filter { it.gatewayId == gatewayId && it.profileId == profileId } }
    override suspend fun upsert(session: SessionEntity) {
        rows.value = rows.value.filterNot {
            it.gatewayId == session.gatewayId && it.profileId == session.profileId && it.sessionId == session.sessionId
        } + session
    }
    override suspend fun upsertAll(sessions: List<SessionEntity>) {
        val keys = sessions.map { Triple(it.gatewayId, it.profileId, it.sessionId) }.toSet()
        rows.value = rows.value.filterNot { Triple(it.gatewayId, it.profileId, it.sessionId) in keys } + sessions
    }
    override suspend fun deleteForGateway(gatewayId: String) {
        rows.value = rows.value.filterNot { it.gatewayId == gatewayId }
    }
}

internal class FakeMessageDao : MessageDao {
    val rows = MutableStateFlow<List<MessageEntity>>(emptyList())
    override fun observeAll(): Flow<List<MessageEntity>> = rows.map { it.sortedByDescending { m -> m.createdAt } }
    override fun observeRoute(gatewayId: String, profileId: String, sessionId: String) =
        rows.map { list ->
            list.filter { it.gatewayId == gatewayId && it.profileId == profileId && it.sessionId == sessionId }
                .sortedBy { it.createdAt }
        }
    override suspend fun insertAll(messages: List<MessageEntity>) {
        val existing = rows.value.map { it.id }.toSet()
        rows.value = rows.value + messages.filterNot { it.id in existing }
    }
    override suspend fun upsert(message: MessageEntity) {
        rows.value = rows.value.filterNot { it.id == message.id } + message
    }
    override suspend fun findRunMessage(gatewayId: String, profileId: String, sessionId: String, runId: String) =
        rows.value.firstOrNull {
            it.gatewayId == gatewayId && it.profileId == profileId &&
                it.sessionId == sessionId && it.runId == runId && it.role == "assistant"
        }
    override suspend fun deleteConfirmed(gatewayId: String, profileId: String, sessionId: String) {
        rows.value = rows.value.filterNot {
            it.gatewayId == gatewayId && it.profileId == profileId &&
                it.sessionId == sessionId && !it.pending && !it.streaming
        }
    }
    override suspend fun deleteForGateway(gatewayId: String) {
        rows.value = rows.value.filterNot { it.gatewayId == gatewayId }
    }
    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

internal class FakeRunDao : RunDao {
    val rows = MutableStateFlow<List<RunEntity>>(emptyList())
    override fun observeAll(): Flow<List<RunEntity>> = rows.map { it.sortedByDescending { r -> r.updatedAt } }
    override fun observeRoute(gatewayId: String, profileId: String, sessionId: String) =
        rows.map { list ->
            list.filter { it.gatewayId == gatewayId && it.profileId == profileId && it.sessionId == sessionId }
                .sortedByDescending { it.updatedAt }
        }
    override suspend fun openRuns() = rows.value.filter { it.state == "streaming" || it.state == "awaiting_approval" }
    override fun observeOpenRuns() =
        rows.map { list -> list.filter { it.state == "streaming" || it.state == "awaiting_approval" } }
    override suspend fun find(gatewayId: String, profileId: String, sessionId: String, runId: String) =
        rows.value.firstOrNull {
            it.gatewayId == gatewayId && it.profileId == profileId &&
                it.sessionId == sessionId && it.runId == runId
        }
    override suspend fun upsert(run: RunEntity) {
        rows.value = rows.value.filterNot {
            it.gatewayId == run.gatewayId && it.profileId == run.profileId &&
                it.sessionId == run.sessionId && it.runId == run.runId
        } + run
    }
    override suspend fun deleteForGateway(gatewayId: String) {
        rows.value = rows.value.filterNot { it.gatewayId == gatewayId }
    }
}

internal class Fakes {
    val gateways = FakeGatewayDao()
    val profiles = FakeProfileDao()
    val sessions = FakeSessionDao()
    val messages = FakeMessageDao()
    val runs = FakeRunDao()
    val outbound = FakeOutboundDao()
    val store = CompanionStore(gateways, profiles, sessions, messages, runs, outbound)
}

internal class FakeOutboundDao : OutboundDao {
    private val rows = kotlinx.coroutines.flow.MutableStateFlow<List<OutboundEntity>>(emptyList())
    override fun observeAll() = rows.map { it.sortedByDescending { r -> r.createdAt } }
    override suspend fun pending() = rows.value.filter { it.state == "Queued" || it.state == "Unacknowledged" }
    override suspend fun find(id: String) = rows.value.firstOrNull { it.id == id }
    override suspend fun findByKey(key: String) = rows.value.firstOrNull { it.idempotencyKey == key }
    override suspend fun upsert(row: OutboundEntity) {
        rows.value = rows.value.filterNot { it.id == row.id } + row
    }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun deleteForGateway(gatewayId: String) { rows.value = rows.value.filterNot { it.gatewayId == gatewayId } }
}
