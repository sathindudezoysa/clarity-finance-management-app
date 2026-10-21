package com.example.clarity

import com.example.clarity.data.local.dao.BudgetDao
import com.example.clarity.data.local.entity.*
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.sync.BudgetAlertEvaluator
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BudgetAlertEvaluatorTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC)
    private val budget = Budget(BudgetPolicy.id("2026-10", "Food"), "owner", "2026-10", "Food", 1_000_000, "LKR", updatedAt = 1)
    private val auth = TestAuth()
    private val dao = FakeDao()
    private val notifications = FakeNotifications()
    private fun evaluator() = BudgetAlertEvaluator(dao, auth, notifications, clock)
    private suspend fun evaluate(spent: Long, value: Budget? = null) {
        val current = value ?: dao.get("owner", budget.id)!!.toDomain()
        evaluator().evaluate(BudgetOverview("owner", listOf(BudgetStatus(current, spent.toBigInteger(), 0))))
    }

    @Test fun escalationsAlertOnceWhileVisibleContentRefreshesSilentlyAndDismissalsStayDismissed() = runBlocking {
        dao.upsert(budget.toEntity())
        for (spent in listOf(800_000L, 1_000_000L, 1_050_000L)) { evaluate(spent); evaluate(spent) }
        assertEquals(listOf(1, 2, 3), notifications.delivered.map { it.threshold.level })
        assertEquals(listOf(1, 2, 3), notifications.refreshed.map { it.threshold.level })
        evaluate(1_100_000)
        assertEquals(100_000.toBigInteger(), -notifications.visible!!.remaining)
        assertEquals(3, notifications.delivered.size)
        notifications.visible = null // Simulate a user dismissal, then reopen/retry with a new evaluator instance.
        evaluate(1_100_000)
        assertNull(notifications.visible)
        assertEquals(4, notifications.refreshed.size)
        evaluate(0); evaluate(1_050_000)
        assertNull(notifications.visible)
        assertEquals(3, notifications.delivered.size)
    }

    @Test fun changedLimitsInvalidateOnceAndStaleEvaluationsCannotConsumeNewHistory() = runBlocking {
        dao.upsert(budget.toEntity())
        evaluate(1_050_000) // Direct jump consumes all lower levels.
        assertEquals(listOf(3), notifications.delivered.map { it.threshold.level })
        val changed = budget.copy(limitMinor = 1_200_000, updatedAt = 2)
        dao.upsert(changed.toEntity())
        assertEquals(0, dao.alert("owner", budget.id)!!.level)
        evaluate(1_050_000, budget) // Stale overview from before the edit.
        assertEquals(0, dao.alert("owner", budget.id)!!.level)
        assertEquals(1, notifications.delivered.size)
        // Also reject a stale claim when remote versions happen to have equal timestamps.
        assertFalse(dao.claimAlert("owner", budget.id, 3, changed.updatedAt, budget.limitMinor))
        evaluate(1_050_000)
        assertEquals(listOf(3, 1), notifications.delivered.map { it.threshold.level })
        assertEquals(changed.limitMinor, notifications.visible!!.budget.limitMinor)
        dao.upsert(changed.copy(updatedAt = 3, pendingSync = true).toEntity()) // Unchanged limit / acknowledgement.
        evaluate(1_050_000)
        assertEquals(1, dao.alert("owner", budget.id)!!.level)
        assertEquals(2, notifications.delivered.size)
        evaluate(0); evaluate(1_050_000)
        assertEquals(2, notifications.delivered.size)
        assertNull(notifications.visible)
        dao.upsert(changed.copy(limitMinor = 2_000_000, updatedAt = 4).toEntity())
        notifications.visible = BudgetStatus(changed, 1_050_000.toBigInteger(), 0)
        notifications.enabled = false
        evaluate(1_800_000)
        assertNull(notifications.visible) // Old-limit content is canceled even when delivery is disabled.
        assertEquals(0, dao.alert("owner", budget.id)!!.level)
        notifications.enabled = true
        evaluate(1_800_000)
        assertEquals(listOf(3, 1, 1), notifications.delivered.map { it.threshold.level })
    }

    @Test fun ownerAndBudgetVersionsGuardAllNotificationChanges() = runBlocking {
        dao.upsert(budget.toEntity())
        evaluate(800_000)
        dao.upsert(budget.copy(updatedAt = 2).toEntity())
        evaluate(0, budget) // An obsolete NORMAL result must not cancel a newer warning.
        assertNotNull(notifications.visible)
        dao.beforeClaim = { dao.upsert(budget.copy(limitMinor = 2_000_000, updatedAt = 3).toEntity()) }
        evaluate(1_050_000) // The limit changes between reading history and the atomic claim.
        assertEquals(0, dao.alert("owner", budget.id)!!.level)
        assertEquals(1, notifications.delivered.size)
        auth.currentUser.value = User("other", null, null)
        evaluate(1_050_000)
        assertEquals(listOf(1), notifications.delivered.map { it.threshold.level })
        assertEquals(0, dao.alert("owner", budget.id)!!.level)
    }

    @Test fun yearlyBudgetsAlertInCurrentYearAndCancelInPastYears() = runBlocking {
        val currentYearly = Budget(BudgetPolicy.id("2026", "Food"), "owner", "2026", "Food", 10_000_000, "LKR", updatedAt = 1)
        val pastYearly = Budget(BudgetPolicy.id("2025", "Food"), "owner", "2025", "Food", 10_000_000, "LKR", updatedAt = 1)
        dao.upsert(currentYearly.toEntity())
        dao.upsert(pastYearly.toEntity())

        evaluator().evaluate(BudgetOverview("owner", listOf(
            BudgetStatus(currentYearly, 8_000_000.toBigInteger(), 0),
            BudgetStatus(pastYearly, 12_000_000.toBigInteger(), 0)
        )))

        assertEquals(listOf(currentYearly.id), notifications.delivered.map { it.budget.id })
        assertEquals(listOf(1), notifications.delivered.map { it.threshold.level })
        assertNull(dao.alert("owner", pastYearly.id))
    }

    // Use the production DAO's upsert/claim bodies with an in-memory backing store. Room's
    // transaction/restart behavior is separately exercised by BudgetPersistenceTest.
    private class FakeDao : BudgetDao() {
        var beforeClaim: (suspend () -> Unit)? = null
        private val budgets = mutableMapOf<Pair<String, String>, BudgetEntity>()
        private val alerts = mutableMapOf<Pair<String, String>, BudgetAlertEntity>()
        override fun observe(uid: String) = flowOf(budgets.values.filter { it.userId == uid })
        override suspend fun get(uid: String, id: String) = budgets[uid to id]
        override suspend fun pending(uid: String) = budgets.values.filter { it.userId == uid && it.pendingSync }
        override suspend fun persist(row: BudgetEntity) { budgets[row.userId to row.id] = row }
        override suspend fun resolve(row: BudgetResolutionEntity) = Unit
        override fun resolutions(uid: String): Flow<List<BudgetResolutionEntity>> = flowOf(emptyList())
        override suspend fun alert(uid: String, id: String) = alerts[uid to id]
        override suspend fun putAlert(row: BudgetAlertEntity) { alerts[row.userId to row.budgetId] = row }
        override suspend fun claimAlert(uid: String, id: String, level: Int, expectedVersion: Long, expectedLimit: Long): Boolean {
            val action = beforeClaim
            beforeClaim = null
            action?.invoke()
            return super.claimAlert(uid, id, level, expectedVersion, expectedLimit)
        }
    }
    private class FakeNotifications : BudgetNotificationDelivery {
        var enabled = true
        var visible: BudgetStatus? = null
        val delivered = mutableListOf<BudgetStatus>()
        val refreshed = mutableListOf<BudgetStatus>()
        override fun available() = enabled
        override fun deliver(status: BudgetStatus) { delivered += status; visible = status }
        override fun refreshVisible(status: BudgetStatus) { if (visible != null) { refreshed += status; visible = status } }
        override fun cancel(owner: String, budgetId: String) { visible = null }
        override fun cancelOwner(owner: String) { visible = null }
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
