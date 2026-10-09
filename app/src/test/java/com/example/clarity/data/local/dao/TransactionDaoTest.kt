package com.example.clarity.data.local.dao

import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TransactionDaoTest {
    private class FakeDao : TransactionDao() {
        private val rows = MutableStateFlow<Map<String, TransactionEntity>>(emptyMap())
        override fun observeAll(userId: String) = rows.map { data ->
            data.values.filter { it.userId == userId && !it.isDeleted }.sortedWith(
                compareByDescending<TransactionEntity> { it.dateEpochMillis }.thenByDescending { it.createdAt }
            )
        }
        override fun observeById(id: String, userId: String) = rows.map {
            it[id]?.takeIf { row -> row.userId == userId && !row.isDeleted }
        }
        override suspend fun upsert(entity: TransactionEntity) { rows.value = rows.value + (entity.id to entity) }
        override suspend fun upsertAll(entities: List<TransactionEntity>) { entities.forEach { upsert(it) } }
        override suspend fun deleteById(id: String, userId: String) {
            if (rows.value[id]?.userId == userId) rows.value = rows.value - id
        }
        override suspend fun deleteAllForUser(userId: String) {
            rows.value = rows.value.filterValues { it.userId != userId }
        }
        override suspend fun getIdsForUser(userId: String) = rows.value.values.filter { it.userId == userId }.map { it.id }
        override suspend fun getById(id: String) = rows.value[id]
        override suspend fun getPendingForUser(userId: String) = rows.value.values.filter { it.userId == userId && it.pendingSync }
    }

    private fun row(id: String = "id", owner: String = "a", updatedAt: Long = 10L) =
        Transaction(id = id, userId = owner, amountMinor = 100L, createdAt = 1L, updatedAt = updatedAt).toEntity()

    @Test fun cacheAbsenceCannotEraseSavedRows() = runTest {
        val dao = FakeDao()
        val local = row()
        dao.upsert(local)
        dao.reconcileRemote("a", emptyList(), emptySet(), false)
        assertEquals(local, dao.getById("id"))
    }
    @Test fun authoritativeAbsenceRemovesSyncedRowsOnlyForThatUser() = runTest {
        val dao = FakeDao()
        dao.upsert(row("a", "a"))
        val other = row("b", "b")
        dao.upsert(other)
        dao.reconcileRemote("a", emptyList(), emptySet(), true)
        assertNull(dao.getById("a"))
        assertEquals(other, dao.getById("b"))
    }
    @Test fun pendingEditsSurviveStaleSnapshotsAndNeedServerConfirmation() = runTest {
        val dao = FakeDao()
        val pending = row(updatedAt = 20L).copy(pendingSync = true)
        dao.upsert(pending)
        dao.reconcileRemote("a", listOf(row(updatedAt = 10L)), setOf("id"), true)
        assertEquals(pending, dao.getById("id"))
        dao.reconcileRemote("a", emptyList(), emptySet(), true)
        assertEquals(pending, dao.getById("id"))
        dao.reconcileRemote("a", listOf(pending.copy(pendingSync = false)), setOf("id"), false)
        assertTrue(dao.getById("id")!!.pendingSync)
        dao.reconcileRemote("a", listOf(pending.copy(pendingSync = false)), setOf("id"), true)
        assertFalse(dao.getById("id")!!.pendingSync)
    }
    @Test fun pendingDeleteCannotBeResurrectedAndRemainsUntilAcknowledged() = runTest {
        val dao = FakeDao()
        val deleted = row().copy(pendingSync = true, isDeleted = true)
        dao.upsert(deleted)
        dao.reconcileRemote("a", listOf(row()), setOf("id"), true)
        assertEquals(deleted, dao.getById("id"))
        assertTrue(dao.observeAll("a").first().isEmpty())
        assertNull(dao.observeById("id", "a").first())
        dao.reconcileRemote("a", emptyList(), emptySet(), true)
        assertEquals(deleted, dao.getById("id"))
    }
    @Test fun malformedRemoteDocumentIsNotMistakenForDeletion() = runTest {
        val dao = FakeDao()
        val local = row()
        dao.upsert(local)
        // Parser omitted the malformed document, but its ID still exists remotely.
        dao.reconcileRemote("a", emptyList(), setOf("id"), true)
        assertEquals(local, dao.getById("id"))
    }
    @Test fun remoteOwnershipAndCrossUserIdCollisionsCannotOverwriteAnotherUser() = runTest {
        val dao = FakeDao()
        val other = row(owner = "b")
        dao.upsert(other)
        dao.reconcileRemote("a", listOf(row(owner = "a")), setOf("id"), true)
        assertEquals(other, dao.getById("id"))
        dao.reconcileRemote("a", listOf(row("new", "b")), setOf("new"), false)
        assertNull(dao.getById("new"))
        assertTrue(dao.observeAll("a").first().isEmpty())
        assertNull(dao.observeById("id", "a").first())
    }
}
