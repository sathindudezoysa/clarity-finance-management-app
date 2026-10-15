package com.example.clarity.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.checkWalletBalance
import com.example.clarity.data.local.entity.*
import com.example.clarity.data.mapper.*
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.CashWalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class CashWalletRepositoryImpl @Inject constructor(
    private val database: ClarityDatabase,
    private val auth: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.systemUTC()
) : CashWalletRepository {
    private val dao get() = database.cashWalletDao

    override fun observeWallet(): Flow<CashWallet> = auth.currentUser.distinctUntilChangedBy { it?.uid }
        .flatMapLatest { user ->
            if (user == null) flowOf(CashWallet())
            else dao.observeChanges(user.uid).conflate().mapLatest {
                // Cancellation discards obsolete reads; this transaction makes each read consistent.
                database.withTransaction {
                    CashWallet(
                        balanceMinor = dao.balance(user.uid).value(),
                        accounts = dao.accountTotals(user.uid).map { it.toWalletAccount() },
                        activity = dao.recentActivity(user.uid).map { it.toWalletActivity() },
                        topUpOption = dao.option(user.uid)?.toWalletOption(),
                        topUpHistory = dao.topUps(user.uid).map {
                            WalletActivity("transfer:${it.id}", if (it.automatic) "Automatic top-up" else "Bank to cash wallet", it.amountMinor, true,
                                it.dateEpochMillis, kind = "TRANSFER", bankAccountId = it.bankAccountId)
                        }, ownerId = user.uid
                    )
                }
            }.distinctUntilChanged()
        }.flowOn(ioDispatcher)

    override suspend fun addBankAccount(name: String, openingBalanceMinor: Long): Result<Unit> =
        saveBankAccount(WalletBankAccountInput(UUID.randomUUID().toString(), "other", name, openingBalanceMinor = openingBalanceMinor))

    override suspend fun saveBankAccount(input: WalletBankAccountInput) = write { uid ->
        walletRequire(input.id.isNotBlank() && input.id.length <= 128, "Choose a valid account.")
        walletRequire(BankCatalog.find(input.bankId) != null, "Choose a bank to continue.")
        walletRequire(input.nickname.trim().isNotBlank() && input.nickname.trim().length <= 100, "Enter an account nickname of up to 100 characters.")
        walletRequire(input.lastFour.isEmpty() || input.lastFour.matches(Regex("[0-9]{4}")), "Enter only the last four digits, or leave them blank.")
        walletRequire(input.accountType in listOf("SAVINGS", "CURRENT", "OTHER"), "Choose an account type.")
        walletRequire(input.currency == "LKR", "Cash wallet accounts currently use LKR.")
        walletRequire(input.openingBalanceMinor >= 0, "Opening balance cannot be negative.")
        val existing = dao.account(uid, input.id)
        walletRequire(existing?.isActive != false, "This account is archived.")
        val default = input.isDefault || (existing == null && dao.accounts(uid).none { it.isActive })
        if (default) dao.clearDefaults(uid)
        if (existing == null) dao.insertAccount(WalletBankAccountEntity(uid, input.id, input.nickname.trim(), input.openingBalanceMinor,
            input.bankId, input.lastFour, input.accountType, input.currency, default, needsBankReview = false))
        else {
            walletRequire(input.openingBalanceMinor == existing.openingBalanceMinor, "Opening balances cannot be edited after registration.")
            dao.updateAccount(uid, input.id, input.nickname.trim(), input.bankId, input.lastFour, input.accountType, default)
        }
    }

    override suspend fun archiveBankAccount(id: String) = write { uid ->
        walletRequire(dao.account(uid, id) != null, "Choose one of your bank accounts.")
        dao.archiveAccount(uid, id)
        dao.disableReminderForAccount(uid, id)
    }

    override suspend fun configureTopUp(option: WalletTopUpOption) = write { uid ->
        if (option.enabled) {
            walletRequire(dao.account(uid, option.bankAccountId)?.isActive == true, "Choose an active bank account to continue.")
            walletRequire(option.thresholdMinor >= 0 && option.amountMinor > 0, "Enter a minimum balance and a suggested top-up greater than zero.")
        }
        dao.saveOption(WalletTopUpOptionEntity(uid, option.bankAccountId, option.enabled, option.thresholdMinor, option.amountMinor, option.automaticExecution))
    }

    override suspend fun topUp(bankAccountId: String, amountMinor: Long, operationId: String) = write { uid ->
        walletRequire(operationId.isNotBlank() && operationId.length <= 128, "Please review the top-up again.")
        walletRequire(amountMinor > 0, "Please enter an amount greater than zero.")
        val previous = dao.topUpById(uid, operationId)
        if (previous != null) {
            walletRequire(previous.bankAccountId == bankAccountId && previous.amountMinor == amountMinor, "This transfer reference has already been used. Review a new top-up.")
        } else {
            val bank = dao.account(uid, bankAccountId)
            walletRequire(bank?.isActive == true, "Choose an active bank account to continue.")
            val available = BigInteger.valueOf(requireNotNull(bank).openingBalanceMinor) - dao.withdrawn(uid, bankAccountId).value()
            walletRequire(available >= BigInteger.valueOf(amountMinor), "Your bank balance is not enough for this top-up.")
            dao.insertTopUp(WalletTopUpEntity(uid, operationId, bankAccountId, amountMinor, clock.millis(), false))
            checkWalletBalance(dao, uid)
        }
    }

    override suspend fun adjustCashBalance(targetMinor: Long, note: String, operationId: String) = write { uid ->
        walletRequire(targetMinor >= 0, "Cash balance cannot be negative.")
        walletRequire(note.trim().length <= 2000, "Keep the adjustment note under 2,000 characters.")
        walletRequire(operationId.isNotBlank() && operationId.length <= 128, "Please start the balance adjustment again.")
        val previous = dao.adjustmentById(uid, operationId)
        if (previous != null) walletRequire(previous.targetBalanceMinor == targetMinor && previous.note == note.trim(), "This adjustment reference has already been used.")
        else {
            val change = BigInteger.valueOf(targetMinor) - dao.balance(uid).value()
            walletRequire(change.abs() <= BigInteger.valueOf(Long.MAX_VALUE), "This adjustment is too large.")
            dao.insertAdjustment(WalletAdjustmentEntity(uid, operationId, change.toLong(), targetMinor, note.trim(), clock.millis()))
            checkWalletBalance(dao, uid)
        }
    }

    override suspend fun recordCash(amountMinor: Long, type: TransactionType, category: String, note: String) = write { uid ->
        walletRequire(amountMinor > 0, "Please enter an amount greater than zero.")
        val normalizedCategory = category.trim().ifBlank { "General" }
        walletRequire(normalizedCategory.length <= 100 && note.trim().length <= 2000, "The category or note is too long.")
        val now = clock.millis()
        val transaction = Transaction(userId = uid, amountMinor = amountMinor, type = type, category = normalizedCategory,
            note = note.trim(), dateEpochMillis = now, createdAt = now, updatedAt = now)
        database.transactionDao.upsertWithTag(transaction.toEntity().copy(pendingSync = true))
        dao.insertCashLink(WalletCashLinkEntity(uid, transaction.id))
        // The saved automatic-execution opt-in authorizes one recorded withdrawal per cash expense.
        // Expense, withdrawal, wallet credit, and pending-sync flag commit or roll back together.
        val option = dao.option(uid)?.toWalletOption()
        if (type == TransactionType.EXPENSE && option?.enabled == true && option.automaticExecution) {
            val balanceAfterExpense = dao.balance(uid).value()
            if (CashWalletRules.needsAutomaticTopUp(balanceAfterExpense, option)) {
                val bank = dao.account(uid, option.bankAccountId)
                val available = bank?.let { BigInteger.valueOf(it.openingBalanceMinor) - dao.withdrawn(uid, it.id).value() }
                val amount = CashWalletRules.automaticTopUpAmount(balanceAfterExpense, option, available, bank?.isActive == true)
                dao.insertTopUp(WalletTopUpEntity(uid, "auto:${transaction.id}", requireNotNull(bank).id, requireNotNull(amount), now, true))
            }
        }
        checkWalletBalance(dao, uid)
    }

    override suspend fun bankActivity(id: String): Result<List<WalletActivity>> = withContext(ioDispatcher) {
        try {
            val uid = auth.currentUser.first()?.uid ?: throw WalletValidationException("Please sign in again.")
            Result.success(database.withTransaction {
                walletRequire(dao.account(uid, id) != null, "Choose one of your bank accounts.")
                dao.bankActivity(uid, id).map { it.toWalletActivity() }
            })
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(safeFailure(error))
        }
    }

    private suspend fun write(block: suspend (String) -> Unit): Result<Unit> = withContext(ioDispatcher) {
        try {
            val uid = auth.currentUser.first()?.uid ?: throw WalletValidationException("Please sign in again.")
            database.withTransaction {
                walletRequire(auth.currentUser.first()?.uid == uid, "Your session changed. Please try again.")
                block(uid)
                walletRequire(auth.currentUser.first()?.uid == uid, "Your session changed. Please try again.")
            }
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(safeFailure(error))
        }
    }

    private fun safeFailure(error: Exception): Exception {
        if (error is WalletValidationException) return error
        Log.w("CashWallet", "Wallet operation failed", error)
        return WalletValidationException("We couldn't save your changes. Please try again.")
    }
}
