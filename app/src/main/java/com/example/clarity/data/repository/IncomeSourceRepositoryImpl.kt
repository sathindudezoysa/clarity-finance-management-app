package com.example.clarity.data.repository

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.domain.model.IncomeError
import com.example.clarity.domain.model.IncomeException
import com.example.clarity.domain.model.IncomeSources
import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.IncomeSourceRepository
import com.example.clarity.domain.util.IncomePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class IncomeSourceRepositoryImpl @Inject constructor(
    private val database: ClarityDatabase, private val auth: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher, private val clock: Clock
) : IncomeSourceRepository {
    private val dao get() = database.incomeSourceDao
    override fun observeSources(): Flow<IncomeSources> = auth.currentUser.distinctUntilChangedBy { it?.uid }
        .flatMapLatest { user -> if (user == null) flowOf(IncomeSources(null, emptyList())) else dao.observe(user.uid).map { rows -> IncomeSources(user.uid, rows.map { it.toDomain() }) } }
        .flowOn(ioDispatcher)
    override suspend fun save(source: IncomeSource) = operation {
        val uid = owner()
        IncomePolicy.validate(source)
        database.withTransaction {
            requireAccount(uid, source.accountId)
            val old = dao.get(uid, source.id)
            if (source.userId.isNotEmpty() && source.userId != uid) throw IncomeException(IncomeError.SIGN_IN)
            val now = maxOf(clock.millis(), old?.updatedAt?.let { Math.addExact(it, 1) } ?: 0)
            checkOwner(uid)
            dao.upsert(source.copy(userId = uid, name = source.name.trim(), createdAt = old?.createdAt ?: now,
                updatedAt = now, lastConfirmedMonth = old?.lastConfirmedMonth, pendingSync = true).toEntity())
        }
    }
    override suspend fun archive(id: String) = operation {
        val uid = owner()
        database.withTransaction {
            val row = dao.get(uid, id) ?: throw IncomeException(IncomeError.NOT_FOUND)
            checkOwner(uid)
            dao.upsert(row.copy(active = false, updatedAt = maxOf(clock.millis(), Math.addExact(row.updatedAt, 1)), pendingSync = true))
        }
    }
    override suspend fun record(id: String, month: String?, expectedVersion: Long): Result<String> = operation {
        val uid = owner()
        database.withTransaction {
            val row = dao.get(uid, id) ?: throw IncomeException(IncomeError.NOT_FOUND)
            if (row.updatedAt != expectedVersion) throw IncomeException(IncomeError.SOURCE_CHANGED)
            if (!row.active) throw IncomeException(IncomeError.NOT_FOUND)
            requireAccount(uid, row.accountId)
            val now = clock.millis()
            val current = YearMonth.from(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))
            if (row.recurring && month != current.toString()) throw IncomeException(IncomeError.PERIOD_CHANGED)
            if (!row.recurring && month != null) throw IncomeException(IncomeError.PERIOD_CHANGED)
            if (row.recurring && !row.toDomain().isDue(current)) throw IncomeException(IncomeError.ALREADY_CONFIRMED)
            val transactionId = if (row.recurring) IncomePolicy.transactionId(uid, id, current) else UUID.randomUUID().toString()
            val existing = database.transactionDao.getById(transactionId)
            checkOwner(uid)
            if (existing == null) {
                // The existing eager transaction outbox observes this commit and queues Firestore immediately.
                database.transactionDao.upsertWithTag(TransactionEntity(transactionId, uid, "INCOME", row.amountMinor,
                    "Income", row.name, now, now, now, pendingSync = true, accountId = row.accountId))
            } else require(existing.userId == uid && existing.type == "INCOME")
            if (row.recurring) dao.upsert(row.copy(lastConfirmedMonth = month, updatedAt = maxOf(now, Math.addExact(row.updatedAt, 1)), pendingSync = true))
            transactionId
        }
    }
    private suspend fun owner() = auth.currentUser.first()?.uid ?: throw IncomeException(IncomeError.SIGN_IN)
    private suspend fun checkOwner(uid: String) { if (auth.currentUser.first()?.uid != uid) throw IncomeException(IncomeError.SIGN_IN) }
    private suspend fun requireAccount(uid: String, id: String) {
        if (database.accountDao.getById(id)?.let { it.userId == uid && it.currency == "LKR" } != true) throw IncomeException(IncomeError.ACCOUNT)
    }
    private suspend fun <T> operation(block: suspend () -> T): Result<T> = withContext(ioDispatcher) {
        try { Result.success(block()) } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
}
