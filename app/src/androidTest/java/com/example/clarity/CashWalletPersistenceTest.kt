package com.example.clarity

import com.example.clarity.di.DatabaseModule

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.repository.CashWalletRepositoryImpl
import com.example.clarity.data.repository.TransactionRepositoryImpl
import com.example.clarity.data.remote.FirestoreDataSource
import com.example.clarity.data.mapper.toDomain
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.User
import com.example.clarity.domain.model.WalletTopUpOption
import com.example.clarity.domain.model.WalletBankAccountInput
import com.example.clarity.data.local.entity.*
import com.example.clarity.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class CashWalletPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "cash-wallet-test"
    private var database: ClarityDatabase? = null
    private val auth = TestAuth()

    private fun open(): ClarityDatabase = DatabaseModule.createDatabase(context, name).also { database = it }
    private val clock = Clock.fixed(Instant.ofEpochMilli(123456789), ZoneOffset.UTC)
    private fun repository(db: ClarityDatabase) = CashWalletRepositoryImpl(db, auth, Dispatchers.IO, clock,
        TestCurrencies.preferences, TestCurrencies.rates)
    @After fun cleanUp() { database?.close(); context.deleteDatabase(name) }

    @Test fun primaryReportingCurrencyDoesNotChangeTheLkrCashLedger() = runBlocking {
        context.deleteDatabase(name)
        val db = open()
        val preferences = object : com.example.clarity.domain.repository.CurrencyPreferenceRepository {
            override fun observe() = kotlinx.coroutines.flow.flowOf(com.example.clarity.domain.repository.CurrencyPreference("USD"))
            override suspend fun set(currency: String) = Result.success(Unit)
            override suspend fun current() = "USD"
        }
        val rates = object : com.example.clarity.domain.repository.ExchangeRateRepository {
            override suspend fun cached(source: String, target: String) = com.example.clarity.domain.model.ExchangeRate(
                source, target, "0.01", "ExchangeRate-API", clock.millis() - 1, clock.millis(), clock.millis() + 1000, true)
            override suspend fun latest(source: String, target: String): com.example.clarity.domain.model.ExchangeRate = error("No network")
        }
        val wallet = CashWalletRepositoryImpl(db, auth, Dispatchers.IO, clock, preferences, rates)
        wallet.recordCash(1000, TransactionType.INCOME, "Cash", "").getOrThrow()
        val entry = db.transactionDao.observeAll("a").first().single()
        assertEquals("LKR", entry.currency)
        assertEquals("USD", entry.targetCurrency)
        assertEquals(10L, entry.conversion!!.amountMinor)
        assertEquals(BigInteger.valueOf(1000), wallet.observeWallet().first().balanceMinor)
        assertTrue(runCatching { db.transactionDao.persistLocal(entry.copy(currency = "USD")) }.isFailure)
        assertEquals("LKR", db.transactionDao.getById(entry.id)!!.currency)
    }

    @Test fun topUpExpenseAndOptionSurviveReopeningAndRemainUserScoped() = runBlocking {
        context.deleteDatabase(name)
        var db = open()
        var repo = repository(db)
        assertTrue(repo.addBankAccount("Daily bank", 10000).isSuccess)
        val bankId = db.cashWalletDao.accounts("a").single().id
        assertTrue(repo.topUp(bankId, 2000).isSuccess)
        assertTrue(repo.configureTopUp(WalletTopUpOption(bankId, true, 500, 1000)).isSuccess)
        assertTrue(repo.recordCash(1700, TransactionType.EXPENSE, "Food", "Lunch").isSuccess)
        assertEquals(BigInteger.valueOf(300), repo.observeWallet().first().balanceMinor)
        assertEquals(BigInteger.valueOf(8000), repo.observeWallet().first().accounts.single().balanceMinor)
        assertEquals(1, db.cashWalletDao.topUps("a").size)
        assertEquals(1, db.transactionDao.getPendingForUser("a").size)
        db.close(); db = open(); repo = repository(db)
        assertEquals(BigInteger.valueOf(300), repo.observeWallet().first().balanceMinor)
        assertTrue(repo.observeWallet().first().topUpOption!!.enabled)
        auth.currentUser.value = User("b", null, null)
        assertTrue(repo.observeWallet().first().activity.isEmpty())
        assertTrue(repo.observeWallet().first().accounts.isEmpty())
        assertTrue(repo.topUp(bankId, 100).isFailure)
        auth.signOut()
        assertTrue(repo.observeWallet().first().activity.isEmpty())
        assertTrue(repo.recordCash(100, TransactionType.INCOME, "Cash", "").isFailure)
    }

    @Test fun reminderNeverCoversAnUnaffordableCashExpense() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.addBankAccount("Small bank", 100).getOrThrow()
        val bankId = db.cashWalletDao.accounts("a").single().id
        repo.configureTopUp(WalletTopUpOption(bankId, true, 500, 1000)).getOrThrow()
        assertTrue(repo.recordCash(200, TransactionType.EXPENSE, "Food", "").isFailure)
        assertTrue(db.cashWalletDao.topUps("a").isEmpty())
        assertTrue(db.transactionDao.observeAll("a").first().isEmpty())
        assertEquals(BigInteger.ZERO, repo.observeWallet().first().balanceMinor)
    }

    @Test fun optedInAutomaticTopUpCommitsWithExpenseAndConservesCombinedFunds() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.saveBankAccount(WalletBankAccountInput("bank", "hnb", "Savings", openingBalanceMinor = 10000)).getOrThrow()
        repo.adjustCashBalance(2000, "Starting cash", "opening").getOrThrow()
        repo.configureTopUp(WalletTopUpOption("bank", true, 500, 1000, automaticExecution = true)).getOrThrow()
        assertTrue(db.cashWalletDao.topUps("a").isEmpty())
        repo.recordCash(1700, TransactionType.EXPENSE, "Food", "Lunch").getOrThrow()
        val wallet = repo.observeWallet().first()
        assertEquals(BigInteger.valueOf(1300), wallet.balanceMinor)
        assertEquals(BigInteger.valueOf(9000), wallet.accounts.single().balanceMinor)
        assertEquals(BigInteger.valueOf(10300), wallet.balanceMinor + wallet.accounts.single().balanceMinor)
        val transfer = db.cashWalletDao.topUps("a").single()
        assertTrue(transfer.automatic)
        assertEquals(clock.millis(), transfer.dateEpochMillis)
        assertTrue(transfer.id.startsWith("auto:"))
        val records = db.transactionDao.observeAll("a").first()
        assertEquals(1, records.size)
        assertEquals("EXPENSE", records.single().type)
        assertEquals(1700L, records.single().amountMinor)
        assertTrue(records.single().pendingSync)
        assertEquals("Automatic top-up", wallet.topUpHistory.single().title)
        assertFalse(com.example.clarity.domain.model.CashWalletRules.needsReminder(wallet.balanceMinor, wallet.topUpOption))
    }

    @Test fun failedAutomaticWithdrawalRollsBackExpenseTagsAndTransfer() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.addBankAccount("Bank", 100).getOrThrow()
        val bank = db.cashWalletDao.accounts("a").single().id
        repo.adjustCashBalance(2000, "Starting", "opening").getOrThrow()
        repo.configureTopUp(WalletTopUpOption(bank, true, 500, 1000, true)).getOrThrow()
        assertTrue(repo.recordCash(1700, TransactionType.EXPENSE, "New tag", "").isFailure)
        assertEquals(BigInteger.valueOf(2000), repo.observeWallet().first().balanceMinor)
        assertTrue(db.cashWalletDao.topUps("a").isEmpty())
        assertTrue(db.transactionDao.getPendingForUser("a").isEmpty())
        assertTrue(db.transactionTagDao.observeTags("a").first().isEmpty())
    }

    @Test fun automaticTopUpCannotOverdrawWalletAndIsIsolatedByOwner() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.addBankAccount("Bank", 10000).getOrThrow()
        val bank = db.cashWalletDao.accounts("a").single().id
        repo.configureTopUp(WalletTopUpOption(bank, true, 500, 1000, true)).getOrThrow()
        assertTrue(repo.recordCash(1500, TransactionType.EXPENSE, "Food", "").isFailure)
        assertTrue(db.cashWalletDao.topUps("a").isEmpty())
        assertEquals(BigInteger.ZERO, repo.observeWallet().first().balanceMinor)
        auth.currentUser.value = User("b", null, null)
        assertTrue(repo.recordCash(1, TransactionType.EXPENSE, "Food", "").isFailure)
        assertTrue(db.cashWalletDao.topUps("a").isEmpty())
        assertTrue(db.cashWalletDao.topUps("b").isEmpty())
    }

    @Test fun cashWritesImmediatelyInvalidateTheUserScopedSyncOutbox() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        assertTrue(db.transactionDao.observePendingForUser("a").first().isEmpty())
        val next = async(Dispatchers.IO) {
            withTimeout(5000) { db.transactionDao.observePendingForUser("a").first { it.isNotEmpty() } }
        }
        repo.recordCash(1000, TransactionType.INCOME, "Cash", "").getOrThrow()
        assertEquals(1000L, next.await().single().amountMinor)
        assertTrue(db.transactionDao.observePendingForUser("b").first().isEmpty())
    }

    @Test fun concurrentAutomaticExpensesCreateOneWithdrawalPerExpense() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.saveBankAccount(WalletBankAccountInput("bank", "hnb", "Savings", openingBalanceMinor = 10000)).getOrThrow()
        repo.configureTopUp(WalletTopUpOption("bank", true, 1000, 500, true)).getOrThrow()
        val results = (1..4).map { async(Dispatchers.IO) { repo.recordCash(100, TransactionType.EXPENSE, "Food", "") } }.awaitAll()
        assertTrue(results.all { it.isSuccess })
        val wallet = repo.observeWallet().first()
        assertEquals(BigInteger.valueOf(1100), wallet.balanceMinor)
        assertEquals(BigInteger.valueOf(8500), wallet.accounts.single().balanceMinor)
        val transfers = db.cashWalletDao.topUps("a")
        assertEquals(3, transfers.size)
        assertEquals(3, transfers.map { it.id }.distinct().size)
        assertTrue(transfers.all { it.automatic })
        assertEquals(4, db.transactionDao.getPendingForUser("a").size)
        db.close()
        assertTrue(repository(open()).observeWallet().first().topUpOption!!.automaticExecution)
    }

    @Test fun editingAndDeletingExistingCashTransactionUpdatesWallet() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.recordCash(1000, TransactionType.INCOME, "Cash", "").getOrThrow()
        val transaction = db.transactionDao.observeAll("a").first().single()
        db.transactionDao.upsert(transaction.copy(amountMinor = 500))
        assertEquals(BigInteger.valueOf(500), repo.observeWallet().first().balanceMinor)
        db.transactionDao.upsert(transaction.copy(isDeleted = true))
        assertEquals(BigInteger.ZERO, repo.observeWallet().first().balanceMinor)
        assertTrue(repo.observeWallet().first().activity.isEmpty())
    }

    @Test fun transactionRepositoryRejectsEditsAndDeletesThatWouldOverdrawWallet() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val wallet = repository(db)
        wallet.recordCash(1000, TransactionType.INCOME, "Cash", "").getOrThrow()
        wallet.recordCash(600, TransactionType.EXPENSE, "Food", "").getOrThrow()
        // An isolated emulator-only Firebase instance prevents all external writes during this test.
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setApplicationId("1:123:android:wallet-test")
            .setProjectId("demo-wallet-test").setApiKey("fake-test-key").build(), "wallet-${java.util.UUID.randomUUID()}")
        val firestore = FirebaseFirestore.getInstance(app).apply { useEmulator("127.0.0.1", 1) }
        try {
            val transactions = TransactionRepositoryImpl(db.transactionDao, FirestoreDataSource(firestore), auth,
                Dispatchers.Unconfined, object : com.example.clarity.data.sync.AccountUploader {
                    override suspend fun uploadPending(uid: String, afterAccounts: suspend () -> Unit): Boolean { afterAccounts(); return true }
                }, this, TestCurrencies.rates, TestCurrencies.preferences, TestCurrencies.scheduler(), clock)
            val rows = db.transactionDao.observeAll("a").first()
            val income = rows.single { it.type == "INCOME" }.toDomain()
            val expense = rows.single { it.type == "EXPENSE" }.toDomain()
            assertTrue(transactions.updateTransaction(income.copy(amountMinor = 500)).isFailure)
            assertTrue(transactions.deleteTransaction(income.id).isFailure)
            assertEquals(1000L, db.transactionDao.getById(income.id)!!.amountMinor)
            assertFalse(db.transactionDao.getById(income.id)!!.isDeleted)
            assertEquals(BigInteger.valueOf(400), wallet.observeWallet().first().balanceMinor)
            transactions.updateTransaction(expense.copy(amountMinor = 700)).getOrThrow()
            assertEquals(BigInteger.valueOf(300), wallet.observeWallet().first().balanceMinor)
            transactions.deleteTransaction(expense.id).getOrThrow()
            assertEquals(BigInteger.valueOf(1000), wallet.observeWallet().first().balanceMinor)
            transactions.deleteTransaction(income.id).getOrThrow()
            assertEquals(BigInteger.ZERO, wallet.observeWallet().first().balanceMinor)
            transactions.stopSync().join()
        } finally {
            auth.signOut()
            firestore.terminate()
        }
    }

    @Test fun confirmedTransferIsIdempotentAndConservesTotal() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.saveBankAccount(WalletBankAccountInput("bank", "commercial", "Personal Savings", openingBalanceMinor = 5000000)).getOrThrow()
        repo.adjustCashBalance(200000, "Starting cash", "opening").getOrThrow()
        val results = (1..8).map { async(Dispatchers.IO) { repo.topUp("bank", 500000, "one-confirmation") } }.awaitAll()
        assertTrue(results.all { it.isSuccess })
        val wallet = repo.observeWallet().first()
        assertEquals(BigInteger.valueOf(700000), wallet.balanceMinor)
        assertEquals(BigInteger.valueOf(4500000), wallet.accounts.single().balanceMinor)
        assertEquals(BigInteger.valueOf(5200000), wallet.balanceMinor + wallet.accounts.single().balanceMinor)
        assertEquals(1, db.cashWalletDao.topUps("a").size)
        assertEquals(clock.millis(), db.cashWalletDao.topUps("a").single().dateEpochMillis)
        assertTrue(db.transactionDao.observeAll("a").first().isEmpty())
        assertTrue(repo.topUp("bank", 400000, "one-confirmation").isFailure)
    }

    @Test fun distinctConcurrentTransfersCannotOverdrawBank() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.saveBankAccount(WalletBankAccountInput("bank", "hnb", "Savings", openingBalanceMinor = 1000)).getOrThrow()
        val results = listOf("one", "two").map { id -> async(Dispatchers.IO) { repo.topUp("bank", 800, id) } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(BigInteger.valueOf(200), repo.observeWallet().first().accounts.single().balanceMinor)
        assertEquals(BigInteger.valueOf(800), repo.observeWallet().first().balanceMinor)
    }

    @Test fun observedReadSnapshotsStayConsistentDuringConcurrentTransfers() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.saveBankAccount(WalletBankAccountInput("bank", "hnb", "Savings", openingBalanceMinor = 10000)).getOrThrow()
        var snapshots = 0
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val observer = launch(Dispatchers.IO) {
            repo.observeWallet().collect { wallet ->
                assertEquals(BigInteger.valueOf(10000), wallet.balanceMinor + wallet.accounts.single().balanceMinor)
                assertTrue(wallet.topUpHistory.size <= 50)
                assertTrue(wallet.activity.size <= 50)
                snapshots++
                started.complete(Unit)
            }
        }
        try {
            withTimeout(5000) { started.await() }
            (1..60).map { id -> async(Dispatchers.IO) { repo.topUp("bank", 10, "snapshot-$id").getOrThrow() } }.awaitAll()
            withTimeout(5000) { repo.observeWallet().first { it.balanceMinor == BigInteger.valueOf(600) } }
        } finally { observer.cancel(); observer.join() }
        assertTrue(snapshots > 0)
    }

    @Test fun overflowAfterTransferInsertionRollsBackBothBalancesAndHistory() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.addBankAccount("Bank", 1000).getOrThrow()
        val bank = db.cashWalletDao.accounts("a").single().id
        repo.adjustCashBalance(Long.MAX_VALUE, "Starting", "opening").getOrThrow()
        assertTrue(repo.topUp(bank, 1, "overflow").isFailure)
        assertTrue(db.cashWalletDao.topUps("a").isEmpty())
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), repo.observeWallet().first().balanceMinor)
        assertEquals(BigInteger.valueOf(1000), repo.observeWallet().first().accounts.single().balanceMinor)
        assertTrue(repo.recordCash(1, TransactionType.INCOME, "Cash", "").isFailure)
        assertTrue(db.transactionDao.observeAll("a").first().isEmpty())
    }

    @Test fun multipleAccountsDefaultsArchivingAndOwnershipKeepHistorySafe() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        repo.saveBankAccount(WalletBankAccountInput("one", "commercial", "Personal", "1234", openingBalanceMinor = 1000)).getOrThrow()
        repo.saveBankAccount(WalletBankAccountInput("two", "commercial", "Business", openingBalanceMinor = 2000, isDefault = true)).getOrThrow()
        assertEquals("two", db.cashWalletDao.accounts("a").single { it.isDefault }.id)
        repo.topUp("one", 500, "history").getOrThrow()
        repo.configureTopUp(WalletTopUpOption("one", true, 100, 500)).getOrThrow()
        repo.archiveBankAccount("one").getOrThrow()
        assertTrue(repo.topUp("one", 1, "archived").isFailure)
        assertFalse(repo.observeWallet().first().topUpOption!!.enabled)
        assertEquals(1, repo.bankActivity("one").getOrThrow().size)
        assertEquals(BigInteger.valueOf(500), repo.observeWallet().first().accounts.first { it.id == "one" }.balanceMinor)
        auth.currentUser.value = User("b", null, null)
        assertTrue(repo.topUp("two", 1, "other-user").isFailure)
        assertTrue(repo.bankActivity("one").isFailure)
        assertTrue(repo.archiveBankAccount("one").isFailure)
    }

    @Test fun sqlAggregatesLargeHistoryExactlyAndBoundsActivity() = runBlocking {
        context.deleteDatabase(name)
        val db = open(); val repo = repository(db)
        db.withTransaction {
            val rows = (1..5000).map { id -> TransactionEntity("cash-$id", "a", "INCOME", 12345, "Cash", "", id.toLong(), 1, 1) }
            db.transactionDao.upsertAll(rows)
            rows.forEach { db.cashWalletDao.insertCashLink(WalletCashLinkEntity("a", it.id)) }
        }
        val wallet = repo.observeWallet().first()
        assertEquals(BigInteger.valueOf(61725000), wallet.balanceMinor)
        assertEquals(50, wallet.activity.size)
        assertEquals(5000L, wallet.activity.first().dateEpochMillis)
        db.transactionDao.upsert(TransactionEntity("huge", "a", "INCOME", Long.MAX_VALUE, "Cash", "", 1, 1, 1))
        db.cashWalletDao.insertCashLink(WalletCashLinkEntity("a", "huge"))
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) + BigInteger.valueOf(61725000), db.cashWalletDao.balance("a").value())
    }

    private class TestAuth : AuthRepository {
        override val currentUser = MutableStateFlow<User?>(User("a", null, null))
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
        override suspend fun signOut() { currentUser.value = null }
    }
}
