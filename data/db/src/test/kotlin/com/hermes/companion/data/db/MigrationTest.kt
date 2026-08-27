package com.hermes.companion.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Proves the schema upgrade path. The project keeps fallbackToDestructiveMigration
 * OFF, so a broken or missing migration is a crash on every user's next update —
 * this test is the guard. Every new version bump adds a case here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CompanionDatabase::class.java,
    )

    @Test
    fun migrate1To2_addsOutbound_andKeepsData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO gateways (id,label,kind,url,authRef,health,lastOkAt,staleSince,error) " +
                    "VALUES ('gw-1','Home','RemoteHttp','http://x/gw-1','none','Healthy',1,NULL,NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // The pre-existing gateway row survived the upgrade.
        db.query("SELECT COUNT(*) FROM gateways").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        // The new outbound table exists and enforces the unique idempotency key.
        db.execSQL(
            "INSERT INTO outbound (id,gatewayId,profileId,sessionId,text,idempotencyKey," +
                "createdAt,attempts,state,runId,expiresAt,attachmentBytes,lastError) " +
                "VALUES ('s1','gw-1','ash','sess','hi','key-1',1,0,'Queued',NULL,NULL,0,NULL)",
        )
        db.query("SELECT COUNT(*) FROM outbound").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        db.close()
    }


    @Test
    fun migrate2To3_addsNodeIdentity() {
        helper.createDatabase(dbName, 1).close()
        // Walk the full chain so ordering is exercised.
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        db.execSQL(
            "INSERT INTO node_identity (gatewayId,nodeId,brokerUrl,token,expiresAt,grantedCapsCsv,pairedAt) " +
                "VALUES ('gw-1','node-1','ws://x/ws/node','tok',1,'device.status',1)",
        )
        db.query("SELECT token FROM node_identity WHERE gatewayId='gw-1'").use { c ->
            c.moveToFirst(); assertEquals("tok", c.getString(0))
        }
        db.close()
    }


    @Test
    fun migrate3To4_addsGrantsAndLeases() {
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        db.execSQL(
            "INSERT INTO grants (gatewayId,profileId,nodeId,capability,mode,expiry,policy,updatedAt) " +
                "VALUES ('gw','','n','device.status','AllowWhileUnlocked',NULL,NULL,1)",
        )
        db.execSQL(
            "INSERT INTO leases (capability,gatewayId,profileId,requestId,acquiredAt,expiresAt) " +
                "VALUES ('camera.snap','gw','','r1',1,999)",
        )
        db.query("SELECT COUNT(*) FROM grants").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM leases").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.close()
    }

    @Suppress("unused")
    private fun ctx() = ApplicationProvider.getApplicationContext<android.content.Context>()
}
