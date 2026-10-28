package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.TransactionTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionTagDao {
    // Include tags received through transaction sync as well as saved category tags.
    @Query("""
        SELECT name FROM transaction_tags WHERE userId = :userId
        UNION
        SELECT category AS name FROM transactions WHERE userId = :userId AND isDeleted = 0
        ORDER BY name COLLATE NOCASE
    """)
    fun observeTags(userId: String): Flow<List<String>>

    @Query("""
        SELECT name FROM transaction_tags WHERE userId = :userId AND type = :type
        UNION
        SELECT category AS name FROM transactions WHERE userId = :userId AND type = :type AND isDeleted = 0
        ORDER BY name COLLATE NOCASE
    """)
    fun observeTagsByType(userId: String, type: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransactionTagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TransactionTagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(entities: List<TransactionTagEntity>)

    @Query("SELECT * FROM transaction_tags WHERE userId = :userId AND type = :type AND name = :name LIMIT 1")
    suspend fun getTag(userId: String, type: String, name: String): TransactionTagEntity?

    @Query("SELECT * FROM transaction_tags WHERE userId = :userId AND pendingSync = 1")
    suspend fun getPendingForUser(userId: String): List<TransactionTagEntity>

    @Query("SELECT * FROM transaction_tags WHERE userId = :userId AND pendingSync = 1")
    fun observePendingForUser(userId: String): Flow<List<TransactionTagEntity>>

    @Query("""
        UPDATE transaction_tags
        SET pendingSync = 0
        WHERE userId = :userId AND type = :type AND name = :name AND updatedAt = :updatedAt
    """)
    suspend fun acknowledge(userId: String, type: String, name: String, updatedAt: Long)
}
