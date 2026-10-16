package com.example.clarity

import com.example.clarity.data.local.ProofRecoveryMigration
import com.example.clarity.data.local.TransactionProofMigration
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.AccountMigration
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.InitialTransactionMigration
import com.example.clarity.data.local.TransactionTagMigration
import com.example.clarity.data.local.TransactionAccountMigration
import com.example.clarity.data.local.TransactionAccountIndexMigration
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.local.entity.AccountEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.remote.AccountRemoteDataSource
import com.example.clarity.data.remote.AccountSnapshot
import com.example.clarity.data.remote.FirestoreDataSource
import com.example.clarity.data.repository.AccountRepositoryImpl
import com.example.clarity.data.repository.TransactionRepositoryImpl
import com.example.clarity.data.sync.AccountUploader
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.util.Resource
import com.example.clarity.domain.util.isSignInRequired
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "account-migration-test"
    private var database: ClarityDatabase? = null
    private val repositories = mutableListOf<AccountRepositoryImpl>()
    private val transactionRepositories = mutableListOf<TransactionRepositoryImpl>()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        runBlocking { repositories.forEach { it.stopSync().join() } }
        runBlocking { transactionRepositories.forEach { it.stopSync().join() } }
        applicationScope.cancel()
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun row(id: String, user: String = "user-a", default: Boolean = false) =
        AccountEntity(id, user, id, "Bank", null, "LKR", default, 1, 1)

    @Test
    fun concurrentDefaultSwitchesAndInvalidUpdatesAreAtomicInRoom() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        database = db
        val dao = db.accountDao
        val repository = repository(db)
        repository.createAccount(row("daily").toDomain()).getOrThrow()
        repository.createAccount(row("savings").toDomain()).getOrThrow()
        repository(db, "user-b").createAccount(row("private", "user-b").toDomain()).getOrThrow()
        (1..20).map { index -> async(Dispatchers.IO) {
            repository.setDefaultAccount(if (index % 2 == 0) "daily" else "savings").getOrThrow()
        } }.awaitAll()
        assertEquals(1, dao.observeAll("user-a").first().count { it.isDefault })
        assertEquals("private", dao.getDefault("user-b")!!.id)
        val before = dao.getAllForUser("user-a")
        val other = before.first { !it.isDefault }
        try {
            repository.updateAccount(other.copy(name = before.first { it.isDefault }.name, isDefault = true).toDomain()).getOrThrow()
            fail("Duplicate name must be rejected")
        } catch (error: AccountException) { assertEquals(AccountError.NAME_DUPLICATE, error.error) }
        assertEquals(before, dao.getAllForUser("user-a"))
        try {
            repository.updateAccount(before.first { it.isDefault }.copy(isDefault = false).toDomain()).getOrThrow()
            fail("Clearing the sole default must be rejected")
        } catch (error: AccountException) { assertEquals(AccountError.DEFAULT_REQUIRED, error.error) }
        assertEquals(before, dao.getAllForUser("user-a"))
    }

    @Test
    fun independentRepositoriesValidateConcurrentCreatesInsideTheRoomTransaction() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        database = db
        val first = repository(db)
        val second = repository(db)
        val results = listOf(
            async(Dispatchers.IO) { first.createAccount(row("first").copy(name = "Daily").toDomain()) },
            async(Dispatchers.IO) { second.createAccount(row("second").copy(name = "daily").toDomain()) }
        ).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        val failure = results.single { it.isFailure }.exceptionOrNull()
        assertEquals(AccountError.NAME_DUPLICATE, (failure as AccountException).error)
        assertEquals(1, db.accountDao.getAllForUser("user-a").size)
        assertEquals(1, db.accountDao.getAllForUser("user-a").count { it.isDefault })
    }

    @Test
    fun signedOutTransactionRepositoryEmitsTypedAuthenticationErrorsWithoutStartingRemoteWork() = runBlocking {
        val db = openDatabase()
        val repository = TransactionRepositoryImpl(db.transactionDao, FirestoreDataSource(FirebaseFirestore.getInstance()),
            object : AuthRepository {
                override val currentUser = flowOf<User?>(null)
                override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
                override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
                override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
                override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
                override suspend fun signOut() = Unit
            }, Dispatchers.IO, object : AccountUploader {
                override suspend fun uploadPending(uid: String, afterAccounts: suspend () -> Unit): Boolean =
                    error("Signed-out users must not upload")
            }, applicationScope).also { transactionRepositories.add(it) }
        val list = withTimeout(10_000) {
            repository.observeTransactions().first { it is Resource.Error } as Resource.Error
        }
        val detail = withTimeout(10_000) {
            repository.observeTransaction("transaction").first { it is Resource.Error } as Resource.Error
        }
        assertTrue(list.cause.isSignInRequired())
        assertTrue(detail.cause.isSignInRequired())
        assertEquals("Please sign in", list.message)
        assertEquals("Please sign in", detail.message)
        assertTrue(repository.deleteTransaction("transaction").exceptionOrNull().isSignInRequired())
    }

    @Test
    fun offlineCreationAndDefaultSwitchSurviveRoomReopenAndUploadTogether() = runBlocking {
        val remote = object : AccountRemoteDataSource {
            @Volatile var online = false
            val committed = mutableListOf<List<Account>>()
            override fun observeAccounts(userId: String) = emptyFlow<AccountSnapshot>()
            override suspend fun writeAccounts(userId: String, accounts: List<Account>) {
                check(online) { "Offline test" }
                assertEquals(1, accounts.count { it.isDefault })
                committed.add(accounts)
            }
        }
        var db = openDatabase()
        val original = repository(db, remote = remote)
        withTimeout(10_000) {
            original.createAccount(row("daily").toDomain()).getOrThrow()
            original.createAccount(row("savings").toDomain()).getOrThrow()
            original.setDefaultAccount("savings").getOrThrow()
        }
        val pending = db.accountDao.getAllForUser("user-a")
        assertTrue(pending.all { it.pendingSync })
        assertEquals(listOf("savings"), pending.filter { it.isDefault }.map { it.id })
        assertTrue(remote.committed.isEmpty())
        original.stopSync().join()
        db.close()

        db = openDatabase()
        assertEquals(pending.associateBy { it.id }, db.accountDao.getAllForUser("user-a").associateBy { it.id })
        val restarted = repository(db, remote = remote)
        remote.online = true
        assertTrue(withTimeout(10_000) { restarted.uploadPending("user-a") })
        val synchronized = db.accountDao.getAllForUser("user-a")
        assertEquals(pending.map { it.copy(pendingSync = false) }.associateBy { it.id }, synchronized.associateBy { it.id })
        assertEquals(setOf("daily", "savings"), remote.committed.single().map { it.id }.toSet())
        assertEquals(listOf("savings"), remote.committed.single().filter { it.isDefault }.map { it.id })
    }

    @Test
    fun versionThreeMigrationPreservesTransactionsAndAddsPersistentAccounts() = runBlocking {
        createLegacyDatabase(3)
        var db = openDatabase()
        assertEquals("Original description", db.transactionDao.getById("legacy")!!.note)
        assertTrue(db.transactionDao.getById("legacy")!!.pendingSync)
        assertEquals(listOf("Hobby"), db.transactionTagDao.observeTags("user-a").first())
        assertSamplePreserved(db)
        db.accountDao.upsertAll(listOf(row("daily").copy(pendingSync = true),
            row("savings", default = true).copy(pendingSync = true)))
        db.close()
        db = openDatabase()
        assertEquals(2, db.accountDao.observeAll("user-a").first().size)
        assertEquals("savings", db.accountDao.getDefault("user-a")!!.id)
        assertTrue(db.accountDao.getAllForUser("user-a").all { it.pendingSync })
    }

    @Test
    fun versionsOneAndTwoMigrateWithoutDestructiveFallback() = runBlocking {
        for (version in listOf(1, 2)) {
            createLegacyDatabase(version)
            val db = openDatabase()
            assertSamplePreserved(db)
            if (version == 2) assertEquals("Original description", db.transactionDao.getById("legacy")!!.note)
            db.accountDao.upsertAll(listOf(row("daily", default = true).copy(pendingSync = true)))
            assertEquals("daily", db.accountDao.getDefault("user-a")!!.id)
            db.close()
        }
    }

    @Test
    fun remoteRenameKeepsBothRoomRowsAndTheirTransactionReferences() = runBlocking {
        val db = openDatabase()
        val repository = repository(db)
        repository.createAccount(row("daily").toDomain()).getOrThrow()
        repository.createAccount(row("savings").toDomain()).getOrThrow()
        repository.acknowledgeBatch(db.accountDao.getAllForUser("user-a"))
        val local = db.accountDao.getAllForUser("user-a")
        db.transactionDao.upsert(TransactionEntity("linked", "user-a", "EXPENSE", 100, "Food", "", 1, 1, 1,
            accountId = "savings"))
        repository.reconcileRemote("user-a", local.map {
            if (it.id == "savings") it.copy(name = "DAILY", updatedAt = it.updatedAt + 1) else it
        }, authoritative = true)
        assertEquals(local, db.accountDao.getAllForUser("user-a"))
        assertNotNull(db.accountDao.getById(db.transactionDao.getById("linked")!!.accountId!!))
    }

    @Test
    fun reconciliationRetainsAccountsUntilTheirLastTransactionIsRemoved() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        database = db
        val daily = row("daily", default = true)
        val linked = row("linked")
        db.accountDao.upsertAll(listOf(daily, linked, row("unused"), row("other", "user-b", true)))
        val transaction = TransactionEntity("tx", "user-a", "EXPENSE", 100, "Food", "", 1, 1, 1,
            pendingSync = true, isDeleted = true, accountId = "linked")
        db.transactionDao.upsert(transaction)
        val repository = repository(db)
        repository.reconcileRemote("user-a", listOf(daily), authoritative = true)
        assertEquals(linked, db.accountDao.getById("linked"))
        assertNull(db.accountDao.getById("unused"))
        assertNotNull(db.accountDao.getById("other"))
        db.transactionDao.deleteById("tx", "user-a")
        repository.reconcileRemote("user-a", listOf(daily), authoritative = true)
        assertNull(db.accountDao.getById("linked"))
    }

    @Test
    fun versionFiveMigrationAddsAccountIndexAndPreservesAllStoredData() = runBlocking {
        createLegacyDatabase(5)
        val db = openDatabase()
        val transaction = db.transactionDao.getById("legacy")!!
        assertEquals("Original description", transaction.note)
        assertEquals("daily", transaction.accountId)
        assertTrue(transaction.pendingSync)
        assertTrue(transaction.isDeleted)
        assertEquals(10L, transaction.updatedAt)
        assertEquals("daily", db.accountDao.getDefault("user-a")!!.id)
        assertEquals(listOf("Hobby"), db.transactionTagDao.observeTags("user-a").first())
        assertSamplePreserved(db)
        db.openHelper.readableDatabase.query("PRAGMA index_info('index_transactions_accountId')").use {
            assertTrue(it.moveToFirst())
            assertEquals("accountId", it.getString(2))
            assertFalse(it.moveToNext())
        }
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_list('transactions')").use {
            assertFalse(it.moveToFirst())
        }
        db.close()
        assertEquals(transaction, openDatabase().transactionDao.getById("legacy"))
    }

    private fun openDatabase() = Room.databaseBuilder(context, ClarityDatabase::class.java, databaseName)
        .addMigrations(InitialTransactionMigration, TransactionTagMigration, AccountMigration, TransactionAccountMigration, TransactionAccountIndexMigration, TransactionProofMigration(), ProofRecoveryMigration)
        .build().also { database = it }

    private fun repository(db: ClarityDatabase, userId: String = "user-a",
        remote: AccountRemoteDataSource = object : AccountRemoteDataSource {
            override fun observeAccounts(userId: String) = emptyFlow<AccountSnapshot>()
            override suspend fun writeAccounts(userId: String, accounts: List<Account>) { error("Offline test") }
        }
    ) = AccountRepositoryImpl(
        db,
        remote,
        object : AuthRepository {
            override val currentUser = flowOf(User(userId, null, null))
            override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
            override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
            override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
            override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
            override suspend fun signOut() = Unit
        },
        Dispatchers.IO, applicationScope
    ).also { repositories.add(it) }

    private fun assertSamplePreserved(db: ClarityDatabase) {
        db.openHelper.readableDatabase.query("SELECT title FROM sample_table WHERE id = 'sample'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Keep this", it.getString(0))
        }
    }

    private fun createLegacyDatabase(version: Int) {
        context.deleteDatabase(databaseName)
        val path = context.getDatabasePath(databaseName)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
            legacy.execSQL("INSERT INTO sample_table VALUES ('sample', 'Keep this', '')")
            if (version >= 2) {
                legacy.execSQL("""
                    CREATE TABLE transactions (
                        id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, type TEXT NOT NULL,
                        amountMinor INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL,
                        dateEpochMillis INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                        pendingSync INTEGER NOT NULL, isDeleted INTEGER NOT NULL
                    )
                """.trimIndent())
                legacy.execSQL("CREATE INDEX index_transactions_userId ON transactions (userId)")
                legacy.execSQL("INSERT INTO transactions VALUES ('legacy', 'user-a', 'EXPENSE', 1250, 'Hobby', 'Original description', 100, 1, 10, 1, 0)")
            }
            if (version >= 3) {
                legacy.execSQL("CREATE TABLE transaction_tags (userId TEXT NOT NULL, name TEXT COLLATE NOCASE NOT NULL, PRIMARY KEY(userId, name))")
                legacy.execSQL("INSERT INTO transaction_tags VALUES ('user-a', 'Hobby')")
            }
            if (version >= 4) {
                legacy.execSQL("""
                    CREATE TABLE accounts (
                        id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, name TEXT COLLATE NOCASE NOT NULL,
                        bankName TEXT NOT NULL, accountType TEXT, currency TEXT NOT NULL, isDefault INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, pendingSync INTEGER NOT NULL
                    )
                """.trimIndent())
                legacy.execSQL("CREATE INDEX index_accounts_userId ON accounts (userId)")
                legacy.execSQL("CREATE UNIQUE INDEX index_accounts_userId_name ON accounts (userId, name)")
                legacy.execSQL("INSERT INTO accounts VALUES ('daily', 'user-a', 'Daily', 'Bank', NULL, 'LKR', 1, 1, 10, 0)")
            }
            if (version >= 5) {
                legacy.execSQL("ALTER TABLE transactions ADD COLUMN accountId TEXT DEFAULT NULL")
                legacy.execSQL("UPDATE transactions SET accountId = 'daily', isDeleted = 1")
            }
            legacy.version = version
        }
    }
}
