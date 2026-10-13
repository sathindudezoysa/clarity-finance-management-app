package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CashWalletDao {
    // Observe invalidation across every source, then read one consistent snapshot in a transaction.
    @Query("SELECT (SELECT COUNT(*) FROM wallet_bank_accounts WHERE userId = :uid) + (SELECT COUNT(*) FROM wallet_top_ups WHERE userId = :uid) + (SELECT COUNT(*) FROM wallet_top_up_options WHERE userId = :uid) + (SELECT COUNT(*) FROM wallet_cash_links WHERE userId = :uid) + (SELECT COUNT(*) FROM transactions WHERE userId = :uid)")
    fun observeChanges(uid: String): Flow<Long>

    @Query("SELECT * FROM wallet_bank_accounts WHERE userId = :uid ORDER BY name")
    suspend fun accounts(uid: String): List<WalletBankAccountEntity>
    @Query("SELECT * FROM wallet_top_ups WHERE userId = :uid")
    suspend fun topUps(uid: String): List<WalletTopUpEntity>
    @Query("SELECT * FROM wallet_top_up_options WHERE userId = :uid")
    suspend fun option(uid: String): WalletTopUpOptionEntity?
    @Query("SELECT t.* FROM transactions t INNER JOIN wallet_cash_links w ON t.id = w.transactionId AND t.userId = w.userId WHERE t.userId = :uid AND t.isDeleted = 0")
    suspend fun cash(uid: String): List<TransactionEntity>

    @Insert suspend fun insertAccount(account: WalletBankAccountEntity)
    @Insert suspend fun insertTopUp(topUp: WalletTopUpEntity)
    @Insert suspend fun insertCashLink(link: WalletCashLinkEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOption(option: WalletTopUpOptionEntity)
}
