package com.hermes.companion.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * T3A: Active-gateway singleton DAO.
 *
 * The table holds exactly one row (PK = 1). [observe] is the only "list" and
 * returns 0 or 1 row; callers decide what to do when empty.
 *
 * [set] uses REPLACE so a second set overwrites the prior row in one statement.
 * No multi-row write is ever needed - that is the singleton invariant.
 */
@Dao
interface ActiveGatewayDao {

    @Query("SELECT * FROM active_gateway WHERE id = 1")
    fun observe(): Flow<ActiveGatewayEntity?>

    @Query("SELECT * FROM active_gateway WHERE id = 1")
    suspend fun get(): ActiveGatewayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: ActiveGatewayEntity)

    @Query("DELETE FROM active_gateway WHERE id = 1")
    suspend fun clear()
}

