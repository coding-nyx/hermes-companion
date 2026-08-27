package com.hermes.companion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GatewayEntity::class,
        ProfileEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        RunEntity::class,
        OutboundEntity::class,
        NodeIdentityEntity::class,
        GrantEntity::class,
        LeaseEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
internal abstract class CompanionDatabase : RoomDatabase() {
    abstract fun gateways(): GatewayDao
    abstract fun profiles(): ProfileDao
    abstract fun sessions(): SessionDao
    abstract fun messages(): MessageDao
    abstract fun runs(): RunDao
    abstract fun outbound(): OutboundDao
    abstract fun nodeIdentity(): NodeIdentityDao
    abstract fun grants(): GrantDao
    abstract fun leases(): LeaseDao
}

/**
 * The DAOs, and nothing else. Room itself never appears in this module's public
 * API, so no dependent module — and therefore not `:app` — can reach a
 * `RoomDatabase` or open a second connection to the same file.
 *
 * The constructor is public so tests can substitute in-memory doubles for the
 * DAOs without a device.
 */
class CompanionStore(
    val gateways: GatewayDao,
    val profiles: ProfileDao,
    val sessions: SessionDao,
    val messages: MessageDao,
    val runs: RunDao,
    val outbound: OutboundDao,
    val nodeIdentity: NodeIdentityDao,
    val grants: GrantDao,
    val leases: LeaseDao,
)

fun openCompanionStore(context: Context): CompanionStore {
    // No fallbackToDestructiveMigration: losing an operator's transcript to a
    // schema bump is not an acceptable default. Every version bump adds a tested
    // migration to ALL_MIGRATIONS.
    val db = Room.databaseBuilder(context, CompanionDatabase::class.java, "companion.db")
        .addMigrations(*ALL_MIGRATIONS)
        .build()
    return CompanionStore(db.gateways(), db.profiles(), db.sessions(), db.messages(), db.runs(), db.outbound(), db.nodeIdentity(), db.grants(), db.leases())
}
