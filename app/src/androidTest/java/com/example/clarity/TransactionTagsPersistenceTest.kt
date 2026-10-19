package com.example.clarity

import com.example.clarity.data.local.ProofRecoveryMigration
import com.example.clarity.data.local.TransactionProofMigration
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.TransactionTagMigration
import com.example.clarity.data.local.AccountMigration
import com.example.clarity.data.local.TransactionAccountMigration
import com.example.clarity.data.local.TransactionAccountIndexMigration
import com.example.clarity.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionTagsPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "transaction-tags-test"
    private var database: ClarityDatabase? = null

    @Before
    fun setUp() { context.deleteDatabase(databaseName) }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(): ClarityDatabase = Room.databaseBuilder(
        context, ClarityDatabase::class.java, databaseName
    ).addMigrations(TransactionTagMigration, AccountMigration, TransactionAccountMigration, TransactionAccountIndexMigration, TransactionProofMigration(), ProofRecoveryMigration).build().also { database = it }

    private fun row(id: String, tag: String, description: String = "", user: String = "user-a") =
        TransactionEntity(id, user, "EXPENSE", 1250, tag, description, 100, 1, 10, pendingSync = true)

    @Test
    fun customTagsRemainReusableAfterEditingDeletingAndReopeningDatabase() = runBlocking {
        var db = openDatabase()
        val original = row("custom", "Hobby", "Paint and brushes")
        db.transactionDao.upsertWithTag(original)
        db.transactionDao.upsertWithTag(row("other-user", "Private", user = "user-b"))
        db.close()
        db = openDatabase()
        assertEquals(original, db.transactionDao.observeById("custom", "user-a").first())
        db.transactionDao.upsertWithTag(original.copy(category = "Savings", note = ""))
        assertEquals("", db.transactionDao.getById("custom")!!.note)
        db.transactionDao.deleteById("custom", "user-a")
        db.close()
        db = openDatabase()
        assertEquals(listOf("Hobby", "Savings"), withTimeout(10_000) { db.transactionTagDao.observeTags("user-a").first() })
        assertEquals(listOf("Private"), db.transactionTagDao.observeTags("user-b").first())
        assertTrue(db.transactionTagDao.observeTags("signed-out-user").first().isEmpty())
    }

    @Test
    fun catalogIgnoresCaseDuplicatesAndIncludesTagsFromSyncedTransactions() = runBlocking {
        val db = openDatabase()
        db.transactionDao.upsertWithTag(row("one", "Hobby"))
        db.transactionDao.upsertWithTag(row("two", "hobby"))
        db.transactionDao.upsert(row("remote", "Travel").copy(pendingSync = false))
        db.transactionDao.deleteById("one", "user-a")
        db.transactionDao.deleteById("two", "user-a")
        assertEquals(listOf("Hobby", "Travel"), db.transactionTagDao.observeTags("user-a").first())
    }

    @Test
    fun remoteReconciliationKeepsTagsReusableAfterTransactionDeletion() = runBlocking {
        val db = openDatabase()
        val synced = row("remote-1", "Hobby", "Synced description").copy(pendingSync = false)
        db.transactionDao.reconcileRemote("user-a", listOf(synced), setOf("remote-1"), authoritative = true)
        assertEquals(synced, db.transactionDao.getById("remote-1"))
        db.transactionDao.deleteById("remote-1", "user-a")
        assertEquals(listOf("Hobby"), db.transactionTagDao.observeTags("user-a").first())
    }

    @Test
    fun versionTwoMigrationPreservesExistingDataAndBackfillsUserScopedTags() = runBlocking {
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
            legacy.execSQL("INSERT INTO sample_table VALUES ('sample', 'Existing sample', 'Keep this')")
            legacy.execSQL("INSERT INTO transactions VALUES ('legacy', 'user-a', 'EXPENSE', 1250, 'Hobby', 'Original description', 100, 1, 10, 1, 0)")
            legacy.execSQL("INSERT INTO transactions VALUES ('other', 'user-b', 'INCOME', 5000, 'Private', '', 100, 1, 10, 0, 0)")
            legacy.version = 2
        }
        val db = openDatabase()
        assertEquals(row("legacy", "Hobby", "Original description"), db.transactionDao.getById("legacy"))
        assertEquals(listOf("Hobby"), db.transactionTagDao.observeTags("user-a").first())
        assertEquals(listOf("Private"), db.transactionTagDao.observeTags("user-b").first())
        db.openHelper.readableDatabase.query("SELECT title, description FROM sample_table WHERE id = 'sample'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Existing sample", it.getString(0))
            assertEquals("Keep this", it.getString(1))
        }
    }
}
