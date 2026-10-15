package com.example.clarity

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "account-migration-test"
    private var database: ClarityDatabase? = null

    @After
    fun tearDown() {
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
        dao.save(row("daily"), create = true)
        dao.save(row("savings"), create = true)
        dao.save(row("private", "user-b"), create = true)
        (1..20).map { index -> async(Dispatchers.IO) {
            dao.setDefault(if (index % 2 == 0) "daily" else "savings", "user-a")
        } }.awaitAll()
        assertEquals(1, dao.observeAll("user-a").first().count { it.isDefault })
        assertEquals("private", dao.getDefault("user-b")!!.id)
        val before = dao.getAllForUser("user-a")
        val other = before.first { !it.isDefault }
        try {
            dao.save(other.copy(name = before.first { it.isDefault }.name, isDefault = true), create = false)
            fail("Duplicate name must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, dao.getAllForUser("user-a"))
        try {
            dao.save(before.first { it.isDefault }.copy(isDefault = false), create = false)
            fail("Clearing the sole default must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, dao.getAllForUser("user-a"))
    }

    @Test
    fun versionThreeMigrationPreservesTransactionsAndAddsPersistentAccounts() = runBlocking {
        createLegacyDatabase(3)
        var db = openDatabase()
        assertEquals("Original description", db.transactionDao.getById("legacy")!!.note)
        assertTrue(db.transactionDao.getById("legacy")!!.pendingSync)
        assertEquals(listOf("Hobby"), db.transactionTagDao.observeTags("user-a").first())
        assertSamplePreserved(db)
        db.accountDao.save(row("daily"), create = true)
        db.accountDao.save(row("savings"), create = true)
        db.accountDao.setDefault("savings", "user-a")
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
            db.accountDao.save(row("daily"), create = true)
            assertEquals("daily", db.accountDao.getDefault("user-a")!!.id)
            db.close()
        }
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
        db.accountDao.reconcileRemote("user-a", listOf(daily), authoritative = true)
        assertEquals(linked, db.accountDao.getById("linked"))
        assertNull(db.accountDao.getById("unused"))
        assertNotNull(db.accountDao.getById("other"))
        db.transactionDao.deleteById("tx", "user-a")
        db.accountDao.reconcileRemote("user-a", listOf(daily), authoritative = true)
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
        .addMigrations(InitialTransactionMigration, TransactionTagMigration, AccountMigration, TransactionAccountMigration, TransactionAccountIndexMigration)
        .build().also { database = it }

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
