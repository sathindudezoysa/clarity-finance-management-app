package com.example.clarity.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.clarity.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import com.example.clarity.data.local.WalletMoneyParts
import com.example.clarity.data.local.WalletBankTotals
import com.example.clarity.data.local.WalletActivityRow

@Dao
interface CashWalletDao : WalletBalanceReader {
    @Query(WALLET_SNAPSHOT_SQL)
    fun observeSnapshotRows(uid: String): Flow<List<com.example.clarity.data.local.WalletSnapshotRow>>

    // A cheap invalidation signal, not a version counter. Do not distinctUntilChanged this signal.
    @Query("SELECT EXISTS(SELECT 1 FROM wallet_bank_accounts WHERE userId = :uid) + EXISTS(SELECT 1 FROM wallet_top_ups WHERE userId = :uid) + EXISTS(SELECT 1 FROM wallet_top_up_options WHERE userId = :uid) + EXISTS(SELECT 1 FROM wallet_cash_links WHERE userId = :uid) + EXISTS(SELECT 1 FROM transactions WHERE userId = :uid) + EXISTS(SELECT 1 FROM wallet_adjustments WHERE userId = :uid)")
    fun observeChanges(uid: String): Flow<Long>

    @Query("SELECT * FROM wallet_bank_accounts WHERE userId = :uid ORDER BY name")
    suspend fun accounts(uid: String): List<WalletBankAccountEntity>
    @Query("SELECT * FROM wallet_top_ups WHERE userId = :uid ORDER BY dateEpochMillis DESC, id DESC LIMIT 50")
    suspend fun topUps(uid: String): List<WalletTopUpEntity>
    @Query("SELECT * FROM wallet_top_up_options WHERE userId = :uid")
    suspend fun option(uid: String): WalletTopUpOptionEntity?
    @Query("SELECT t.* FROM transactions t INNER JOIN wallet_cash_links w ON t.id = w.transactionId AND t.userId = w.userId WHERE t.userId = :uid AND t.isDeleted = 0")
    suspend fun cash(uid: String): List<TransactionEntity>

    @Query("SELECT COALESCE(SUM(amountMinor / 1000000000),0) AS high, COALESCE(SUM(amountMinor % 1000000000),0) AS low FROM wallet_top_ups WHERE userId = :uid AND bankAccountId = :bankId")
    suspend fun withdrawn(uid: String, bankId: String): WalletMoneyParts

    @Query(WALLET_ACCOUNT_TOTALS_SQL)
    suspend fun accountTotals(uid: String): List<WalletBankTotals>

    @Query("SELECT * FROM wallet_bank_accounts WHERE userId = :uid AND id = :id")
    suspend fun account(uid: String, id: String): WalletBankAccountEntity?

    @Query("SELECT * FROM wallet_top_ups WHERE userId = :uid AND id = :id")
    suspend fun topUpById(uid: String, id: String): WalletTopUpEntity?

    @Query("SELECT * FROM wallet_adjustments WHERE userId = :uid AND id = :id")
    suspend fun adjustmentById(uid: String, id: String): WalletAdjustmentEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM wallet_cash_links WHERE userId = :uid AND transactionId = :id)")
    suspend fun isCashLink(uid: String, id: String): Boolean

    @Query(WALLET_RECENT_ACTIVITY_SQL)
    suspend fun recentActivity(uid: String): List<WalletActivityRow>

    @Query("SELECT 'transfer:' || id AS id, 'Withdrawal to cash wallet' AS title, -amountMinor AS amountMinor, dateEpochMillis, 'TRANSFER' AS kind, NULL AS transactionId, bankAccountId FROM wallet_top_ups WHERE userId = :uid AND bankAccountId = :id ORDER BY dateEpochMillis DESC, id DESC LIMIT 50")
    suspend fun bankActivity(uid: String, id: String): List<WalletActivityRow>

    @Query("UPDATE wallet_bank_accounts SET isDefault = 0 WHERE userId = :uid")
    suspend fun clearDefaults(uid: String)

    @Query("UPDATE wallet_bank_accounts SET name = :name, bankId = :bankId, lastFour = :lastFour, accountType = :type, isDefault = :isDefault, needsBankReview = 0 WHERE userId = :uid AND id = :id")
    suspend fun updateAccount(uid: String, id: String, name: String, bankId: String, lastFour: String, type: String, isDefault: Boolean)

    @Query("UPDATE wallet_bank_accounts SET isActive = 0, isDefault = 0 WHERE userId = :uid AND id = :id")
    suspend fun archiveAccount(uid: String, id: String)

    @Query("UPDATE wallet_top_up_options SET enabled = 0 WHERE userId = :uid AND bankAccountId = :id")
    suspend fun disableReminderForAccount(uid: String, id: String)

    @Insert suspend fun insertAccount(account: WalletBankAccountEntity)
    @Insert suspend fun insertTopUp(topUp: WalletTopUpEntity)
    @Insert suspend fun insertCashLink(link: WalletCashLinkEntity)
    @Insert suspend fun insertAdjustment(adjustment: WalletAdjustmentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOption(option: WalletTopUpOptionEntity)
}
