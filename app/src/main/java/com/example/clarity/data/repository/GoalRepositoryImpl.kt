package com.example.clarity.data.repository

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.sync.GoalSyncProcessor
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.GoalsSnapshot
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.util.GoalPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val database: ClarityDatabase,
    private val auth: AuthRepository,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository,
    private val processor: GoalSyncProcessor,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock
) : GoalRepository {

    private val goalDao get() = database.goalDao

    override fun observeGoals(): Flow<GoalsSnapshot> =
        auth.currentUser.distinctUntilChangedBy { it?.uid }.flatMapLatest { user ->
            if (user == null) {
                flowOf(GoalsSnapshot(ownerId = null, goals = emptyList()))
            } else {
                goalDao.observe(user.uid).map { rows ->
                    GoalsSnapshot(
                        ownerId = user.uid,
                        goals = rows.map { it.toDomain() }
                    )
                }
            }
        }.flowOn(ioDispatcher)

    override suspend fun getGoal(id: String): Result<FinancialGoal?> = operation {
        val uid = owner()
        if (!GoalPolicy.validId(id)) throw GoalException(GoalError.INVALID_ID)
        goalDao.get(uid, id)?.toDomain()
    }

    override suspend fun create(goal: FinancialGoal): Result<FinancialGoal> = operation {
        val uid = owner()
        if (goal.userId.isNotEmpty() && goal.userId != uid) {
            throw GoalException(GoalError.SIGN_IN_REQUIRED)
        }
        val recordedCurrency = goal.currency.trim().ifEmpty {
            currencyPreferenceRepository.current()
        }
        database.withTransaction {
            checkOwner(uid)
            val existing = goalDao.get(uid, goal.id)
            if (existing != null) {
                return@withTransaction existing.toDomain()
            }
            val today = GoalPolicy.todayEpochDay(clock)
            val validated = GoalPolicy.validateForCreate(
                goal.copy(userId = uid, currency = recordedCurrency),
                today
            )
            val now = maxOf(clock.millis(), 1L)
            val created = validated.copy(
                userId = uid,
                createdAt = now,
                updatedAt = now,
                pendingSync = true
            )
            goalDao.upsert(created.toEntity())
            created
        }
    }

    override suspend fun edit(goal: FinancialGoal): Result<FinancialGoal> = operation {
        val uid = owner()
        if (goal.userId.isNotEmpty() && goal.userId != uid) {
            throw GoalException(GoalError.SIGN_IN_REQUIRED)
        }
        database.withTransaction {
            checkOwner(uid)
            val existingEntity = goalDao.get(uid, goal.id)
                ?: throw GoalException(GoalError.NOT_FOUND)
            val existing = existingEntity.toDomain()
            val today = GoalPolicy.todayEpochDay(clock)
            val validated = GoalPolicy.validateForEdit(existing, goal, today)
            val now = maxOf(clock.millis(), Math.addExact(existing.updatedAt, 1L))
            val updated = validated.copy(
                updatedAt = now,
                pendingSync = true
            )
            goalDao.upsert(updated.toEntity())
            updated
        }
    }

    override suspend fun retrySync(): Result<Unit> = operation {
        val uid = owner()
        processor.upload(uid)
    }

    private suspend fun owner(): String =
        auth.currentUser.first()?.uid ?: throw GoalException(GoalError.SIGN_IN_REQUIRED)

    private suspend fun checkOwner(uid: String) {
        if (auth.currentUser.first()?.uid != uid) {
            throw GoalException(GoalError.SIGN_IN_REQUIRED)
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
