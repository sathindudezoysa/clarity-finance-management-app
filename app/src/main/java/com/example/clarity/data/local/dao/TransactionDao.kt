package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.clarity.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId AND isDeleted = 0 ORDER BY dateEpochMillis DESC, createdAt DESC")
    abstract fun observeAll(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId AND isDeleted = 0")
    abstract fun observeById(id: String, userId: String): Flow<TransactionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<TransactionEntity>)

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
