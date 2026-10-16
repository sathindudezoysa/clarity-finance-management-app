package com.example.clarity.data.local.dao

import androidx.room.*
import com.example.clarity.data.local.entity.TransactionProofEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionProofDao {
    @Query("SELECT * FROM transaction_proofs WHERE userId = :uid AND transactionId = :transactionId ORDER BY createdAt, id")
    fun observe(uid: String, transactionId: String): Flow<List<TransactionProofEntity>>

    @Query("SELECT * FROM transaction_proofs WHERE userId = :uid AND pendingUpload = 1 ORDER BY createdAt, id")
    fun observePending(uid: String): Flow<List<TransactionProofEntity>>

    @Query("SELECT * FROM transaction_proofs WHERE userId = :uid AND transactionId = :transactionId AND id = :id")
    suspend fun get(uid: String, transactionId: String, id: String): TransactionProofEntity?

    @Query("SELECT COUNT(*) FROM transaction_proofs WHERE userId = :uid AND transactionId = :transactionId")
    suspend fun count(uid: String, transactionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TransactionProofEntity)

    @Query("UPDATE transaction_proofs SET pendingUpload = 0 WHERE userId = :uid AND transactionId = :transactionId AND id = :id AND revision = :revision")
    suspend fun acknowledge(uid: String, transactionId: String, id: String, revision: Long)
}
