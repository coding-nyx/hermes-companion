package com.hermes.companion.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change ships an explicit, tested migration. There is no
 * fallbackToDestructiveMigration: an operator's transcript and audit trail must
 * survive an app update. New phases append a MIGRATION_n_(n+1) here and wire it
 * into [ALL_MIGRATIONS] plus a MigrationTest case.
 */

/** v2: the durable outbound outbox. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `outbound` (
                `id` TEXT NOT NULL,
                `gatewayId` TEXT NOT NULL,
                `profileId` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `idempotencyKey` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `attempts` INTEGER NOT NULL,
                `state` TEXT NOT NULL,
                `runId` TEXT,
                `expiresAt` INTEGER,
                `attachmentBytes` INTEGER NOT NULL,
                `lastError` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_outbound_idempotencyKey` ON `outbound` (`idempotencyKey`)",
        )
    }
}

/** v3: paired-node identity + broker credential (kept out of the gateway row). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `node_identity` (
                `gatewayId` TEXT NOT NULL,
                `nodeId` TEXT NOT NULL,
                `brokerUrl` TEXT NOT NULL,
                `token` TEXT NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `grantedCapsCsv` TEXT NOT NULL,
                `pairedAt` INTEGER NOT NULL,
                PRIMARY KEY(`gatewayId`)
            )
            """.trimIndent(),
        )
    }
}

/** v4: capability grants + exclusive leases. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `grants` (
                `gatewayId` TEXT NOT NULL,
                `profileId` TEXT NOT NULL,
                `nodeId` TEXT NOT NULL,
                `capability` TEXT NOT NULL,
                `mode` TEXT NOT NULL,
                `expiry` INTEGER,
                `policy` TEXT,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`gatewayId`, `profileId`, `nodeId`, `capability`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `leases` (
                `capability` TEXT NOT NULL,
                `gatewayId` TEXT NOT NULL,
                `profileId` TEXT NOT NULL,
                `requestId` TEXT NOT NULL,
                `acquiredAt` INTEGER NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                PRIMARY KEY(`capability`)
            )
            """.trimIndent(),
        )
    }
}

/** v5: seal the node token at rest (plaintext token -> sealedToken). Forces re-pair. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Plaintext tokens must not persist post-hardening and cannot be sealed
        // on the migration thread (no Keystore). Recreate the table; re-pair once.
        db.execSQL("DROP TABLE IF EXISTS `node_identity`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `node_identity` (
                `gatewayId` TEXT NOT NULL,
                `nodeId` TEXT NOT NULL,
                `brokerUrl` TEXT NOT NULL,
                `sealedToken` TEXT NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `grantedCapsCsv` TEXT NOT NULL,
                `pairedAt` INTEGER NOT NULL,
                PRIMARY KEY(`gatewayId`)
            )
            """.trimIndent(),
        )
    }
}

/** v6: per-source stream rules (on-device redaction / consent). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stream_rules` (
                `source` TEXT NOT NULL,
                `mode` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`source`)
            )
            """.trimIndent(),
        )
    }
}



/** v7: active-gateway singleton row (T3A). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `active_gateway` (
                `id` INTEGER NOT NULL CHECK (id = 1),
                `gatewayId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
)
