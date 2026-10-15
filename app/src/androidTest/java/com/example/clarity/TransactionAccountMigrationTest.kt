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
    fun migrationBackfillsOnlyTheOwnersDefaultAndRetainsLegacyRowsWithoutAccounts() = runBlocking {
        context.deleteDatabase(databaseName)
        val path = context.getDatabasePath(databaseName)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL("CREATE TABLE sample_table (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL)")
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
            legacy.execSQL("""
                CREATE TABLE accounts (
                    id TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, name TEXT COLLATE NOCASE NOT NULL,
                    bankName TEXT NOT NULL, accountType TEXT, currency TEXT NOT NULL, isDefault INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, pendingSync INTEGER NOT NULL
                )
            """.trimIndent())
            legacy.execSQL("CREATE INDEX index_accounts_userId ON accounts (userId)")
            legacy.execSQL("CREATE UNIQUE INDEX index_accounts_userId_name ON accounts (userId, name)")
            legacy.execSQL("INSERT INTO accounts VALUES ('daily-a', 'user-a', 'Daily', 'Bank', NULL, 'LKR', 1, 1, 10, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('savings-a', 'user-a', 'Savings', 'Bank', NULL, 'LKR', 0, 1, 10, 0)")
            legacy.execSQL("INSERT INTO accounts VALUES ('daily-b', 'user-b', 'Daily', 'Bank', NULL, 'USD', 1, 1, 10, 0)")
            for (user in listOf("user-a", "user-b", "no-accounts")) {
                legacy.execSQL("INSERT INTO transactions VALUES ('$user', '$user', 'EXPENSE', 1250, 'Food', 'Original', 100, 1, 10, 0, 0)")
            }
            legacy.version = 4
        }
        var db = open()
        val userA = db.transactionDao.getById("user-a")!!
        assertEquals("daily-a", userA.accountId)
        assertEquals("daily-b", db.transactionDao.getById("user-b")!!.accountId)
        assertTrue(userA.pendingSync)
        assertTrue(userA.updatedAt > 10)
        assertEquals(1, userA.createdAt)
        assertEquals("Original", userA.note)
        val noAccount = db.transactionDao.getById("no-accounts")!!
        assertNull(noAccount.accountId)
        assertFalse(noAccount.pendingSync)
        assertEquals(10, noAccount.updatedAt)
        assertEquals("daily-a", db.accountDao.getDefault("user-a")!!.id)
        val switched = userA.copy(accountId = "savings-a")
        db.transactionDao.upsertWithTag(switched)
        db.close()
        db = open()
        assertEquals(switched, db.transactionDao.observeById("user-a", "user-a").first())
        assertEquals(3, listOf("user-a", "user-b", "no-accounts").mapNotNull { db.transactionDao.getById(it) }.size)
    }

    private fun open() = Room.databaseBuilder(context, ClarityDatabase::class.java, databaseName)
        .addMigrations(TransactionAccountMigration, TransactionAccountIndexMigration).build().also { database = it }
}
