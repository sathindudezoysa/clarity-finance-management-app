package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.IncomeSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeSourceDao {
    @Query("SELECT * FROM income_sources WHERE userId = :uid ORDER BY createdAt, id")
    fun observe(uid: String): Flow<List<IncomeSourceEntity>>
    @Query("SELECT * FROM income_sources WHERE userId = :uid AND id = :id")
    suspend fun get(uid: String, id: String): IncomeSourceEntity?
    @Query("SELECT * FROM income_sources WHERE userId = :uid AND pendingSync = 1 ORDER BY id")
    suspend fun pending(uid: String): List<IncomeSourceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: IncomeSourceEntity)
    @Query("UPDATE income_sources SET pendingSync = 0 WHERE userId = :uid AND id = :id AND updatedAt = :version")
    suspend fun acknowledge(uid: String, id: String, version: Long)
}
