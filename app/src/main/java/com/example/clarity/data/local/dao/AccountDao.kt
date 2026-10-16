package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AccountDao {
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY createdAt, id")
    abstract fun observeAll(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY createdAt, id")
    abstract suspend fun getAllForUser(userId: String): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    abstract suspend fun getById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE userId = :userId AND isDefault = 1 LIMIT 1")
    abstract suspend fun getDefault(userId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id AND userId = :userId")
    abstract suspend fun deleteById(id: String, userId: String)

    // Include pending deletes and every owner: any stored reference must remain valid.
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE accountId = :accountId)")
    abstract suspend fun hasTransactionReferences(accountId: String): Boolean
}
