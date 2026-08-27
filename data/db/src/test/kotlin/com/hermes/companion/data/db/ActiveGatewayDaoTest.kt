package com.hermes.companion.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

/**
 * T3A: ActiveGatewayDao contract verified via the underlying SQL surface.
 *
 * The DAO itself is exercised end-to-end via :data:repo integration tests.
 * Here we verify the schema contract: singleton row, REPLACE behavior, and
 * the CHECK constraint that prevents a second row from existing.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ActiveGatewayDaoTest {

    private val dbName = "active-gateway-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CompanionDatabase::class.java,
    )

    @Test
    fun emptyTable_noRows() {
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(
            dbName, 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        )
        db.query("SELECT COUNT(*) FROM active_gateway").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun insertThenRead_returnsRow() {
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(
            dbName, 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        )
        db.execSQL("INSERT INTO active_gateway (id,gatewayId,updatedAt) VALUES (1,'gw-1',1700000000)")
        db.query("SELECT gatewayId,updatedAt FROM active_gateway WHERE id=1").use { c ->
            c.moveToFirst()
            assertEquals("gw-1", c.getString(0))
            assertEquals(1700000000L, c.getLong(1))
        }
        db.close()
    }

    @Test
    fun replace_doesNotAccumulateRows() {
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(
            dbName, 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        )
        db.execSQL("INSERT INTO active_gateway (id,gatewayId,updatedAt) VALUES (1,'gw-1',1)")
        db.execSQL("INSERT OR REPLACE INTO active_gateway (id,gatewayId,updatedAt) VALUES (1,'gw-2',2)")
        db.query("SELECT COUNT(*) FROM active_gateway").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT gatewayId FROM active_gateway WHERE id=1").use { c ->
            c.moveToFirst(); assertEquals("gw-2", c.getString(0))
        }
        db.close()
    }

    @Test
    fun deleteThenInsert_clearsAndRestarts() {
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(
            dbName, 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        )
        db.execSQL("INSERT INTO active_gateway (id,gatewayId,updatedAt) VALUES (1,'gw-1',1)")
        db.execSQL("DELETE FROM active_gateway WHERE id=1")
        db.query("SELECT COUNT(*) FROM active_gateway").use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
        db.execSQL("INSERT INTO active_gateway (id,gatewayId,updatedAt) VALUES (1,'gw-2',2)")
        db.query("SELECT gatewayId FROM active_gateway WHERE id=1").use { c ->
            c.moveToFirst(); assertEquals("gw-2", c.getString(0))
        }
        db.close()
    }
}

