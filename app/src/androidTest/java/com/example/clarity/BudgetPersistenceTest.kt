package com.example.clarity

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.remote.BudgetRemoteDataSource
import com.example.clarity.data.repository.BudgetRepositoryImpl
import com.example.clarity.data.sync.*
import com.example.clarity.di.DatabaseModule
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BudgetPersistenceTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC)
    private val budget = Budget(BudgetPolicy.id("2026-10", "Food"), "owner", "2026-10", "Food", 1_000_000, "LKR")
    private val rates = object : ExchangeRateRepository {
        override suspend fun cached(source: String, target: String): ExchangeRate? = null
        override suspend fun latest(source: String, target: String): ExchangeRate = error("Offline")
    }

    @Test fun offlineRestartDedupAcknowledgementAndUserIsolation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "budget-${UUID.randomUUID()}"
        val auth = TestAuth()
        val signals = BudgetSignals()
        val notifications = FakeNotifications()
        var db = DatabaseModule.createDatabase(context, name)
        fun repository() = BudgetRepositoryImpl(db, auth, rates, clock, signals, Dispatchers.IO)
        suspend fun status(repo: BudgetRepository) = withTimeout(5_000) { repo.observe().first { it.statuses.isNotEmpty() } }
        try {
            var repo = repository()
            repo.save(budget).getOrThrow()
            assertTrue(repo.save(budget.copy(currency = "JPY")).isFailure)
            assertTrue(db.budgetDao.get("owner", budget.id)!!.pendingSync)
            val expense = TransactionEntity("expense", "owner", "EXPENSE", 1_050_000, "Food", "",
                clock.millis(), clock.millis(), clock.millis(), pendingSync = true)
            db.transactionDao.upsert(expense)
            db.transactionDao.upsert(expense.copy(id = "deleted", amountMinor = 9999999, isDeleted = true))
            var overview = status(repo)
            assertEquals(1_050_000.toBigInteger(), overview.statuses.single().spent)
            var evaluator = BudgetAlertEvaluator(db, auth, notifications, clock)
            evaluator.evaluate(overview); evaluator.evaluate(overview)
            assertEquals(listOf(2), notifications.delivered)
            db.close()
            db = DatabaseModule.createDatabase(context, name)
            repo = repository()
            evaluator = BudgetAlertEvaluator(db, auth, notifications, clock)
            evaluator.evaluate(status(repo))
            assertEquals(listOf(2), notifications.delivered)
            db.transactionDao.upsert(expense.copy(amountMinor = 800_000))
            evaluator.evaluate(status(repo))
            assertEquals(listOf(2), notifications.delivered)
            assertEquals(BudgetThreshold.NEARING, status(repo).statuses.single().threshold)

            val remote = FakeRemote()
            val processor = BudgetSyncProcessor(db, remote, auth, signals)
            assertFalse(processor.upload("owner"))
            remote.offline = false
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            remote.onWrite = { entered.complete(Unit); release.await() }
            coroutineScope {
                val uploading = async(Dispatchers.IO) { processor.upload("owner") }
                entered.await()
                repo.save(db.budgetDao.get("owner", budget.id)!!.toDomain().copy(limitMinor = 2_000_000)).getOrThrow()
                release.complete(Unit)
                assertFalse(uploading.await())
            }
            assertTrue(db.budgetDao.get("owner", budget.id)!!.pendingSync)
            remote.onWrite = {}
            assertTrue(processor.upload("owner"))
            assertEquals(2_000_000L, remote.rows.getValue(budget.id).limitMinor)
            repo.disable(budget.id).getOrThrow()
            val disabled = db.budgetDao.get("owner", budget.id)!!
            processor.reconcile("owner", listOf(budget.copy(updatedAt = disabled.updatedAt - 1)))
            assertFalse(db.budgetDao.get("owner", budget.id)!!.active)
            assertTrue(processor.upload("owner"))
            processor.reconcile("owner", listOf(budget.copy(updatedAt = disabled.updatedAt - 1)))
            assertFalse(db.budgetDao.get("owner", budget.id)!!.active)
            evaluator.evaluate(status(repo))
            assertTrue(notifications.cancelled.contains(budget.id))

            auth.currentUser.value = User("other", null, null)
            assertTrue(repo.observe().first { !it.loading && it.ownerId == "other" }.statuses.isEmpty())
            assertTrue(repo.save(budget).isFailure)
            assertTrue(repo.disable(budget.id).isFailure)
            assertTrue(runCatching { processor.upload("owner") }.isFailure)
            evaluator.evaluate(overview)
            assertEquals(listOf(2), notifications.delivered)
            auth.currentUser.value = null
            assertNull(repo.observe().first().ownerId)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    private class FakeNotifications : BudgetNotificationDelivery {
        val delivered = mutableListOf<Int>()
        val cancelled = mutableListOf<String>()
        override fun available() = true
        override fun deliver(status: BudgetStatus) { delivered += status.threshold.level }
        override fun cancel(owner: String, budgetId: String) { cancelled += budgetId }
        override fun cancelOwner(owner: String) {}
    }
    private class FakeRemote : BudgetRemoteDataSource {
        var offline = true
        var onWrite: suspend () -> Unit = {}
        val rows = mutableMapOf<String, Budget>()
        override fun observe(uid: String): Flow<List<Budget>> = flowOf(rows.values.filter { it.userId == uid })
        override suspend fun write(budget: Budget): Budget {
            check(!offline)
            onWrite()
            val saved = budget.copy(pendingSync = false)
            rows[budget.id] = saved
            return saved
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
