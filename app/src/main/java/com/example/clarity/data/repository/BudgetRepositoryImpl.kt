package com.example.clarity.data.repository

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.entity.BudgetResolutionEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.sync.BudgetSignals
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val db: ClarityDatabase, private val auth: AuthRepository,
    private val rates: ExchangeRateRepository, private val clock: Clock,
    private val signals: BudgetSignals, private val io: CoroutineDispatcher
) : BudgetRepository {
    override fun observe(): Flow<BudgetOverview> = auth.currentUser.distinctUntilChangedBy { it?.uid }.flatMapLatest { user ->
        if (user == null) flowOf(BudgetOverview()) else combine(
            db.budgetDao.observe(user.uid), db.transactionDao.observeAll(user.uid),
            db.budgetDao.resolutions(user.uid), signals.errors, signals.refresh
        ) { budgets, transactions, resolutions, errors, _ ->
            val entries = transactions.filter { !it.isDeleted }.map { it.toDomain() }
            val resolved = resolutions.map { BudgetResolution(it.transactionId, it.transactionVersion, it.currency, it.amountMinor) }
            val zone = ZoneId.systemDefault()
            val visibleBudgets = budgets.filter { !it.isDeleted && it.active }
            BudgetOverview(user.uid, visibleBudgets.map { BudgetPolicy.evaluate(it.toDomain(), entries, resolved, zone, clock) }, user.uid in errors)
        }.onStart { emit(BudgetOverview(user.uid, loading = true)) }
    }.flowOn(io)

    override suspend fun save(budget: Budget): Result<Unit> = operation {
        val uid = owner()
        require(budget.userId == uid)
        val normalized = budget.copy(category = TransactionTags.normalize(budget.category))
        BudgetPolicy.validate(normalized)
        db.withTransaction {
            val old = db.budgetDao.get(uid, normalized.id)
            val activeExisting = old?.takeIf { !it.isDeleted }
            // Identity and the recorded currency survive edits to an existing non-deleted budget.
            require(
                activeExisting == null || (
                    activeExisting.month == normalized.month &&
                        activeExisting.categoryKey == BudgetPolicy.categoryKey(normalized.category) &&
                        activeExisting.currency == normalized.currency
                    )
            )
            checkOwner(uid)
            val now = maxOf(clock.millis(), old?.updatedAt?.let { Math.addExact(it, 1) } ?: 0)
            db.budgetDao.upsert(
                normalized.copy(
                    currency = activeExisting?.currency ?: normalized.currency,
                    createdAt = activeExisting?.createdAt ?: now,
                    updatedAt = now,
                    pendingSync = true
                ).toEntity()
            )
        }
    }

    override suspend fun delete(id: String): Result<Unit> = operation {
        val uid = owner()
        db.withTransaction {
            val row = requireNotNull(db.budgetDao.get(uid, id))
            require(!row.isDeleted) { "Budget already deleted" }
            checkOwner(uid)
            db.budgetDao.upsert(
                row.copy(
                    active = false,
                    isDeleted = true,
                    updatedAt = maxOf(clock.millis(), Math.addExact(row.updatedAt, 1)),
                    pendingSync = true
                )
            )
        }
    }

    override suspend fun disable(id: String): Result<Unit> = delete(id)
    override suspend fun resolveUncounted(id: String): Result<Unit> = operation {
        val uid = owner()
        val budget = requireNotNull(db.budgetDao.get(uid, id)).toDomain()
        val entries = db.transactionDao.observeAll(uid).first()
        val zone = ZoneId.systemDefault()
        var incomplete = false
        for (row in entries) {
            val transaction = row.toDomain()
            if (!BudgetPolicy.eligible(budget, transaction, zone, clock) || transaction.currency == budget.currency ||
                (!transaction.pendingConversion && transaction.targetCurrency == budget.currency)) continue
            try {
                checkOwner(uid)
                val quote = rates.latest(transaction.currency, budget.currency)
                val amount = CurrencyConversion.snapshot(transaction.amountMinor, quote).amountMinor
                db.withTransaction {
                    checkOwner(uid)
                    if (db.transactionDao.getById(row.id) == row) db.budgetDao.resolve(
                        BudgetResolutionEntity(uid, row.id, row.updatedAt, budget.currency, amount))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                incomplete = true
            }
        }
        check(!incomplete) { "Some conversions remain unavailable" }
    }
    private suspend fun owner() = requireNotNull(auth.currentUser.first()?.uid) { "Sign in required" }
    private suspend fun checkOwner(uid: String) { check(owner() == uid) { "Session changed" } }
    private suspend fun <T> operation(block: suspend () -> T): Result<T> = withContext(io) {
        try { Result.success(block()) } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
}
