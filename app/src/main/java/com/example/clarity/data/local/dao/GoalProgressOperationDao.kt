package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.GoalProgressOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalProgressOperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(operation: GoalProgressOperationEntity): Long

    @Query("SELECT * FROM goal_progress_operations WHERE userId = :uid AND id = :id")
    suspend fun get(uid: String, id: String): GoalProgressOperationEntity?

    @Query("SELECT * FROM goal_progress_operations WHERE userId = :uid ORDER BY createdAt ASC, id ASC")
    fun observeByUser(uid: String): Flow<List<GoalProgressOperationEntity>>

    @Query("SELECT * FROM goal_progress_operations WHERE userId = :uid ORDER BY createdAt ASC, id ASC")
    suspend fun getByUser(uid: String): List<GoalProgressOperationEntity>

    @Query("SELECT * FROM goal_progress_operations WHERE userId = :uid AND state = 'PENDING' ORDER BY createdAt ASC, id ASC")
    suspend fun pending(uid: String): List<GoalProgressOperationEntity>

    @Query("SELECT * FROM goal_progress_operations WHERE userId = :uid AND goalId = :goalId AND state = 'PENDING' ORDER BY createdAt ASC, id ASC")
    suspend fun pendingForGoal(uid: String, goalId: String): List<GoalProgressOperationEntity>

    @Query("UPDATE goal_progress_operations SET state = 'APPLIED' WHERE userId = :uid AND id = :id")
    suspend fun markApplied(uid: String, id: String)

    @Query("UPDATE goal_progress_operations SET state = 'REJECTED' WHERE userId = :uid AND id = :id")
    suspend fun markRejected(uid: String, id: String)
}
