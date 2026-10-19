package com.example.clarity

import com.example.clarity.di.DatabaseModule

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
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

    private fun openDatabase(): ClarityDatabase = DatabaseModule.createDatabase(context, databaseName).also { database = it }

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

}
