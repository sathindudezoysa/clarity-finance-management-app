package com.example.clarity.data.local.dao

import androidx.room.*
import com.example.clarity.data.local.entity.TransactionProofEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionProofDao {
    @Query("SELECT * FROM transaction_proofs WHERE userId = :uid AND transactionId = :transactionId ORDER BY createdAt, id")
    fun observe(uid: String, transactionId: String): Flow<List<TransactionProofEntity>>

    @Query("SELECT DISTINCT transactionId FROM transaction_proofs WHERE userId = :uid AND unavailable = 0")
    fun observeAttachedTransactionIds(uid: String): Flow<List<String>>

    @Query("SELECT p.* FROM transaction_proofs p WHERE p.userId = :uid AND p.pendingUpload = 1 AND p.unavailable = 0 AND EXISTS (SELECT 1 FROM transactions t WHERE t.id = p.transactionId AND t.userId = p.userId AND t.isDeleted = 0) ORDER BY p.createdAt, p.id")
    fun observePending(uid: String): Flow<List<TransactionProofEntity>>

    @Query("SELECT p.* FROM transaction_proofs p WHERE p.userId = :uid AND p.pendingUpload = 1 AND p.unavailable = 0 AND EXISTS (SELECT 1 FROM transactions t WHERE t.id = p.transactionId AND t.userId = p.userId AND t.isDeleted = 0) ORDER BY p.createdAt, p.id")
    suspend fun pending(uid: String): List<TransactionProofEntity>

    @Query("SELECT * FROM transaction_proofs WHERE userId = :uid AND transactionId = :transactionId AND id = :id")
    suspend fun get(uid: String, transactionId: String, id: String): TransactionProofEntity?

    @Query("SELECT COUNT(*) FROM transaction_proofs WHERE userId = :uid AND transactionId = :transactionId")
    suspend fun count(uid: String, transactionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TransactionProofEntity)

    @Query("UPDATE transaction_proofs SET pendingUpload = 0 WHERE userId = :uid AND transactionId = :transactionId AND id = :id AND revision = :revision")
    suspend fun acknowledge(uid: String, transactionId: String, id: String, revision: Long)
    @Query("UPDATE transaction_proofs SET pendingUpload = 0, unavailable = 1, verifiedAmountMinor = NULL WHERE userId = :uid AND transactionId = :transactionId AND id = :id AND revision = :revision AND pendingUpload = 1")
    suspend fun markUnavailable(uid: String, transactionId: String, id: String, revision: Long)

    @Query("DELETE FROM transaction_proofs WHERE userId = :uid AND transactionId = :transactionId AND id = :id AND unavailable = 1 AND pendingUpload = 0")
    suspend fun removeUnavailable(uid: String, transactionId: String, id: String)

}
