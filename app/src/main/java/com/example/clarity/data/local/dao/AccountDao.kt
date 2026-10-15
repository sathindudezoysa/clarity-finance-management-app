package com.example.clarity.data.local.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    protected open fun warnRetainedAccount(id: String) {
        Log.w("AccountDao", "Keeping account $id missing remotely because local transactions reference it")
    }

    // Validation, first-account selection and both sides of a switch share one commit.
    @Transaction
    open suspend fun save(entity: AccountEntity, create: Boolean) {
        val existing = getById(entity.id)
        if (create) require(existing == null) { "Account already exists" }
        else require(existing != null && existing.userId == entity.userId) { "Account not found" }
        val accounts = getAllForUser(entity.userId)
        require(entity.name.isNotBlank()) { "Account name must not be blank" }
        require(accounts.none { it.id != entity.id && it.name.equals(entity.name, ignoreCase = true) }) {
            "An account with this name already exists"
        }
        require(existing?.isDefault != true || entity.isDefault) { "Select another default account first" }
        val latest = accounts.maxOfOrNull { it.updatedAt } ?: 0L
        require(latest < Long.MAX_VALUE) { "Account timestamp is out of range" }
        val now = maxOf(System.currentTimeMillis(), latest + 1)
        val saved = entity.copy(
            isDefault = accounts.isEmpty() || entity.isDefault,
            createdAt = existing?.createdAt ?: now, updatedAt = now, pendingSync = true
        )
        val previousDefaults = if (saved.isDefault) accounts.filter { it.isDefault && it.id != saved.id }
            .map { it.copy(isDefault = false, updatedAt = now, pendingSync = true) } else emptyList()
        upsertAll(previousDefaults + saved)
    }

    @Transaction
    open suspend fun setDefault(id: String, userId: String) {
        val existing = getById(id)
        require(existing != null && existing.userId == userId) { "Account not found" }
        if (!existing.isDefault) save(existing.copy(isDefault = true), create = false)
    }

    @Transaction
    open suspend fun acknowledgeBatch(entities: List<AccountEntity>) {
        // Never clear a newer local change made while the batch was uploading.
        val unchanged = entities.filter { getById(it.id) == it }
        upsertAll(unchanged.map { it.copy(pendingSync = false) })
    }

    @Transaction
    open suspend fun reconcileRemote(userId: String, remote: List<AccountEntity>, authoritative: Boolean) {
        val local = getAllForUser(userId)
        val pending = local.any { it.pendingSync }
        val merged = local.associateBy { it.id }.toMutableMap()
        remote.filter { it.userId == userId }.forEach { incoming ->
            val existing = getById(incoming.id)
            if (existing != null && existing.userId != userId) return@forEach
            // A pending default switch is an indivisible group. Cache cannot confirm it.
            if (existing?.pendingSync == true || (pending && !authoritative)) return@forEach
            if (existing != null && existing.updatedAt > incoming.updatedAt) {
                if (authoritative) merged[incoming.id] = existing.copy(pendingSync = true)
            } else {
                merged[incoming.id] = incoming.copy(
                    isDefault = if (pending) existing?.isDefault == true else incoming.isDefault,
                    pendingSync = pending && incoming.isDefault != (existing?.isDefault == true)
                )
            }
        }
        if (authoritative) {
            val remoteIds = remote.map { it.id }.toSet()
            local.filter { !it.pendingSync && it.id !in remoteIds }.forEach {
                if (hasTransactionReferences(it.id)) {
                    warnRetainedAccount(it.id)
                } else {
                    merged.remove(it.id)
                    deleteById(it.id, userId)
                }
            }
        }
        if (merged.isEmpty()) return
        // Normalize malformed or concurrent remote defaults before Room observers see them.
        val defaults = merged.values.filter { it.isDefault }
        val selected = defaults.maxWithOrNull(compareBy<AccountEntity> { it.updatedAt }.thenBy { it.id })
            ?: merged.values.minWith(compareBy<AccountEntity> { it.createdAt }.thenBy { it.id })
        val latest = merged.values.maxOf { it.updatedAt }
        require(latest < Long.MAX_VALUE) { "Account timestamp is out of range" }
        val now = maxOf(System.currentTimeMillis(), latest + 1)
        val normalized = merged.values.map { account ->
            val isDefault = account.id == selected.id
            if (account.isDefault == isDefault) account
            else account.copy(isDefault = isDefault, updatedAt = now, pendingSync = true)
        }
        // Reject invalid remote duplicate names atomically, keeping the previous local state.
        require(normalized.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == normalized.size) {
            "Remote account names are duplicated"
        }
        upsertAll(normalized)
    }
}
