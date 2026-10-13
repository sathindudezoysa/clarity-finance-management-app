package com.example.clarity

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.CashWalletMigration
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.repository.CashWalletRepositoryImpl
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.User
import com.example.clarity.domain.model.WalletTopUpOption
import com.example.clarity.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger

@RunWith(AndroidJUnit4::class)
class CashWalletPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "cash-wallet-test"
    private var database: ClarityDatabase? = null
    private val auth = TestAuth()

    private fun open(): ClarityDatabase = Room.databaseBuilder(context, ClarityDatabase::class.java, name)
        .addMigrations(CashWalletMigration).build().also { database = it }
    private fun repository(db: ClarityDatabase) = CashWalletRepositoryImpl(db, auth, Dispatchers.IO)
    @After fun cleanUp() { database?.close(); context.deleteDatabase(name) }

    @Test fun topUpExpenseAndOptionSurviveReopeningAndRemainUserScoped() = runBlocking {
        context.deleteDatabase(name)
        var db = open()
        var repo = repository(db)
        assertTrue(repo.addBankAccount("Daily bank", 10000).isSuccess)
        val bankId = db.cashWalletDao.accounts("a").single().id
        assertTrue(repo.topUp(bankId, 2000).isSuccess)
        assertTrue(repo.configureTopUp(WalletTopUpOption(bankId, true, 500, 1000)).isSuccess)
        assertTrue(repo.recordCash(1700, TransactionType.EXPENSE, "Food", "Lunch").isSuccess)
        assertEquals(BigInteger.valueOf(1300), repo.observeWallet().first().balanceMinor)
        assertEquals(BigInteger.valueOf(7000), repo.observeWallet().first().accounts.single().balanceMinor)
        assertEquals(2, db.cashWalletDao.topUps("a").size)
        assertEquals(1, db.transactionDao.getPendingForUser("a").size)
        db.close(); db = open(); repo = repository(db)
        assertEquals(BigInteger.valueOf(1300), repo.observeWallet().first().balanceMinor)
        assertTrue(repo.observeWallet().first().topUpOption!!.enabled)
        auth.currentUser.value = User("b", null, null)
        assertTrue(repo.observeWallet().first().activity.isEmpty())
        assertTrue(repo.observeWallet().first().accounts.isEmpty())
        assertTrue(repo.topUp(bankId, 100).isFailure)
        auth.signOut()
        assertTrue(repo.observeWallet().first().activity.isEmpty())
        assertTrue(repo.recordCash(100, TransactionType.INCOME, "Cash", "").isFailure)
    }

    @Test fun failedAutomaticWithdrawalRollsBackEntireCashSave() = runBlocking {
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

    @Test fun migrationPreservesVersionThreeTransactionsAndTags() = runBlocking {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
            legacy.execSQL("CREATE TABLE transactions (id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, type TEXT NOT NULL, amountMinor INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, dateEpochMillis INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, pendingSync INTEGER NOT NULL, isDeleted INTEGER NOT NULL)")
            legacy.execSQL("CREATE INDEX index_transactions_userId ON transactions(userId)")
            legacy.execSQL("CREATE TABLE transaction_tags (userId TEXT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY(userId, name))")
            legacy.execSQL("INSERT INTO transactions VALUES ('old', 'a', 'EXPENSE', 500, 'Food', 'Keep', 1, 1, 1, 1, 0)")
            legacy.execSQL("INSERT INTO transaction_tags VALUES ('a', 'Food')")
            legacy.version = 3
        }
        val db = open()
        assertEquals("Keep", db.transactionDao.getById("old")!!.note)
        assertEquals(listOf("Food"), db.transactionTagDao.observeTags("a").first())
        assertTrue(repository(db).observeWallet().first().activity.isEmpty())
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
