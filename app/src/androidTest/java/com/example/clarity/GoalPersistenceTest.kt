package com.example.clarity

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.remote.GoalRemoteDataSource
import com.example.clarity.data.repository.GoalRepositoryImpl
import com.example.clarity.data.sync.GoalSyncProcessor
import com.example.clarity.di.DatabaseModule
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.CurrencyPreference
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.GoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GoalPersistenceTest {
    private val fixedInstant = Instant.parse("2026-10-19T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val todayEpochDay = LocalDate.ofInstant(fixedInstant, ZoneOffset.UTC).toEpochDay()
    private val futureDay1 = todayEpochDay + 30L
    private val futureDay2 = todayEpochDay + 60L

    @Test
    fun multiGoalLifecycle_edit_currencyImmutability_reopen_syncAndUserIsolation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "goals-${UUID.randomUUID()}"
        val auth = TestAuth()
        val currencyPrefs = FakeCurrencyPrefs("LKR")
        var db = DatabaseModule.createDatabase(context, name)
        val remote = FakeGoalRemote()

        fun processor() = GoalSyncProcessor(db, remote, auth)
        fun repository() = GoalRepositoryImpl(
            database = db,
            auth = auth,
            currencyPreferenceRepository = currencyPrefs,
            processor = processor(),
            ioDispatcher = Dispatchers.IO,
            clock = clock
        )
        suspend fun awaitSnapshot(repo: GoalRepository, owner: String? = "owner") =
            withTimeout(5_000) { repo.observeGoals().first { it.ownerId == owner } }

        try {
            var repo = repository()

            // 1. Create multiple goals offline; currency is recorded from current preference
            val goalA = repo.create(
                FinancialGoal(
                    id = "goal-a",
                    name = "Emergency Fund",
                    description = "Six months",
                    targetMinor = 100_000L,
                    currency = "",
                    targetDateEpochDay = futureDay2
                )
            ).getOrThrow()
            val goalB = repo.create(
                FinancialGoal(
                    id = "goal-b",
                    name = "Vacation",
                    description = "Trip",
                    targetMinor = 50_000L,
                    currency = "",
                    targetDateEpochDay = futureDay1
                )
            ).getOrThrow()

            assertEquals("LKR", goalA.currency)
            assertEquals("LKR", goalB.currency)
            assertTrue(goalA.pendingSync)
            assertTrue(goalB.pendingSync)

            // Ordered by targetDateEpochDay ASC, createdAt ASC, id ASC
            val initialSnapshot = withTimeout(5_000) {
                repo.observeGoals().first { it.ownerId == "owner" && it.goals.size == 2 }
            }
            assertEquals(listOf("goal-b", "goal-a"), initialSnapshot.goals.map { it.id })

            // 2. Updating one goal's details leaves the other goal untouched
            val editedA = repo.edit(
                goalA.copy(
                    name = "Emergency Reserve",
                    description = "Updated description",
                    targetMinor = 120_000L,
                    targetDateEpochDay = futureDay2 + 5L
                )
            ).getOrThrow()
            assertEquals("Emergency Reserve", editedA.name)
            assertEquals("Updated description", editedA.description)
            assertEquals(120_000L, editedA.targetMinor)
            assertEquals(futureDay2 + 5L, editedA.targetDateEpochDay)
            assertEquals("Vacation", repo.getGoal("goal-b").getOrThrow()?.name)

            // Changing currency preference later does not alter existing goals
            currencyPrefs.code = "USD"
            val goalC = repo.create(
                FinancialGoal(
                    id = "goal-c",
                    name = "Conference Trip",
                    description = "",
                    targetMinor = 2_000_00L,
                    currency = "",
                    targetDateEpochDay = futureDay2 + 10L
                )
            ).getOrThrow()
            assertEquals("USD", goalC.currency)
            assertEquals("LKR", repo.getGoal("goal-a").getOrThrow()?.currency)

            // Reject invalid edit with non-positive target
            val invalidEdit = repo.edit(editedA.copy(targetMinor = 0L))
            assertTrue(invalidEdit.isFailure)
            val invalidEx = invalidEdit.exceptionOrNull() as? GoalException
            assertNotNull(invalidEx)
            assertEquals(GoalError.TARGET_NON_POSITIVE, invalidEx?.error)

            // 3. Reopen Room database and verify persistence of goals and pendingSync flags
            db.close()
            db = DatabaseModule.createDatabase(context, name)
            repo = repository()
            val afterReopen = withTimeout(5_000) {
                repo.observeGoals().first { it.ownerId == "owner" && it.goals.size == 3 }
            }
            assertEquals(listOf("goal-b", "goal-a", "goal-c"), afterReopen.goals.map { it.id })
            assertEquals(3, db.goalDao.pending("owner").size)

            // 4. Offline upload -> online upload -> acknowledgement
            val sync = processor()
            assertFalse(sync.upload("owner"))
            remote.offline = false
            assertTrue(sync.upload("owner"))
            assertTrue(db.goalDao.pending("owner").isEmpty())
            assertEquals(120_000L, remote.goals.getValue("goal-a").targetMinor)
            assertEquals(50_000L, remote.goals.getValue("goal-b").targetMinor)

            // 5. Remote reconciliation preserves local pending edits and applies newer remote updates
            repo.edit(editedA.copy(name = "Local Pending Edit")).getOrThrow()
            val remoteIncoming = listOf(
                remote.goals.getValue("goal-a").copy(
                    name = "Stale Remote Name",
                    updatedAt = editedA.updatedAt + 10_000L
                ),
                remote.goals.getValue("goal-b").copy(
                    name = "Remote Updated Vacation",
                    updatedAt = goalB.updatedAt + 10_000L
                )
            )
            sync.reconcile("owner", remoteIncoming)
            assertEquals("Local Pending Edit", db.goalDao.get("owner", "goal-a")?.name)
            assertTrue(db.goalDao.get("owner", "goal-a")?.pendingSync == true)
            assertEquals("Remote Updated Vacation", db.goalDao.get("owner", "goal-b")?.name)
            assertFalse(db.goalDao.get("owner", "goal-b")?.pendingSync == true)

            // 6. User isolation across account switch and sign out
            auth.currentUser.value = User("other", null, null)
            val otherSnapshot = awaitSnapshot(repo, owner = "other")
            assertTrue(otherSnapshot.goals.isEmpty())
            assertNull(repo.getGoal("goal-a").getOrThrow())

            auth.currentUser.value = null
            val signedOutSnapshot = awaitSnapshot(repo, owner = null)
            assertNull(signedOutSnapshot.ownerId)
            assertTrue(signedOutSnapshot.goals.isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private class FakeCurrencyPrefs(var code: String) : CurrencyPreferenceRepository {
        override fun observe(): Flow<CurrencyPreference> = flowOf(CurrencyPreference(code, pendingSync = false))
        override suspend fun set(currency: String): Result<Unit> = Result.success(Unit)
        override suspend fun current(): String = code
    }

    private class FakeGoalRemote : GoalRemoteDataSource {
        var offline = true
        val goals = mutableMapOf<String, FinancialGoal>()

        override fun observe(uid: String): Flow<List<FinancialGoal>> =
            flowOf(goals.values.filter { it.userId == uid })

        override suspend fun write(goal: FinancialGoal): FinancialGoal {
            check(!offline) { "Offline" }
            val existing = goals[goal.id]
            return if (existing != null && existing.updatedAt > goal.updatedAt) {
                existing
            } else {
                val saved = goal.copy(
                    userId = existing?.userId ?: goal.userId,
                    currency = existing?.currency ?: goal.currency,
                    createdAt = existing?.createdAt ?: goal.createdAt,
                    pendingSync = false
                )
                goals[goal.id] = saved
                saved
            }
        }
    }

    private class TestAuth : AuthRepository {
        override val currentUser = MutableStateFlow<User?>(User("owner", null, null))
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
        override suspend fun signOut() { currentUser.value = null }
    }
}
