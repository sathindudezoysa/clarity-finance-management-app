package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE userId = :uid ORDER BY targetDateEpochDay ASC, createdAt ASC, id ASC")
    fun observe(uid: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE userId = :uid AND id = :id")
    suspend fun get(uid: String, id: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE userId = :uid AND pendingSync = 1 ORDER BY createdAt ASC, id ASC")
    suspend fun pending(uid: String): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Query("UPDATE goals SET pendingSync = 0 WHERE userId = :uid AND id = :id AND updatedAt = :version")
    suspend fun acknowledge(uid: String, id: String, version: Long)
}

