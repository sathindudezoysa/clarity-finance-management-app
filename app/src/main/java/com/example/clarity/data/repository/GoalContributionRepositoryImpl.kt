package com.example.clarity.data.repository

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.sync.GoalContributionSyncProcessor
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.GoalContributionsSnapshot
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.GoalContributionRepository
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.GoalProgressPolicy
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
class GoalContributionRepositoryImpl @Inject constructor(
    private val database: ClarityDatabase,
    private val auth: AuthRepository,
    private val processor: GoalContributionSyncProcessor,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock
) : GoalContributionRepository {

    private val goalDao get() = database.goalDao
    private val contributionDao get() = database.goalContributionDao

    override fun observeContributions(): Flow<GoalContributionsSnapshot> =
        auth.currentUser.distinctUntilChangedBy { it?.uid }.flatMapLatest { user ->
            if (user == null) {
                flowOf(GoalContributionsSnapshot(ownerId = null, contributions = emptyList()))
            } else {
                contributionDao.observe(user.uid).map { rows ->
                    GoalContributionsSnapshot(
                        ownerId = user.uid,
                        contributions = rows.map { it.toDomain() }
                    )
                }
            }
        }.flowOn(ioDispatcher)

    override suspend fun add(contribution: GoalContribution): Result<GoalContribution> = operation {
        val uid = owner()
        if (contribution.userId.isNotEmpty() && contribution.userId != uid) {
            throw GoalContributionException(GoalContributionError.SIGN_IN_REQUIRED)
        }
        database.withTransaction {
            checkOwner(uid)
            val existing = contributionDao.get(uid, contribution.id)
            if (existing != null) {
                return@withTransaction existing.toDomain()
            }
            if (!GoalPolicy.validId(contribution.goalId)) {
                throw GoalContributionException(GoalContributionError.INVALID_GOAL_ID)
            }
            val goal = goalDao.get(uid, contribution.goalId)?.toDomain()
                ?: throw GoalContributionException(GoalContributionError.GOAL_NOT_FOUND)
            val today = GoalPolicy.todayEpochDay(clock)
            val validated = GoalProgressPolicy.validateForCreate(
                contribution.copy(
                    userId = uid,
                    goalId = goal.id,
                    currency = goal.currency
                ),
                today
            )
            val now = maxOf(clock.millis(), 1L)
            val created = validated.copy(
                userId = uid,
                currency = goal.currency,
                dateEpochDay = today,
                createdAt = now,
                updatedAt = now,
                pendingSync = true
            )
            contributionDao.upsert(created.toEntity())
            created
        }
    }

    override suspend fun retrySync(): Result<Unit> = operation {
        val uid = owner()
        processor.upload(uid)
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

