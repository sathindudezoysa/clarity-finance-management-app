package com.example.clarity.data.repository

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.entity.*
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.mapper.walletSnapshot
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.CashWalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class CashWalletRepositoryImpl @Inject constructor(
    private val database: ClarityDatabase,
    private val auth: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher
) : CashWalletRepository {
    private val dao get() = database.cashWalletDao

    override fun observeWallet(): Flow<CashWallet> = auth.currentUser.distinctUntilChangedBy { it?.uid }
        .flatMapLatest { user ->
            if (user == null) flowOf(CashWallet())
            else dao.observeChanges(user.uid).map {
                database.withTransaction {
                    walletSnapshot(dao.accounts(user.uid), dao.topUps(user.uid), dao.cash(user.uid), dao.option(user.uid))
                }
            }
        }.flowOn(ioDispatcher)

    override suspend fun addBankAccount(name: String, openingBalanceMinor: Long) = write { uid ->
        require(name.trim().isNotBlank() && name.trim().length <= 100) { "Enter a bank account name (up to 100 characters)" }
        require(openingBalanceMinor >= 0) { "Opening balance cannot be negative" }
        dao.insertAccount(WalletBankAccountEntity(uid, UUID.randomUUID().toString(), name.trim(), openingBalanceMinor))
    }

    override suspend fun configureTopUp(option: WalletTopUpOption) = write { uid ->
        require(dao.accounts(uid).any { it.id == option.bankAccountId }) { "Select a bank account" }
        require(option.thresholdMinor >= 0 && option.amountMinor > 0) { "Enter a non-negative threshold and a positive top-up amount" }
        dao.saveOption(WalletTopUpOptionEntity(uid, option.bankAccountId, option.enabled, option.thresholdMinor, option.amountMinor))
    }

    override suspend fun topUp(bankAccountId: String, amountMinor: Long) = write { uid ->
        transfer(uid, bankAccountId, amountMinor, automatic = false)
    }

    override suspend fun recordCash(amountMinor: Long, type: TransactionType, category: String, note: String) = write { uid ->
        require(amountMinor > 0) { "Amount must be greater than zero" }
        val normalizedCategory = category.trim().ifBlank { "General" }
        require(normalizedCategory.length <= 100 && note.trim().length <= 2000) { "Category or note is too long" }
        val snapshot = walletSnapshot(dao.accounts(uid), dao.topUps(uid), dao.cash(uid), dao.option(uid))
        if (type == TransactionType.EXPENSE) {
            val automaticAmount = CashWalletRules.topUpNeeded(snapshot.balanceMinor, amountMinor, snapshot.topUpOption)
            if (automaticAmount > 0) transfer(uid, requireNotNull(snapshot.topUpOption).bankAccountId, automaticAmount, automatic = true)
            require(snapshot.balanceMinor + BigInteger.valueOf(automaticAmount) >= BigInteger.valueOf(amountMinor)) {
                "Insufficient cash. Top up the wallet first."
            }
        }
        val transaction = Transaction(userId = uid, amountMinor = amountMinor, type = type, category = normalizedCategory, note = note.trim())
        // Insert into the existing durable outbox without changing its CRUD or synchronization logic.
        database.transactionDao.upsertWithTag(transaction.toEntity().copy(pendingSync = true))
        dao.insertCashLink(WalletCashLinkEntity(uid, transaction.id))
    }

    private suspend fun transfer(uid: String, bankId: String, amount: Long, automatic: Boolean) {
        require(amount > 0) { "Amount must be greater than zero" }
        val bank = walletSnapshot(dao.accounts(uid), dao.topUps(uid), emptyList(), null)
            .accounts.firstOrNull { it.id == bankId } ?: error("Select a bank account")
        require(bank.balanceMinor >= BigInteger.valueOf(amount)) { "Insufficient bank balance" }
        dao.insertTopUp(WalletTopUpEntity(uid, UUID.randomUUID().toString(), bankId, amount, System.currentTimeMillis(), automatic))
    }

    private suspend fun write(block: suspend (String) -> Unit): Result<Unit> = withContext(ioDispatcher) {
        try {
            val uid = auth.currentUser.first()?.uid ?: error("Please sign in")
            database.withTransaction { block(uid) }
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
}
