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
    ],
    version = 1,
    exportSchema = true,
)
internal abstract class CompanionDatabase : RoomDatabase() {
    abstract fun gateways(): GatewayDao
    abstract fun profiles(): ProfileDao
    abstract fun sessions(): SessionDao
    abstract fun messages(): MessageDao
    abstract fun runs(): RunDao
}

/**
 * The five DAOs, and nothing else. Room itself never appears in this module's
 * public API, so no dependent module — and therefore not `:app` — can reach a
 * `RoomDatabase` or open a second connection to the same file.
 *
 * The constructor is public so tests can substitute in-memory doubles for the
 * five DAOs without a device.
 */
class CompanionStore(
    val gateways: GatewayDao,
    val profiles: ProfileDao,
    val sessions: SessionDao,
    val messages: MessageDao,
    val runs: RunDao,
)

fun openCompanionStore(context: Context): CompanionStore {
    // No fallbackToDestructiveMigration: losing an operator's transcript to a
    // schema bump is not an acceptable default.
    val db = Room.databaseBuilder(context, CompanionDatabase::class.java, "companion.db").build()
    return CompanionStore(db.gateways(), db.profiles(), db.sessions(), db.messages(), db.runs())
}
