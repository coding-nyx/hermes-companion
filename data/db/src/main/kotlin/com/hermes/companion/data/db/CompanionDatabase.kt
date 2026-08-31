package com.hermes.companion.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GatewayEntity::class,
        ProfileEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        RunEntity::class,
        OutboundEntity::class,
        NodeIdentityEntity::class,
        ActiveGatewayEntity::class,
        GrantEntity::class,
        LeaseEntity::class,
        StreamRuleEntity::class,
    ],
    version = 9,
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
    abstract fun streamRules(): StreamRuleDao
    abstract fun activeGateway(): ActiveGatewayDao
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
    val streamRules: StreamRuleDao,
    val activeGateway: ActiveGatewayDao,
)

fun openCompanionStore(context: Context): CompanionStore {
    Log.i("BootSequence", "openCompanionStore: Room.databaseBuilder(\"companion.db\").addMigrations(ALL_MIGRATIONS).build()")
    // No fallbackToDestructiveMigration: losing an operator's transcript to a
    // schema bump is not an acceptable default. Every version bump adds a tested
    // migration to ALL_MIGRATIONS.
    val db = Room.databaseBuilder(context, CompanionDatabase::class.java, "companion.db")
        .addMigrations(*ALL_MIGRATIONS)
        // Log every Room callback so a cold-boot migration failure or a v8->v9
        // migration mismatch is observable from logcat without a debug build
        // attached to Android Studio.
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                Log.i("BootSequence", "Room: onCreate (fresh DB; schema written for v9)")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                val v = db.version
                Log.i("BootSequence", "Room: onOpen (user_version=$v, all migrations up-to-date)")
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                Log.w("BootSequence", "Room: onDestructiveMigration (this should NEVER fire; ALL_MIGRATIONS is exhaustive)")
            }
        })
        .build()
    Log.i("BootSequence", "openCompanionStore: Room database built; DAOs wired")
    return CompanionStore(
        db.gateways(),
        db.profiles(),
        db.sessions(),
        db.messages(),
        db.runs(),
        db.outbound(),
        db.nodeIdentity(),
        db.grants(),
        db.leases(),
        db.streamRules(),
        db.activeGateway(),
    )
}
