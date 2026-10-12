package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionTagDao {
    // Include tags received through the existing transaction sync, as well as saved names.
    @Query("""
        SELECT name FROM transaction_tags WHERE userId = :userId
        UNION
        SELECT category AS name FROM transactions WHERE userId = :userId AND isDeleted = 0
        ORDER BY name COLLATE NOCASE
    """)
    fun observeTags(userId: String): Flow<List<String>>
}
