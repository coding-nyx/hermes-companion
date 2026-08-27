package com.hermes.companion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Interfaces, not abstract classes, so `:data:repo` can be tested against
 * fakes without a device or Robolectric.
 */

@Dao
interface GatewayDao {
    @Query("SELECT * FROM gateways ORDER BY label")
    fun observeAll(): Flow<List<GatewayEntity>>

    @Query("SELECT * FROM gateways ORDER BY label")
    suspend fun all(): List<GatewayEntity>

    @Query("SELECT * FROM gateways WHERE id = :id")
    suspend fun find(id: String): GatewayEntity?

    @Upsert
    suspend fun upsert(gateway: GatewayEntity)

    @Query("UPDATE gateways SET health = :health, error = :error, lastOkAt = :lastOkAt, staleSince = :staleSince WHERE id = :id")
    suspend fun setHealth(id: String, health: String, error: String?, lastOkAt: Long?, staleSince: Long?)

    @Query("DELETE FROM gateways WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY gatewayId, profileId")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Upsert
    suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE gatewayId = :gatewayId")
    suspend fun deleteForGateway(gatewayId: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE gatewayId = :gatewayId AND profileId = :profileId ORDER BY updatedAt DESC")
    fun observeForProfile(gatewayId: String, profileId: String): Flow<List<SessionEntity>>

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE gatewayId = :gatewayId")
    suspend fun deleteForGateway(gatewayId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT 50")
    fun observeAll(): Flow<List<MessageEntity>>

    /** Route-keyed on purpose; session id alone is not an identity. */
    @Query(
        "SELECT * FROM messages WHERE gatewayId = :gatewayId AND profileId = :profileId " +
            "AND sessionId = :sessionId ORDER BY createdAt ASC, rowid ASC"
    )
    fun observeRoute(gatewayId: String, profileId: String, sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query(
        "SELECT * FROM messages WHERE gatewayId = :gatewayId AND profileId = :profileId " +
            "AND sessionId = :sessionId AND runId = :runId AND role = 'assistant' LIMIT 1"
    )
    suspend fun findRunMessage(gatewayId: String, profileId: String, sessionId: String, runId: String): MessageEntity?

    /** Server-confirmed history replaces what we cached, but never a pending row. */
    @Query(
        "DELETE FROM messages WHERE gatewayId = :gatewayId AND profileId = :profileId " +
            "AND sessionId = :sessionId AND pending = 0 AND streaming = 0"
    )
    suspend fun deleteConfirmed(gatewayId: String, profileId: String, sessionId: String)

    @Query("DELETE FROM messages WHERE gatewayId = :gatewayId")
    suspend fun deleteForGateway(gatewayId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY updatedAt DESC LIMIT 50")
    fun observeAll(): Flow<List<RunEntity>>

    @Query(
        "SELECT * FROM runs WHERE gatewayId = :gatewayId AND profileId = :profileId " +
            "AND sessionId = :sessionId ORDER BY updatedAt DESC"
    )
    fun observeRoute(gatewayId: String, profileId: String, sessionId: String): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE state IN ('streaming', 'awaiting_approval')")
    suspend fun openRuns(): List<RunEntity>

    @Query("SELECT * FROM runs WHERE state IN ('streaming', 'awaiting_approval')")
    fun observeOpenRuns(): Flow<List<RunEntity>>

    @Query(
        "SELECT * FROM runs WHERE gatewayId = :gatewayId AND profileId = :profileId " +
            "AND sessionId = :sessionId AND runId = :runId"
    )
    suspend fun find(gatewayId: String, profileId: String, sessionId: String, runId: String): RunEntity?

    @Upsert
    suspend fun upsert(run: RunEntity)

    @Query("DELETE FROM runs WHERE gatewayId = :gatewayId")
    suspend fun deleteForGateway(gatewayId: String)
}

@Dao
interface OutboundDao {
    @Query("SELECT * FROM outbound ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OutboundEntity>>

    @Query("SELECT * FROM outbound WHERE state IN ('Queued', 'Unacknowledged') ORDER BY createdAt ASC")
    suspend fun pending(): List<OutboundEntity>

    @Query("SELECT * FROM outbound WHERE id = :id")
    suspend fun find(id: String): OutboundEntity?

    @Query("SELECT * FROM outbound WHERE idempotencyKey = :key")
    suspend fun findByKey(key: String): OutboundEntity?

    @Upsert
    suspend fun upsert(row: OutboundEntity)

    @Query("DELETE FROM outbound WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM outbound WHERE gatewayId = :gatewayId")
    suspend fun deleteForGateway(gatewayId: String)
}

@Dao
interface NodeIdentityDao {
    @Query("SELECT * FROM node_identity")
    fun observeAll(): Flow<List<NodeIdentityEntity>>

    @Query("SELECT * FROM node_identity WHERE gatewayId = :gatewayId")
    suspend fun find(gatewayId: String): NodeIdentityEntity?

    @Upsert
    suspend fun upsert(identity: NodeIdentityEntity)

    @Query("DELETE FROM node_identity WHERE gatewayId = :gatewayId")
    suspend fun deleteForGateway(gatewayId: String)
}
