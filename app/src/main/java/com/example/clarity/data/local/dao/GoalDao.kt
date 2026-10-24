package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY targetDateEpochDay ASC, createdAt ASC, id ASC")
    fun observe(userId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE userId = :userId AND id = :id")
    suspend fun get(userId: String, id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE userId = :userId AND pendingSync = 1 ORDER BY updatedAt ASC, id ASC")
    suspend fun pending(userId: String): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GoalEntity)

    @Query("UPDATE goals SET pendingSync = 0 WHERE userId = :userId AND id = :id AND updatedAt = :updatedAt")
    suspend fun acknowledge(userId: String, id: String, updatedAt: Long)
}
