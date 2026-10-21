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
import com.example.clarity.domain.usecase.budget.*
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import com.example.clarity.presentation.budgets.BudgetsViewModel
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
            var evaluator = BudgetAlertEvaluator(db.budgetDao, auth, notifications, clock)
            evaluator.evaluate(overview); evaluator.evaluate(overview)
            assertEquals(listOf(3), notifications.delivered)
            db.close()
            db = DatabaseModule.createDatabase(context, name)
            repo = repository()
            evaluator = BudgetAlertEvaluator(db.budgetDao, auth, notifications, clock)
            evaluator.evaluate(status(repo))
            assertEquals(listOf(3), notifications.delivered)
            db.transactionDao.upsert(expense.copy(amountMinor = 800_000))
            evaluator.evaluate(status(repo))
            assertEquals(listOf(3), notifications.delivered)
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
            assertEquals(0, db.budgetDao.alert("owner", budget.id)!!.level)
            assertFalse(db.budgetDao.claimAlert("owner", budget.id, 3, overview.statuses.single().budget.updatedAt, budget.limitMinor))
            evaluator.evaluate(overview)
            assertEquals(0, db.budgetDao.alert("owner", budget.id)!!.level)
            assertTrue(db.budgetDao.get("owner", budget.id)!!.pendingSync)
            remote.onWrite = {}
            assertTrue(processor.upload("owner"))
            assertEquals(2_000_000L, remote.rows.getValue(budget.id).limitMinor)
            assertEquals(0, db.budgetDao.alert("owner", budget.id)!!.level) // Acknowledgement does not claim or reset again.
            val remoteChange = remote.rows.getValue(budget.id).copy(limitMinor = 750_000,
                updatedAt = db.budgetDao.get("owner", budget.id)!!.updatedAt + 10)
            processor.reconcile("owner", listOf(remoteChange))
            assertEquals(0, db.budgetDao.alert("owner", budget.id)!!.level)
            evaluator.evaluate(status(repo))
            assertEquals(listOf(3, 3), notifications.delivered)
            processor.reconcile("owner", listOf(remoteChange))
            processor.reconcile("owner", listOf(remoteChange.copy(updatedAt = remoteChange.updatedAt + 1)))
            assertEquals(3, db.budgetDao.alert("owner", budget.id)!!.level)
            repo.save(db.budgetDao.get("owner", budget.id)!!.toDomain()).getOrThrow()
            assertEquals(3, db.budgetDao.alert("owner", budget.id)!!.level)
            assertTrue(processor.upload("owner"))
            evaluator.evaluate(status(repo))
            assertEquals(listOf(3, 3), notifications.delivered)
            assertNull(db.budgetDao.alert("other", budget.id))
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
            assertEquals(listOf(3, 3), notifications.delivered)
            auth.currentUser.value = null
            assertNull(repo.observe().first().ownerId)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun viewModelPreservesSelectedMonthAcrossEmissionsSupportsDeepLinkAndDisableReenable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "budget-vm-${UUID.randomUUID()}"
        val auth = TestAuth()
        val signals = BudgetSignals()
        val db = DatabaseModule.createDatabase(context, name)
        val repo = BudgetRepositoryImpl(db, auth, rates, clock, signals, Dispatchers.IO)
        val tagsRepo = object : TransactionTagRepository {
            override fun observeTags(): Flow<List<String>> = flowOf(listOf("CustomTag"))
        }
        val prefRepo = object : CurrencyPreferenceRepository {
            override fun observe(): Flow<CurrencyPreference> = flowOf(CurrencyPreference("LKR"))
            override suspend fun set(currency: String): Result<Unit> = Result.success(Unit)
            override suspend fun current(): String = "LKR"
        }
        val vm = BudgetsViewModel(
            observe = ObserveBudgetsUseCase(repo),
            tags = GetTransactionTagsUseCase(tagsRepo),
            preference = ObserveCurrencyPreferenceUseCase(prefRepo),
            saveBudget = SaveBudgetUseCase(repo),
            disableBudget = DisableBudgetUseCase(repo),
            resolve = ResolveBudgetCurrencyUseCase(repo),
            clock = clock,
            auth = auth
        )
        try {
            repo.save(budget).getOrThrow()
            val novBudget = Budget(BudgetPolicy.id("2026-11", "Travel"), "owner", "2026-11", "Travel", 500_000, "LKR")
            repo.save(novBudget).getOrThrow()
            withTimeout(5_000) { vm.state.first { !it.loading && it.overview.statuses.size == 2 } }

            // Navigate to November; currentMonth stays 2026-10 and re-emissions do not reset selectedMonth.
            vm.nextMonth()
            assertEquals("2026-11", vm.state.value.selectedMonth)
            assertEquals("2026-10", vm.state.value.currentMonth)
            db.transactionDao.upsert(
                TransactionEntity("e1", "owner", "EXPENSE", 250_000, "Food", "",
                    clock.millis(), clock.millis(), clock.millis(), pendingSync = true)
            )
            withTimeout(5_000) {
                vm.state.first {
                    it.overview.statuses.firstOrNull { s -> s.budget.id == budget.id }?.spent == 250_000.toBigInteger()
                }
            }
            assertEquals("2026-11", vm.state.value.selectedMonth)
            assertEquals("2026-10", vm.state.value.currentMonth)

            // Deep link switches selectedMonth once per selectedId, then allows user navigation.
            vm.applySelectedId(budget.id)
            assertEquals("2026-10", vm.state.value.selectedMonth)
            vm.nextMonth()
            assertEquals("2026-11", vm.state.value.selectedMonth)
            vm.applySelectedId(budget.id)
            assertEquals("2026-11", vm.state.value.selectedMonth)

            // Disable confirmation flow and re-enable via editor.
            vm.selectMonth("2026-10")
            vm.requestDisable(budget)
            assertEquals(budget.id, vm.state.value.confirmDisable?.id)
            vm.confirmDisable()
            withTimeout(5_000) { vm.state.first { !it.busy && it.monthView.disabled.any { d -> d.budget.id == budget.id } } }
            val disabled = vm.state.value.monthView.disabled.single().budget
            vm.edit(disabled)
            vm.changeAmount("15000.00")
            vm.save()
            withTimeout(5_000) {
                vm.state.first {
                    !it.busy && it.monthView.active.any { a -> a.budget.id == budget.id && a.budget.limitMinor == 1_500_000L }
                }
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private class FakeNotifications : BudgetNotificationDelivery {
        val delivered = mutableListOf<Int>()
        val cancelled = mutableListOf<String>()
        override fun available() = true
        override fun deliver(status: BudgetStatus) { delivered += status.threshold.level }
        override fun refreshVisible(status: BudgetStatus) {}
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
