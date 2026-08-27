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

val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
)
