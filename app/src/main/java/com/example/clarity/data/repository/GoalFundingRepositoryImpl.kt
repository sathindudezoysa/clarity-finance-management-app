package com.example.clarity.data.repository

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.sync.AccountUploader
import com.example.clarity.data.sync.GoalContributionSyncProcessor
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.repository.GoalFundingRepository
import com.example.clarity.domain.util.GoalFundingPolicy
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.GoalProgressPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalFundingRepositoryImpl @Inject internal constructor(
    private val database: ClarityDatabase,
    private val auth: AuthRepository,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository,
    private val rates: ExchangeRateRepository,
    private val accountUploader: AccountUploader,
    private val contributionProcessor: GoalContributionSyncProcessor,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock
) : GoalFundingRepository {

    private val goalDao get() = database.goalDao
    private val contributionDao get() = database.goalContributionDao
    private val accountDao get() = database.accountDao
    private val transactionDao get() = database.transactionDao

    override suspend fun contribute(
        goalId: String,
        amountMinor: Long,
        sourceAccountId: String,
        id: String
    ): Result<GoalContribution> = operation {
        val uid = owner()
        val preferredCurrency = currencyPreferenceRepository.current()
        val created = database.withTransaction {
            checkOwner(uid)
            if (!GoalPolicy.validId(id)) {
                throw GoalContributionException(GoalContributionError.INVALID_ID)
            }
            if (!GoalPolicy.validId(goalId)) {
                throw GoalContributionException(GoalContributionError.INVALID_GOAL_ID)
            }
            if (sourceAccountId.isBlank() || !GoalPolicy.validId(sourceAccountId)) {
                throw GoalContributionException(GoalContributionError.ACCOUNT_REQUIRED)
            }
            val existing = contributionDao.get(uid, id)
            if (existing != null) {
                return@withTransaction existing.toDomain()
            }
            val goal = goalDao.get(uid, goalId)?.toDomain()
                ?: throw GoalContributionException(GoalContributionError.GOAL_NOT_FOUND)
            if (goal.userId != uid) {
                throw GoalContributionException(GoalContributionError.SIGN_IN_REQUIRED)
            }

            val sourceAccount = accountDao.getById(sourceAccountId)
                ?.takeIf { it.userId == uid }
                ?.toDomain()
                ?.takeIf { !it.isGoalAccount }
                ?: throw GoalContributionException(GoalContributionError.ACCOUNT_NOT_FOUND)

            if (!goal.currency.equals(sourceAccount.currency, ignoreCase = true)) {
                throw GoalContributionException(GoalContributionError.CURRENCY_MISMATCH)
            }

            val userAccounts = accountDao.getAllForUser(uid).map { it.toDomain() }
            val activeTransactions = transactionDao.getActiveForUser(uid).map { it.toDomain() }
            val availability = GoalFundingPolicy.evaluate(
                transactions = activeTransactions,
                accounts = userAccounts,
                currency = goal.currency,
                selectedAccountId = sourceAccount.id
            )
            if (!availability.canAfford(amountMinor)) {
                throw GoalContributionException(GoalContributionError.INSUFFICIENT_FUNDS)
            }

            val now = maxOf(clock.millis(), 1L)
            val today = GoalPolicy.todayEpochDay(clock)
            val validatedContribution = GoalProgressPolicy.validateForCreate(
                GoalContribution(
                    id = id,
                    userId = uid,
                    goalId = goal.id,
                    amountMinor = amountMinor,
                    currency = goal.currency
                ),
                today
            ).copy(
                userId = uid,
                currency = goal.currency,
                dateEpochDay = today,
                createdAt = now,
                updatedAt = now,
                pendingSync = true
            )

            val goalAccountId = Account.goalAccountId(goal.id)
            if (accountDao.getById(goalAccountId) == null) {
                val hiddenGoalAccount = Account.goalAccount(
                    goalId = goal.id,
                    currency = goal.currency,
                    userId = uid,
                    createdAt = now,
                    updatedAt = now
                )
                accountDao.upsertAll(listOf(hiddenGoalAccount.toEntity().copy(pendingSync = true)))
            }

            val targetCurrency = preferredCurrency.ifBlank { goal.currency }
            val snapshot = if (goal.currency.equals(targetCurrency, ignoreCase = true)) {
                ConversionSnapshot.identity(amountMinor)
            } else {
                rates.cached(goal.currency, targetCurrency)?.let { quote ->
                    runCatching { CurrencyConversion.snapshot(amountMinor, quote) }.getOrNull()
                }
            }
            val txUpdatedAt = maxOf(now, snapshot?.fetchedAt ?: 0L)

            val expenseTransaction = Transaction(
                id = id,
                userId = uid,
                type = TransactionType.EXPENSE,
                amountMinor = amountMinor,
                category = TransactionTags.GOAL_CONTRIBUTION,
                note = "Goal: ${goal.name}",
                dateEpochMillis = now,
                createdAt = now,
                updatedAt = txUpdatedAt,
                accountId = sourceAccount.id,
                currency = goal.currency,
                targetCurrency = targetCurrency,
                conversion = snapshot,
                pendingConversion = snapshot == null
            ).also { CurrencyConversion.validate(it) }

            try {
                transactionDao.persistLocal(
                    expenseTransaction.toEntity().copy(
                        pendingSync = true,
                        isDeleted = false
                    )
                )
            } catch (error: TransactionException) {
                if (error.error == TransactionError.INSUFFICIENT_BALANCE) {
                    throw GoalContributionException(
                        GoalContributionError.INSUFFICIENT_FUNDS,
                        error.message
                    )
                }
                throw error
            }

            contributionDao.upsert(validatedContribution.toEntity())
            validatedContribution
        }

        applicationScope.launch {
            runCatching { accountUploader.uploadPending(uid) }
            runCatching { contributionProcessor.upload(uid) }
        }
        created
    }

    private suspend fun owner(): String =
        auth.currentUser.first()?.uid
            ?: throw GoalContributionException(GoalContributionError.SIGN_IN_REQUIRED)

    private suspend fun checkOwner(uid: String) {
        if (auth.currentUser.first()?.uid != uid) {
            throw GoalContributionException(GoalContributionError.SIGN_IN_REQUIRED)
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = withContext(ioDispatcher) {
        try {
            Result.success(block())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
}
