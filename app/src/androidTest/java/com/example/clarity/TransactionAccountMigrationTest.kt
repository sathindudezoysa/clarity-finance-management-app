package com.example.clarity

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.TransactionAccountMigration
import com.example.clarity.data.local.TransactionAccountIndexMigration
import com.example.clarity.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionAccountMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "transaction-account-migration-test"
    private var database: ClarityDatabase? = null

    @After
    fun tearDown() { database?.close(); context.deleteDatabase(databaseName) }

    @Test
    fun migrationSelectsAccountsDeterministicallyPerOwnerAndPreservesExistingData() = runBlocking {
        context.deleteDatabase(databaseName)
        val path = context.getDatabasePath(databaseName)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
            legacy.execSQL("INSERT INTO sample_table VALUES ('sample', 'Keep this', 'Original')")
            legacy.execSQL("""
                CREATE TABLE transactions (
                    id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, type TEXT NOT NULL,
                    amountMinor INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL,
                    dateEpochMillis INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    pendingSync INTEGER NOT NULL, isDeleted INTEGER NOT NULL
                )
            """.trimIndent())
            legacy.execSQL("CREATE INDEX index_transactions_userId ON transactions (userId)")
            legacy.execSQL("CREATE TABLE transaction_tags (userId TEXT NOT NULL, name TEXT COLLATE NOCASE NOT NULL, PRIMARY KEY(userId, name))")
            legacy.execSQL("INSERT INTO transaction_tags VALUES ('user-a', 'Food')")
            legacy.execSQL("""
                CREATE TABLE accounts (
                    id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, name TEXT COLLATE NOCASE NOT NULL,
                    bankName TEXT NOT NULL, accountType TEXT, currency TEXT NOT NULL, isDefault INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, pendingSync INTEGER NOT NULL
                )
            """.trimIndent())
            legacy.execSQL("CREATE INDEX index_accounts_userId ON accounts (userId)")
            legacy.execSQL("CREATE UNIQUE INDEX index_accounts_userId_name ON accounts (userId, name)")
            // Insert defaults in reverse preference order to exercise both timestamp and id ties.
            legacy.execSQL("INSERT INTO accounts VALUES ('later-a', 'user-a', 'Later', 'Bank', NULL, 'LKR', 1, 30, 30, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('z-default-a', 'user-a', 'Tied', 'Bank', NULL, 'LKR', 1, 20, 30, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('daily-a', 'user-a', 'Daily', 'Bank', NULL, 'LKR', 1, 20, 30, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('savings-a', 'user-a', 'Savings', 'Bank', NULL, 'LKR', 0, 1, 10, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('daily-b', 'user-b', 'Daily', 'Bank', NULL, 'USD', 1, 1, 10, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('later', 'no-default', 'Later', 'Bank', NULL, 'LKR', 0, 20, 30, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('b-first', 'no-default', 'Tied', 'Bank', NULL, 'LKR', 0, 10, 30, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('a-first', 'no-default', 'First', 'Bank', NULL, 'LKR', 0, 10, 30, 0)")
            for (user in listOf("user-a", "user-b", "no-default", "no-accounts")) {
                legacy.execSQL("INSERT INTO transactions VALUES ('$user', '$user', 'EXPENSE', 1250, 'Food', 'Original', 100, 1, 10, 0, 0)")
            }
            legacy.execSQL("INSERT INTO transactions VALUES ('deleted', 'user-a', 'EXPENSE', 1250, 'Food', 'Keep tombstone', 100, 1, 10, 1, 1)")
            legacy.execSQL("INSERT INTO transactions VALUES ('saturated', 'user-a', 'EXPENSE', 1250, 'Food', '', 100, 1, 9223372036854775807, 0, 0)")
            legacy.version = 4
        }
        var db = open()
        val userA = db.transactionDao.getById("user-a")!!
        assertEquals("daily-a", userA.accountId)
        assertEquals("daily-b", db.transactionDao.getById("user-b")!!.accountId)
        assertEquals("a-first", db.transactionDao.getById("no-default")!!.accountId)
        assertTrue(userA.pendingSync)
        assertEquals(11L, userA.updatedAt)
        assertEquals(11L, db.transactionDao.getById("user-b")!!.updatedAt)
        assertEquals(11L, db.transactionDao.getById("no-default")!!.updatedAt)
        val saturated = db.transactionDao.getById("saturated")!!
        assertEquals(Long.MAX_VALUE, saturated.updatedAt)
        assertEquals("daily-a", saturated.accountId)
        assertTrue(saturated.pendingSync)
        assertEquals(1L, userA.createdAt)
        assertEquals("Original", userA.note)
        val noAccount = db.transactionDao.getById("no-accounts")!!
        assertNull(noAccount.accountId)
        assertFalse(noAccount.pendingSync)
        assertEquals(10L, noAccount.updatedAt)
        assertEquals("daily-a", db.accountDao.getById("daily-a")!!.id)
        val tombstone = db.transactionDao.getById("deleted")!!
        assertEquals("daily-a", tombstone.accountId)
        assertTrue(tombstone.isDeleted)
        assertTrue(tombstone.pendingSync)
        assertEquals(11L, tombstone.updatedAt)
        assertEquals("Keep tombstone", tombstone.note)
        assertEquals(listOf("Food"), db.transactionTagDao.observeTags("user-a").first())
        db.openHelper.readableDatabase.query("SELECT title FROM sample_table WHERE id = 'sample'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Keep this", it.getString(0))
        }
        val switched = userA.copy(accountId = "savings-a")
        db.transactionDao.upsertWithTag(switched)
        db.close()
        db = open()
        assertEquals(switched, db.transactionDao.observeById("user-a", "user-a").first())
        assertEquals(6, listOf("user-a", "user-b", "no-default", "no-accounts", "deleted", "saturated").mapNotNull { db.transactionDao.getById(it) }.size)
        assertEquals(tombstone, db.transactionDao.getById("deleted"))
    }

    private fun open() = Room.databaseBuilder(context, ClarityDatabase::class.java, databaseName)
        .addMigrations(*com.example.clarity.data.local.IntegratedMigrations.all).build().also { database = it }
}
