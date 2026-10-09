package com.example.clarity

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.dao.TransactionDao
import com.example.clarity.data.local.entity.TransactionEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {
    private lateinit var database: ClarityDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ClarityDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.transactionDao
    }

    @After
    fun tearDown() { database.close() }

    private fun row(
        id: String,
        userId: String = "user-a",
        date: Long = 100L,
        created: Long = 1L
    ) = TransactionEntity(id, userId, "EXPENSE", 1250L, "General", "", date, created, 10L)

    @Test
    fun insertObserveOrderingAndUserIsolation() = runBlocking {
        dao.upsertAll(listOf(
            row("older", date = 100L), row("newer", date = 200L),
            row("same-date-later-created", date = 200L, created = 2L),
            row("other-user", userId = "user-b", date = 300L)
        ))
        val userA = withTimeout(10_000) { dao.observeAll("user-a").first() }
        assertEquals(listOf("same-date-later-created", "newer", "older"), userA.map { it.id })
        assertEquals(listOf("other-user"), withTimeout(10_000) { dao.observeAll("user-b").first() }.map { it.id })
        assertNull(withTimeout(10_000) { dao.observeById("other-user", "user-a").first() })
        assertEquals("other-user", withTimeout(10_000) { dao.observeById("other-user", "user-b").first() }?.id)
    }

    @Test
    fun updateAndDeleteNotifyTheSameRoomObserver() = runBlocking {
        val original = row("transaction")
        dao.upsert(original)
        val events = Channel<TransactionEntity?>(Channel.UNLIMITED)
        val observer = launch { dao.observeById(original.id, original.userId).collect { events.send(it) } }
        suspend fun awaitRow(expected: TransactionEntity?) {
            withTimeout(10_000) { while (events.receive() != expected) { /* Skip duplicate emissions. */ } }
        }
        try {
            awaitRow(original)
            val updated = original.copy(amountMinor = 3000L, updatedAt = 20L)
            dao.upsert(updated)
            awaitRow(updated)
            dao.deleteById(original.id, "user-b")
            assertEquals(updated, dao.getById(original.id))
            dao.deleteById(original.id, original.userId)
            awaitRow(null)
            assertTrue(dao.getIdsForUser(original.userId).isEmpty())
        } finally {
            observer.cancel()
            events.close()
        }
    }

    @Test
    fun pendingEditSurvivesCacheAndOlderRemoteDataUntilConfirmed() = runBlocking {
        val pending = row("pending").copy(updatedAt = 20L, pendingSync = true)
        val other = row("other", userId = "user-b")
        dao.upsertAll(listOf(pending, other))
        dao.reconcileRemote("user-a", emptyList(), emptySet(), authoritative = false)
        dao.reconcileRemote("user-a", listOf(row("pending")), setOf("pending"), authoritative = true)
        dao.reconcileRemote("user-a", emptyList(), emptySet(), authoritative = true)
        assertEquals(pending, dao.getById("pending"))
        val confirmed = pending.copy(pendingSync = false)
        dao.reconcileRemote("user-a", listOf(confirmed), setOf("pending"), authoritative = false)
        assertTrue(dao.getById("pending")!!.pendingSync)
        dao.reconcileRemote("user-a", listOf(confirmed), setOf("pending"), authoritative = true)
        assertEquals(confirmed, dao.getById("pending"))
        assertEquals(other, dao.getById("other"))
        assertTrue(dao.getPendingForUser("user-b").isEmpty())
    }

    @Test
    fun deletionMarkerStaysHiddenAndRemoteDeletionOnlyRemovesSyncedRows() = runBlocking {
        val tombstone = row("deleted").copy(isDeleted = true, pendingSync = true)
        val synced = row("synced")
        dao.upsertAll(listOf(tombstone, synced))
        assertEquals(listOf("synced"), withTimeout(10_000) { dao.observeAll("user-a").first() }.map { it.id })
        assertNull(withTimeout(10_000) { dao.observeById("deleted", "user-a").first() })
        assertEquals(listOf(tombstone), dao.getPendingForUser("user-a"))
        dao.reconcileRemote("user-a", listOf(row("deleted")), setOf("deleted"), authoritative = true)
        assertEquals(tombstone, dao.getById("deleted"))
        assertNull(dao.getById("synced"))
        dao.reconcileRemote("user-a", emptyList(), emptySet(), authoritative = true)
        assertEquals(tombstone, dao.getById("deleted"))
        dao.deleteById("deleted", "user-a")
        assertNull(dao.getById("deleted"))
    }
}
