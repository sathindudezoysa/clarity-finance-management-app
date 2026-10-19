package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.local.entity.TransactionTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionDao : WalletBalanceReader {
    @Query("SELECT * FROM transactions WHERE userId = :userId AND isDeleted = 0 ORDER BY dateEpochMillis DESC, createdAt DESC")
    abstract fun observeAll(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId AND isDeleted = 0")
    abstract fun observeById(id: String, userId: String): Flow<TransactionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTag(entity: TransactionTagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTags(entities: List<TransactionTagEntity>)

    // A single save creates both the transaction and its reusable tag, including offline.
    @Transaction
    open suspend fun upsertWithTag(entity: TransactionEntity) {
        upsert(entity)
        insertTag(TransactionTagEntity(entity.userId, entity.category))
    }

    @Query("DELETE FROM transactions WHERE id = :id AND userId = :userId")
    abstract suspend fun deleteById(id: String, userId: String)

    @Query("DELETE FROM transactions WHERE userId = :userId")
    abstract suspend fun deleteAllForUser(userId: String)

    @Query("SELECT id FROM transactions WHERE userId = :userId")
    abstract suspend fun getIdsForUser(userId: String): List<String>

    @Query("SELECT * FROM transactions WHERE id = :id")
    abstract suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE userId = :userId AND pendingSync = 1")
    abstract suspend fun getPendingForUser(userId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND pendingSync = 1 ORDER BY id")
    abstract fun observePendingForUser(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM wallet_cash_links WHERE userId = :uid AND transactionId = :id)")
    abstract suspend fun isWalletCash(uid: String, id: String): Boolean

    /** Keep local transaction persistence and dependent wallet integrity in one storage transaction. */
    @Transaction
    open suspend fun persistLocal(entity: TransactionEntity) {
        upsertWithTag(entity)
        if (isWalletCash(entity.userId, entity.id)) {
            com.example.clarity.data.local.validateWalletBalance(balance(entity.userId).value())
        }
    }

    @Transaction
    open suspend fun reconcileRemote(
        userId: String,
        remote: List<TransactionEntity>,
        remoteIds: Set<String>,
        authoritative: Boolean,
        confirmedDeletes: Set<String> = emptySet()
    ) {
        val replacements = remote.mapNotNull { incoming ->
            val local = getById(incoming.id)
            when {
                incoming.userId != userId -> null
                local != null && local.userId != userId -> null
                local?.isDeleted == true -> null
                local?.pendingSync == true -> {
                    // A newer server edit wins; equal timestamps also prefer the server.
                    // Cached data cannot acknowledge or overwrite any pending local edit.
                    if (authoritative && incoming.updatedAt >= local.updatedAt) incoming else null
                }
                local != null && local.updatedAt > incoming.updatedAt -> {
                    // Repair an older remote overwrite instead of leaving the stores divergent.
                    if (authoritative) local.copy(pendingSync = true) else null
                }
                else -> incoming
            }
        }
        upsertAll(replacements)
        insertTags(
            replacements.mapNotNull {
                val tag = it.category.trim()
                if (!it.isDeleted && tag.length in 1..100) TransactionTagEntity(it.userId, tag) else null
            }.distinct()
        )
        // An incomplete cache snapshot is never evidence of a remote deletion.
        if (authoritative) {
            (getIdsForUser(userId) - remoteIds).forEach { id ->
                val local = getById(id)
                // A pending edit wins a remote deletion and is retried as a recreation.
                // Tombstones need both a delete acknowledgement and fresh server absence.
                if (local != null && (!local.pendingSync || (local.isDeleted && id in confirmedDeletes))) {
                    deleteById(id, userId)
                }
            }
        }
    }
}
