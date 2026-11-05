package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.GoalContributionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalContributionDao {
    @Query("SELECT * FROM goal_contributions WHERE userId = :userId ORDER BY dateEpochDay DESC, createdAt DESC, id ASC")
    fun observe(userId: String): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions WHERE userId = :userId AND id = :id")
    suspend fun get(userId: String, id: String): GoalContributionEntity?

    @Query("SELECT * FROM goal_contributions WHERE userId = :userId AND pendingSync = 1 ORDER BY updatedAt ASC, id ASC")
    suspend fun pending(userId: String): List<GoalContributionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GoalContributionEntity)

    @Query("UPDATE goal_contributions SET pendingSync = 0 WHERE userId = :userId AND id = :id AND updatedAt = :updatedAt")
    suspend fun acknowledge(userId: String, id: String, updatedAt: Long)
}

