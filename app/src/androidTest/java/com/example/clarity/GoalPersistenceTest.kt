package com.example.clarity

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.entity.AccountEntity
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.remote.GoalDetailWriteResult
import com.example.clarity.data.remote.GoalOperationWriteResult
import com.example.clarity.data.remote.GoalRemoteDataSource
import com.example.clarity.data.repository.GoalRepositoryImpl
import com.example.clarity.data.sync.GoalSyncProcessor
import com.example.clarity.di.DatabaseModule
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.GoalProgressOperation
import com.example.clarity.domain.model.GoalProgressOperationState
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.model.GoalStatus
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.CurrencyPreference
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.util.GoalPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
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

    private fun lkrIncomeTx(
        id: String,
        accountId: String,
        amountMinor: Long,
        userId: String = "owner"
    ) = TransactionEntity(
        id = id,
        userId = userId,
        type = "INCOME",
        amountMinor = amountMinor,
        category = "Salary",
        note = "Seed income",
        dateEpochMillis = 1_000L,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        pendingSync = false,
        isDeleted = false,
        accountId = accountId,
        currency = "LKR",
        targetCurrency = "LKR",
        conversion = ConversionSnapshot.identity(amountMinor),
        pendingConversion = false
    )

    private fun lkrExpenseTx(
        id: String,
        accountId: String,
        amountMinor: Long,
        userId: String = "owner"
    ) = TransactionEntity(
        id = id,
        userId = userId,
        type = "EXPENSE",
        amountMinor = amountMinor,
        category = "Shopping",
        note = "Expense",
        dateEpochMillis = 2_000L,
        createdAt = 2_000L,
        updatedAt = 2_000L,
        pendingSync = false,
        isDeleted = false,
        accountId = accountId,
        currency = "LKR",
        targetCurrency = "LKR",
        conversion = ConversionSnapshot.identity(amountMinor),
        pendingConversion = false
    )

    @Test
    fun multiGoalLifecycle_atomicAccountLinkedContributions_rollback_idempotency_reconcile_rejection_andUserIsolation() = runBlocking {
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

        suspend fun availableFor(accountId: String): BigInteger {
            val txs = db.transactionDao.getActiveForUser("owner").map { it.toDomain() }
            val ops = db.goalProgressOperationDao.getByUser("owner").map { it.toDomain() }
            return GoalPolicy.availableBalanceForAccount(accountId, txs, ops)
        }

        try {
            // Seed accounts and initial income balances:
            // acc-1 (LKR bank account): 55_000 LKR
            // cash_wallet_owner (LKR cash wallet): 30_000 LKR
            // acc-usd (USD account): 100_000 USD
            // acc-other (owned by 'other'): 100_000 LKR
            db.accountDao.upsertAll(
                listOf(
                    AccountEntity(
                        id = "acc-1",
                        userId = "owner",
                        name = "Main Savings",
                        bankName = "BOC",
                        accountType = "SAVINGS",
                        currency = "LKR",
                        isDefault = true,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                        pendingSync = false
                    ),
                    AccountEntity(
                        id = "cash_wallet_owner",
                        userId = "owner",
                        name = "Cash Wallet",
                        bankName = "Cash",
                        accountType = "CASH",
                        currency = "LKR",
                        isDefault = false,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                        pendingSync = false
                    ),
                    AccountEntity(
                        id = "acc-usd",
                        userId = "owner",
                        name = "USD Account",
                        bankName = "HSBC",
                        accountType = "SAVINGS",
                        currency = "USD",
                        isDefault = false,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                        pendingSync = false
                    ),
                    AccountEntity(
                        id = "acc-other",
                        userId = "other",
                        name = "Other User Account",
                        bankName = "BOC",
                        accountType = "SAVINGS",
                        currency = "LKR",
                        isDefault = true,
                        createdAt = 1_000L,
                        updatedAt = 1_000L,
                        pendingSync = false
                    )
                )
            )
            db.transactionDao.persistLocal(lkrIncomeTx("inc-acc-1", "acc-1", 55_000L))
            db.transactionDao.persistLocal(lkrIncomeTx("inc-cash", "cash_wallet_owner", 30_000L))

            var repo = repository()

            // 1. Create multiple goals offline
            val goalA = repo.create(
                FinancialGoal(
                    id = "goal-a",
                    name = "Emergency Fund",
                    description = "Six months",
                    targetMinor = 100_000L,
                    savedMinor = 10_000L,
                    currency = "",
                    targetDateEpochDay = futureDay1
                )
            ).getOrThrow()
            val goalB = repo.create(
                FinancialGoal(
                    id = "goal-b",
                    name = "Vacation",
                    description = "Trip",
                    targetMinor = 50_000L,
                    savedMinor = 0L,
                    currency = "",
                    targetDateEpochDay = futureDay2
                )
            ).getOrThrow()

            assertEquals("LKR", goalA.currency)
            assertEquals("LKR", goalB.currency)
            assertTrue(goalA.pendingSync)
            assertEquals(GoalStatus.ACTIVE, goalA.status)

            val initialSnapshot = withTimeout(5_000) {
                repo.observeGoals().first { it.ownerId == "owner" && it.goals.size == 2 }
            }
            assertEquals(listOf("goal-a", "goal-b"), initialSnapshot.goals.map { it.id })

            // 2. Updating one goal's details leaves the other goal untouched
            val editedA = repo.edit(
                goalA.copy(
                    name = "Emergency Reserve",
                    description = "Updated description",
                    targetMinor = 120_000L,
                    targetDateEpochDay = futureDay1 + 5L
                )
            ).getOrThrow()
            assertEquals("Emergency Reserve", editedA.name)
            assertEquals(120_000L, editedA.targetMinor)
            assertEquals(10_000L, editedA.savedMinor)
            assertEquals("Vacation", repo.getGoal("goal-b").getOrThrow()!!.name)

            // Reject lowering target below saved amount
            val invalidEdit = repo.edit(editedA.copy(targetMinor = 5_000L))
            assertTrue(invalidEdit.isFailure)
            assertEquals(GoalError.TARGET_BELOW_SAVED, (invalidEdit.exceptionOrNull() as GoalException).error)

            // 3. Reject missing account, unowned account, currency mismatch, and insufficient account balance
            val blankAccountRes = repo.addContribution("goal-a", "op-bad-blank", 5_000L, "   ")
            assertEquals(GoalError.ACCOUNT_REQUIRED, (blankAccountRes.exceptionOrNull() as GoalException).error)

            val missingAccountRes = repo.addContribution("goal-a", "op-bad-missing", 5_000L, "acc-nonexistent")
            assertEquals(GoalError.ACCOUNT_NOT_FOUND, (missingAccountRes.exceptionOrNull() as GoalException).error)

            val unownedAccountRes = repo.addContribution("goal-a", "op-bad-owner", 5_000L, "acc-other")
            assertEquals(GoalError.ACCOUNT_NOT_FOUND, (unownedAccountRes.exceptionOrNull() as GoalException).error)

            val mismatchCurrencyRes = repo.addContribution("goal-a", "op-bad-curr", 5_000L, "acc-usd")
            assertEquals(GoalError.ACCOUNT_CURRENCY_MISMATCH, (mismatchCurrencyRes.exceptionOrNull() as GoalException).error)

            val insufficientRes = repo.addContribution("goal-a", "op-bad-bal", 55_001L, "acc-1")
            assertEquals(GoalError.INSUFFICIENT_ACCOUNT_BALANCE, (insufficientRes.exceptionOrNull() as GoalException).error)
            // Verify both goal and account are unchanged after rejected attempts
            assertEquals(10_000L, repo.getGoal("goal-a").getOrThrow()!!.savedMinor)
            assertEquals(BigInteger.valueOf(55_000L), availableFor("acc-1"))

            // 4. Simulated failure inside Room transaction rolls back BOTH operation insert and goal upsert
            repo.beforeGoalUpsertHook = { throw IllegalStateException("Simulated crash after operation insert") }
            val crashedRes = repo.addContribution("goal-a", "op-rollback", 15_000L, "acc-1")
            assertTrue(crashedRes.isFailure)
            repo.beforeGoalUpsertHook = null
            assertNull(db.goalProgressOperationDao.get("owner", "op-rollback"))
            assertEquals(10_000L, repo.getGoal("goal-a").getOrThrow()!!.savedMinor)
            assertEquals(BigInteger.valueOf(55_000L), availableFor("acc-1"))

            // 5. Repeated contributions from same and different accounts (including cash wallet) accumulate atomically
            val step1 = repo.addContribution("goal-a", "op-1", 15_000L, "acc-1").getOrThrow()
            assertEquals(25_000L, step1.savedMinor)
            assertEquals(BigInteger.valueOf(40_000L), availableFor("acc-1"))

            // Duplicate operationId with identical fields must NOT double-apply (neither goal nor account changes)
            val step1Retry = repo.addContribution("goal-a", "op-1", 15_000L, "acc-1").getOrThrow()
            assertEquals(25_000L, step1Retry.savedMinor)
            assertEquals(BigInteger.valueOf(40_000L), availableFor("acc-1"))

            // Duplicate operationId with different accountId or amount is rejected with INVALID_ID
            val conflictingRetryAccount = repo.addContribution("goal-a", "op-1", 15_000L, "cash_wallet_owner")
            assertEquals(GoalError.INVALID_ID, (conflictingRetryAccount.exceptionOrNull() as GoalException).error)
            val conflictingRetryAmount = repo.addContribution("goal-a", "op-1", 10_000L, "acc-1")
            assertEquals(GoalError.INVALID_ID, (conflictingRetryAmount.exceptionOrNull() as GoalException).error)

            // Contribute from second account (cash_wallet_owner)
            val step2 = repo.addContribution("goal-a", "op-2", 25_000L, "cash_wallet_owner").getOrThrow()
            assertEquals(50_000L, step2.savedMinor)
            assertEquals(BigInteger.valueOf(40_000L), availableFor("acc-1"))
            assertEquals(BigInteger.valueOf(5_000L), availableFor("cash_wallet_owner"))

            // 6. TransactionDao.persistLocal enforces goal reservations via TransactionBalanceGuard:
            // acc-1 has 55_000 net tx and 15_000 reserved -> 40_000 available.
            // Expense of 40_001 on acc-1 must be blocked:
            try {
                db.transactionDao.persistLocal(lkrExpenseTx("exp-too-high", "acc-1", 40_001L))
                fail("Expected TransactionException(INSUFFICIENT_BALANCE) when expense exceeds unreserved balance")
            } catch (e: TransactionException) {
                assertEquals(TransactionError.INSUFFICIENT_BALANCE, e.error)
            }
            // Expense of 10_000 on acc-1 succeeds, leaving 30_000 available on acc-1:
            db.transactionDao.persistLocal(lkrExpenseTx("exp-ok", "acc-1", 10_000L))
            assertEquals(BigInteger.valueOf(30_000L), availableFor("acc-1"))

            // 7. Set absolute balance on goal-b to complete it without changing any account balance
            val completedB = repo.setBalance("goal-b", "op-b1", 50_000L).getOrThrow()
            assertEquals(50_000L, completedB.savedMinor)
            assertEquals(GoalStatus.COMPLETED, completedB.status)
            assertEquals(50_000L, repo.getGoal("goal-a").getOrThrow()!!.savedMinor)
            assertEquals(BigInteger.valueOf(30_000L), availableFor("acc-1"))
            assertEquals(BigInteger.valueOf(5_000L), availableFor("cash_wallet_owner"))

            // 8. Reopen Room database and verify persistence of goals, pending operations, sourceAccountId, and available balances
            db.close()
            db = DatabaseModule.createDatabase(context, name)
            repo = repository()
            val afterReopen = withTimeout(5_000) {
                repo.observeGoals().first { it.ownerId == "owner" && it.goals.size == 2 }
            }
            assertEquals(2, afterReopen.goals.size)
            assertEquals(50_000L, afterReopen.goals.first { it.id == "goal-a" }.savedMinor)
            assertEquals(GoalStatus.COMPLETED, afterReopen.goals.first { it.id == "goal-b" }.status)
            assertEquals(3, db.goalProgressOperationDao.pending("owner").size)
            assertEquals("acc-1", db.goalProgressOperationDao.get("owner", "op-1")?.sourceAccountId)
            assertEquals("cash_wallet_owner", db.goalProgressOperationDao.get("owner", "op-2")?.sourceAccountId)
            assertNull(db.goalProgressOperationDao.get("owner", "op-b1")?.sourceAccountId)
            assertEquals(BigInteger.valueOf(30_000L), availableFor("acc-1"))
            assertEquals(BigInteger.valueOf(5_000L), availableFor("cash_wallet_owner"))

            // 9. Offline upload -> online upload -> acknowledgement (PENDING -> APPLIED does not double-deduct or release reservation)
            val sync = processor()
            assertFalse(sync.upload("owner")) // Still offline
            remote.offline = false
            assertTrue(sync.upload("owner"))
            assertTrue(db.goalDao.pending("owner").isEmpty())
            assertTrue(db.goalProgressOperationDao.pending("owner").isEmpty())
            assertEquals(50_000L, remote.goals.getValue("goal-a").savedMinor)
            assertEquals(50_000L, remote.goals.getValue("goal-b").savedMinor)
            assertEquals("acc-1", remote.appliedOps.getValue("op-1").sourceAccountId)
            assertEquals("cash_wallet_owner", remote.appliedOps.getValue("op-2").sourceAccountId)
            // Available balances remain identical after PENDING -> APPLIED
            assertEquals(BigInteger.valueOf(30_000L), availableFor("acc-1"))
            assertEquals(BigInteger.valueOf(5_000L), availableFor("cash_wallet_owner"))

            // 10. Reconciliation replays ONLY PENDING operations (never APPLIED operations)
            // Create a new local pending operation op-3 (+20_000 from acc-1 -> goal-a 70_000, acc-1 available 10_000) while offline
            remote.offline = true
            repo.addContribution("goal-a", "op-3", 20_000L, "acc-1").getOrThrow()
            assertEquals(70_000L, repo.getGoal("goal-a").getOrThrow()!!.savedMinor)
            assertEquals(BigInteger.valueOf(10_000L), availableFor("acc-1"))

            // Simulate remote snapshot arriving with server savedMinor = 50_000 (before op-3 uploaded)
            val remoteSnapshotBeforeOp3 = listOf(
                remote.goals.getValue("goal-a").copy(updatedAt = editedA.updatedAt + 100L),
                remote.goals.getValue("goal-b")
            )
            sync.reconcile("owner", remoteSnapshotBeforeOp3)
            // Reconciled local row should be remote 50_000 + pending op-3 (20_000) = 70_000 (op-1 and op-2 are APPLIED so not replayed!)
            assertEquals(70_000L, db.goalDao.get("owner", "goal-a")!!.savedMinor)
            assertEquals(BigInteger.valueOf(10_000L), availableFor("acc-1"))

            // 11. Rejected operation handling: if remote rejects op-3, goal-a reconciles to 50_000 AND acc-1 available balance is restored from 10_000 back to 30_000!
            remote.offline = false
            remote.forceRejectOperationIds.add("op-3")
            assertTrue(sync.upload("owner"))
            assertEquals(50_000L, db.goalDao.get("owner", "goal-a")!!.savedMinor)
            assertEquals(BigInteger.valueOf(30_000L), availableFor("acc-1"))
            val snapWithRejection = withTimeout(5_000) {
                repo.observeGoals().first { it.ownerId == "owner" && it.rejectedOperations.isNotEmpty() }
            }
            assertEquals(listOf("op-3"), snapWithRejection.rejectedOperations.map { it.id })

            // 12. User isolation across account switch and sign out
            auth.currentUser.value = User("other", null, null)
            val otherSnapshot = awaitSnapshot(repo, owner = "other")
            assertTrue(otherSnapshot.goals.isEmpty())
            assertTrue(otherSnapshot.operations.isEmpty())
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

    @Test
    fun migration16To17_preservesAllExistingV16TablesAndAddsNullableSourceAccountIdColumn() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-16-17-${UUID.randomUUID()}"
        context.deleteDatabase(name)

        // 1. Create a real v16 SQLite database using the exact ClarityDatabase_Impl v16 schema and identity hash
        val v16Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V16_CREATE_SQL.forEach { sql -> db.execSQL(sql) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        v16Helper.writableDatabase.use { sqlDb ->
            sqlDb.execSQL(
                "INSERT INTO `accounts` (`id`, `userId`, `name`, `bankName`, `accountType`, `currency`, `isDefault`, `createdAt`, `updatedAt`, `pendingSync`) " +
                    "VALUES ('acc-1', 'owner', 'Main Savings', 'BOC', 'SAVINGS', 'LKR', 1, 1000, 1000, 0)"
            )
            sqlDb.execSQL(
                "INSERT INTO `transactions` (`id`, `userId`, `type`, `amountMinor`, `category`, `note`, `dateEpochMillis`, `createdAt`, `updatedAt`, `pendingSync`, `isDeleted`, `accountId`, `currency`, `targetCurrency`, `pendingConversion`, `conversion_amountMinor`, `conversion_rate`, `conversion_provider`, `conversion_rateAt`, `conversion_fetchedAt`, `conversion_cached`) " +
                    "VALUES ('tx-inc-1', 'owner', 'INCOME', 100000, 'Salary', 'Monthly salary', 1000, 1000, 1000, 0, 0, 'acc-1', 'LKR', 'LKR', 0, 100000, '1', 'IDENTITY', 0, 0, 1)"
            )
            sqlDb.execSQL(
                "INSERT INTO `goals` (`userId`, `id`, `name`, `description`, `targetMinor`, `savedMinor`, `currency`, `targetDateEpochDay`, `status`, `createdAt`, `updatedAt`, `pendingSync`) " +
                    "VALUES ('owner', 'v16-goal', 'Car Fund', 'Existing v16 goal', 200000, 30000, 'LKR', $futureDay1, 'ACTIVE', 1000, 1000, 0)"
            )
            sqlDb.execSQL(
                "INSERT INTO `goal_progress_operations` (`userId`, `id`, `goalId`, `type`, `amountMinor`, `createdAt`, `state`) " +
                    "VALUES ('owner', 'legacy-op-1', 'v16-goal', 'ADD', 30000, 1000, 'APPLIED')"
            )
        }
        v16Helper.close()

        // 2. Open the database at v17 via DatabaseModule.createDatabase (runs MIGRATION_16_17 and validates schema)
        val db = DatabaseModule.createDatabase(context, name)
        try {
            // Verify pre-existing v16 rows survived and legacy operation has sourceAccountId == null
            assertEquals("Main Savings", db.accountDao.getById("acc-1")?.name)
            assertEquals(100_000L, db.transactionDao.getById("tx-inc-1")?.amountMinor)
            assertEquals(30_000L, db.goalDao.get("owner", "v16-goal")?.savedMinor)
            val legacyOp = db.goalProgressOperationDao.get("owner", "legacy-op-1")
            assertEquals(30_000L, legacyOp?.amountMinor)
            assertNull(legacyOp?.sourceAccountId)

            // Legacy operation with null sourceAccountId does not reduce acc-1's available balance
            val txsBefore = db.transactionDao.getActiveForUser("owner").map { it.toDomain() }
            val opsBefore = db.goalProgressOperationDao.getByUser("owner").map { it.toDomain() }
            assertEquals(
                BigInteger.valueOf(100_000L),
                GoalPolicy.availableBalanceForAccount("acc-1", txsBefore, opsBefore)
            )

            // New v17 account-linked contribution works and reduces acc-1's available balance
            val auth = TestAuth()
            val remote = FakeGoalRemote()
            val repo = GoalRepositoryImpl(
                database = db,
                auth = auth,
                currencyPreferenceRepository = FakeCurrencyPrefs("LKR"),
                processor = GoalSyncProcessor(db, remote, auth),
                ioDispatcher = Dispatchers.IO,
                clock = clock
            )
            val updated = repo.addContribution("v16-goal", "v17-op-1", 40_000L, "acc-1").getOrThrow()
            assertEquals(70_000L, updated.savedMinor)
            assertEquals("acc-1", db.goalProgressOperationDao.get("owner", "v17-op-1")?.sourceAccountId)

            val opsAfter = db.goalProgressOperationDao.getByUser("owner").map { it.toDomain() }
            assertEquals(
                BigInteger.valueOf(60_000L),
                GoalPolicy.availableBalanceForAccount("acc-1", txsBefore, opsAfter)
            )
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration15To16_preservesAllExistingV15TablesAndCreatesGoalTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-15-16-${UUID.randomUUID()}"
        context.deleteDatabase(name)

        // 1. Create a real v15 SQLite database using the exact ClarityDatabase_Impl v15 schema and identity hash
        val v15Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V15_CREATE_SQL.forEach { sql -> db.execSQL(sql) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        v15Helper.writableDatabase.use { sqlDb ->
            sqlDb.execSQL(
                "INSERT INTO `accounts` (`id`, `userId`, `name`, `bankName`, `accountType`, `currency`, `isDefault`, `createdAt`, `updatedAt`, `pendingSync`) " +
                    "VALUES ('acc-1', 'owner', 'Main Savings', 'BOC', 'SAVINGS', 'LKR', 1, 1000, 1000, 0)"
            )
            sqlDb.execSQL(
                "INSERT INTO `transactions` (`id`, `userId`, `type`, `amountMinor`, `category`, `note`, `dateEpochMillis`, `createdAt`, `updatedAt`, `pendingSync`, `isDeleted`, `accountId`, `currency`, `targetCurrency`, `pendingConversion`, `conversion_amountMinor`, `conversion_rate`, `conversion_provider`, `conversion_rateAt`, `conversion_fetchedAt`, `conversion_cached`) " +
                    "VALUES ('tx-1', 'owner', 'INCOME', 100000, 'Salary', 'Salary', 1000, 1000, 1000, 0, 0, 'acc-1', 'LKR', 'LKR', 0, 100000, '1', 'IDENTITY', 0, 0, 1)"
            )
            sqlDb.execSQL(
                "INSERT INTO `budgets` (`id`, `userId`, `month`, `category`, `categoryKey`, `limitMinor`, `currency`, `active`, `createdAt`, `updatedAt`, `pendingSync`) " +
                    "VALUES ('b-1', 'owner', '2026-10', 'Food', 'food', 50000, 'LKR', 1, 1000, 1000, 0)"
            )
            sqlDb.execSQL(
                "INSERT INTO `income_sources` (`userId`, `id`, `name`, `amountMinor`, `accountId`, `recurring`, `active`, `createdAt`, `updatedAt`, `lastConfirmedMonth`, `pendingSync`) " +
                    "VALUES ('owner', 'inc-1', 'Salary', 150000, 'acc-1', 1, 1, 1000, 1000, '2026-10', 0)"
            )
            sqlDb.execSQL(
                "INSERT INTO `currency_preferences` (`userId`, `currency`, `updatedAt`, `pendingSync`) " +
                    "VALUES ('owner', 'LKR', 1000, 0)"
            )
        }
        v15Helper.close()

        // 2. Open the database at v17 via DatabaseModule.createDatabase (runs MIGRATION_15_16 + MIGRATION_16_17 and validates schema)
        val db = DatabaseModule.createDatabase(context, name)
        try {
            assertEquals("Main Savings", db.accountDao.getById("acc-1")?.name)
            assertEquals(100000L, db.transactionDao.getById("tx-1")?.amountMinor)
            assertEquals(50000L, db.budgetDao.get("owner", "b-1")?.limitMinor)
            assertEquals("Salary", db.incomeSourceDao.get("owner", "inc-1")?.name)
            assertEquals("LKR", db.currencyDao.preference("owner")?.currency)

            val auth = TestAuth()
            val remote = FakeGoalRemote()
            val repo = GoalRepositoryImpl(
                database = db,
                auth = auth,
                currencyPreferenceRepository = FakeCurrencyPrefs("LKR"),
                processor = GoalSyncProcessor(db, remote, auth),
                ioDispatcher = Dispatchers.IO,
                clock = clock
            )
            val created = repo.create(
                FinancialGoal(
                    id = "migrated-goal",
                    name = "House Deposit",
                    description = "Post-migration goal",
                    targetMinor = 500_000L,
                    savedMinor = 50_000L,
                    currency = "LKR",
                    targetDateEpochDay = futureDay1
                )
            ).getOrThrow()
            assertEquals("migrated-goal", created.id)

            val updated = repo.addContribution("migrated-goal", "mig-op-1", 25_000L, "acc-1").getOrThrow()
            assertEquals(75_000L, updated.savedMinor)
            assertEquals(1, db.goalProgressOperationDao.pending("owner").size)
            assertEquals("acc-1", db.goalProgressOperationDao.get("owner", "mig-op-1")?.sourceAccountId)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private class FakeCurrencyPrefs(private val code: String) : CurrencyPreferenceRepository {
        override fun observe(): Flow<CurrencyPreference> = flowOf(CurrencyPreference(code, pendingSync = false))
        override suspend fun set(currency: String): Result<Unit> = Result.success(Unit)
        override suspend fun current(): String = code
    }

    private class FakeGoalRemote : GoalRemoteDataSource {
        var offline = true
        val goals = mutableMapOf<String, FinancialGoal>()
        val appliedOps = mutableMapOf<String, GoalProgressOperation>()
        val forceRejectOperationIds = mutableSetOf<String>()

        override fun observe(uid: String): Flow<List<FinancialGoal>> =
            flowOf(goals.values.filter { it.userId == uid })

        override suspend fun writeDetails(
            goal: FinancialGoal,
            initialSavedMinor: Long
        ): GoalDetailWriteResult {
            check(!offline) { "Offline" }
            val existing = goals[goal.id]
            return if (existing == null) {
                val createdSaved = initialSavedMinor.coerceIn(0L, goal.targetMinor)
                val created = goal.copy(
                    savedMinor = createdSaved,
                    status = GoalPolicy.deriveWriteStatus(createdSaved, goal.targetMinor),
                    pendingSync = false
                )
                goals[goal.id] = created
                GoalDetailWriteResult.Saved(created)
            } else if (existing.updatedAt > goal.updatedAt) {
                GoalDetailWriteResult.Saved(existing)
            } else if (goal.targetMinor < existing.savedMinor) {
                GoalDetailWriteResult.RejectedTargetBelowSaved(existing)
            } else {
                val merged = goal.copy(
                    savedMinor = existing.savedMinor,
                    status = GoalPolicy.deriveWriteStatus(existing.savedMinor, goal.targetMinor),
                    pendingSync = false
                )
                goals[goal.id] = merged
                GoalDetailWriteResult.Saved(merged)
            }
        }

        override suspend fun writeOperation(
            uid: String,
            operation: GoalProgressOperation
        ): GoalOperationWriteResult {
            check(!offline) { "Offline" }
            val current = goals[operation.goalId]
                ?: return GoalOperationWriteResult.Rejected(null, "Goal missing")
            if (forceRejectOperationIds.contains(operation.id)) {
                return GoalOperationWriteResult.Rejected(current, "Forced rejection")
            }
            if (appliedOps.containsKey(operation.id)) {
                return GoalOperationWriteResult.Applied(current)
            }
            val nextSaved = when (operation.type) {
                GoalProgressOperationType.ADD -> current.savedMinor + operation.amountMinor
                GoalProgressOperationType.SET -> operation.amountMinor
            }
            if (nextSaved < 0L || nextSaved > current.targetMinor) {
                return GoalOperationWriteResult.Rejected(current, "Out of bounds")
            }
            val updated = current.copy(
                savedMinor = nextSaved,
                status = GoalPolicy.deriveWriteStatus(nextSaved, current.targetMinor),
                updatedAt = maxOf(current.updatedAt + 1L, operation.createdAt),
                pendingSync = false
            )
            goals[current.id] = updated
            appliedOps[operation.id] = operation.copy(state = GoalProgressOperationState.APPLIED)
            return GoalOperationWriteResult.Applied(updated)
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

    companion object {
        private val V15_CREATE_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `dateEpochMillis` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `accountId` TEXT DEFAULT NULL, `currency` TEXT NOT NULL, `targetCurrency` TEXT NOT NULL, `pendingConversion` INTEGER NOT NULL, `conversion_amountMinor` INTEGER, `conversion_rate` TEXT, `conversion_provider` TEXT, `conversion_rateAt` INTEGER, `conversion_fetchedAt` INTEGER, `conversion_cached` INTEGER, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_transactions_userId` ON `transactions` (`userId`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)",
            "CREATE TABLE IF NOT EXISTS `transaction_tags` (`userId` TEXT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY(`userId`, `name`))",
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, `bankName` TEXT NOT NULL, `accountType` TEXT, `currency` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_accounts_userId` ON `accounts` (`userId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_userId_name` ON `accounts` (`userId`, `name`)",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`source` TEXT NOT NULL, `target` TEXT NOT NULL, `rate` TEXT NOT NULL, `provider` TEXT NOT NULL, `rateAt` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, `nextUpdateAt` INTEGER NOT NULL, PRIMARY KEY(`source`, `target`))",
            "CREATE TABLE IF NOT EXISTS `currency_preferences` (`userId` TEXT NOT NULL, `currency` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`userId`))",
            "CREATE TABLE IF NOT EXISTS `transaction_proofs` (`userId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `verifiedAmountMinor` INTEGER, `pendingUpload` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `unavailable` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`userId`, `transactionId`, `id`))",
            "CREATE TABLE IF NOT EXISTS `income_sources` (`userId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `accountId` TEXT NOT NULL, `recurring` INTEGER NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastConfirmedMonth` TEXT, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`userId`, `id`))",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `month` TEXT NOT NULL, `category` TEXT NOT NULL, `categoryKey` TEXT NOT NULL, `limitMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`userId`, `id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_userId_month_categoryKey` ON `budgets` (`userId`, `month`, `categoryKey`)",
            "CREATE TABLE IF NOT EXISTS `budget_alerts` (`userId` TEXT NOT NULL, `budgetId` TEXT NOT NULL, `level` INTEGER NOT NULL, PRIMARY KEY(`userId`, `budgetId`))",
            "CREATE TABLE IF NOT EXISTS `budget_resolutions` (`userId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `transactionVersion` INTEGER NOT NULL, `currency` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, PRIMARY KEY(`userId`, `transactionId`, `currency`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ba9ad5ceef76b06a389535001a62f022')"
        )

        private val V16_CREATE_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `category` TEXT NOT NULL, `note` TEXT NOT NULL, `dateEpochMillis` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `accountId` TEXT DEFAULT NULL, `currency` TEXT NOT NULL, `targetCurrency` TEXT NOT NULL, `pendingConversion` INTEGER NOT NULL, `conversion_amountMinor` INTEGER, `conversion_rate` TEXT, `conversion_provider` TEXT, `conversion_rateAt` INTEGER, `conversion_fetchedAt` INTEGER, `conversion_cached` INTEGER, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_transactions_userId` ON `transactions` (`userId`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)",
            "CREATE TABLE IF NOT EXISTS `transaction_tags` (`userId` TEXT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY(`userId`, `name`))",
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, `bankName` TEXT NOT NULL, `accountType` TEXT, `currency` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_accounts_userId` ON `accounts` (`userId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_userId_name` ON `accounts` (`userId`, `name`)",
            "CREATE TABLE IF NOT EXISTS `exchange_rates` (`source` TEXT NOT NULL, `target` TEXT NOT NULL, `rate` TEXT NOT NULL, `provider` TEXT NOT NULL, `rateAt` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, `nextUpdateAt` INTEGER NOT NULL, PRIMARY KEY(`source`, `target`))",
            "CREATE TABLE IF NOT EXISTS `currency_preferences` (`userId` TEXT NOT NULL, `currency` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`userId`))",
            "CREATE TABLE IF NOT EXISTS `transaction_proofs` (`userId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `verifiedAmountMinor` INTEGER, `pendingUpload` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `unavailable` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`userId`, `transactionId`, `id`))",
            "CREATE TABLE IF NOT EXISTS `income_sources` (`userId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `accountId` TEXT NOT NULL, `recurring` INTEGER NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastConfirmedMonth` TEXT, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`userId`, `id`))",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `month` TEXT NOT NULL, `category` TEXT NOT NULL, `categoryKey` TEXT NOT NULL, `limitMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`userId`, `id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_userId_month_categoryKey` ON `budgets` (`userId`, `month`, `categoryKey`)",
            "CREATE TABLE IF NOT EXISTS `budget_alerts` (`userId` TEXT NOT NULL, `budgetId` TEXT NOT NULL, `level` INTEGER NOT NULL, PRIMARY KEY(`userId`, `budgetId`))",
            "CREATE TABLE IF NOT EXISTS `budget_resolutions` (`userId` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `transactionVersion` INTEGER NOT NULL, `currency` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, PRIMARY KEY(`userId`, `transactionId`, `currency`))",
            "CREATE TABLE IF NOT EXISTS `goals` (`userId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `targetMinor` INTEGER NOT NULL, `savedMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `targetDateEpochDay` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`userId`, `id`))",
            "CREATE INDEX IF NOT EXISTS `index_goals_userId` ON `goals` (`userId`)",
            "CREATE TABLE IF NOT EXISTS `goal_progress_operations` (`userId` TEXT NOT NULL, `id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`userId`, `id`))",
            "CREATE INDEX IF NOT EXISTS `index_goal_progress_operations_userId_goalId_state` ON `goal_progress_operations` (`userId`, `goalId`, `state`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'eaa4dea1ffb511b2e5abb2885b44b6b8')"
        )
    }
}
