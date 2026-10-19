package com.example.clarity

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.entity.AccountEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.remote.IncomeSourceRemoteDataSource
import com.example.clarity.data.repository.IncomeSourceRepositoryImpl
import com.example.clarity.data.sync.AccountUploader
import com.example.clarity.data.sync.IncomeSyncProcessor
import com.example.clarity.di.DatabaseModule
import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.util.IncomePolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IncomeSourcePersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val october = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val november = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC)
    private fun source(id: String = "salary", recurring: Boolean = true) = IncomeSource(id = id, name = id, amountMinor = 125000, accountId = "bank", recurring = recurring)
    private suspend fun accounts(db: ClarityDatabase) { db.accountDao.upsertAll(listOf(
        AccountEntity("bank", "owner", "Daily", "Bank", null, "LKR", true, 1, 1),
        AccountEntity("private", "other", "Private", "Bank", null, "LKR", true, 1, 1))) }

    @Test fun multipleSourcesConfirmOncePerMonthAndPersistAcrossReopening() = runBlocking {
        val name = "income-${UUID.randomUUID()}"
        val auth = TestAuth()
        var db = DatabaseModule.createDatabase(context, name)
        try {
            accounts(db)
            var repo = IncomeSourceRepositoryImpl(db, auth, Dispatchers.IO, october)
            repo.save(source()).getOrThrow()
            repo.save(source("freelance", recurring = false)).getOrThrow()
            assertEquals(2, repo.observeSources().first().sources.size)
            assertEquals(0, db.transactionDao.getIdsForUser("owner").size)
            val row = db.incomeSourceDao.get("owner", "salary")!!
            val results = coroutineScope { (1..2).map { async(Dispatchers.IO) { repo.record("salary", "2026-10", row.updatedAt) } }.awaitAll() }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, db.transactionDao.getIdsForUser("owner").size)
            val transaction = db.transactionDao.getById(results.single { it.isSuccess }.getOrThrow())!!
            assertEquals("INCOME", transaction.type)
            assertEquals("bank", transaction.accountId)
            assertEquals(125000L, transaction.amountMinor)
            assertTrue(transaction.pendingSync)
            assertFalse(db.cashWalletDao.isCashLink("owner", transaction.id))
            db.close()
            db = DatabaseModule.createDatabase(context, name)
            repo = IncomeSourceRepositoryImpl(db, auth, Dispatchers.IO, november)
            val saved = db.incomeSourceDao.get("owner", "salary")!!
            assertEquals("2026-10", saved.lastConfirmedMonth)
            assertTrue(saved.pendingSync)
            repo.record("salary", "2026-11", saved.updatedAt).getOrThrow()
            assertEquals(2, db.transactionDao.getIdsForUser("owner").size)
            val oneOff = db.incomeSourceDao.get("owner", "freelance")!!
            assertNotEquals(repo.record("freelance", null, oneOff.updatedAt).getOrThrow(), repo.record("freelance", null, oneOff.updatedAt).getOrThrow())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun ownershipUnavailableAccountsStaleReviewAndWrongMonthCannotCreateIncome() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        try {
            accounts(db)
            val auth = TestAuth()
            val repo = IncomeSourceRepositoryImpl(db, auth, Dispatchers.IO, october)
            assertTrue(repo.save(source().copy(accountId = "private")).isFailure)
            assertTrue(repo.save(source().copy(userId = "other")).isFailure)
            repo.save(source()).getOrThrow()
            val before = db.incomeSourceDao.get("owner", "salary")!!
            assertTrue(repo.record("salary", "2026-09", before.updatedAt).isFailure)
            repo.save(before.toDomain().copy(amountMinor = 150000)).getOrThrow()
            assertTrue(repo.record("salary", "2026-10", before.updatedAt).isFailure)
            assertTrue(db.transactionDao.getIdsForUser("owner").isEmpty())
            auth.currentUser.value = User("other", null, null)
            assertEquals("other", repo.observeSources().first().ownerId)
            assertTrue(repo.observeSources().first().sources.isEmpty())
            assertTrue(repo.record("salary", "2026-10", before.updatedAt).isFailure)
            auth.currentUser.value = null
            assertNull(repo.observeSources().first().ownerId)
            assertTrue(repo.save(source()).isFailure)
        } finally { db.close() }
    }

    @Test fun archivingStopsPromptsWithoutDeletingFinancialHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        try {
            accounts(db)
            val repo = IncomeSourceRepositoryImpl(db, TestAuth(), Dispatchers.IO, october)
            repo.save(source()).getOrThrow()
            val row = db.incomeSourceDao.get("owner", "salary")!!
            repo.record("salary", "2026-10", row.updatedAt).getOrThrow()
            repo.archive("salary").getOrThrow()
            assertFalse(db.incomeSourceDao.get("owner", "salary")!!.active)
            assertEquals(1, db.transactionDao.getIdsForUser("owner").size)
            val archived = db.incomeSourceDao.get("owner", "salary")!!
            assertTrue(repo.record("salary", "2026-10", archived.updatedAt).isFailure)
        } finally { db.close() }
    }

    @Test fun sourceSyncRetriesOfflineAndDoesNotAcknowledgeANewerEdit() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ClarityDatabase::class.java).build()
        try {
            accounts(db)
            val auth = TestAuth()
            val repo = IncomeSourceRepositoryImpl(db, auth, Dispatchers.IO, october)
            val remote = FakeRemote()
            val uploader = object : AccountUploader {
                override suspend fun uploadPending(uid: String, afterAccounts: suspend () -> Unit): Boolean { afterAccounts(); return true }
            }
            val processor = IncomeSyncProcessor(db, remote, auth, uploader)
            repo.save(source()).getOrThrow()
            assertFalse(processor.upload("owner"))
            assertTrue(db.incomeSourceDao.get("owner", "salary")!!.pendingSync)
            remote.offline = false
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            remote.onWrite = { entered.complete(Unit); release.await() }
            coroutineScope {
                val attempt = async(Dispatchers.IO) { processor.upload("owner") }
                entered.await()
                repo.save(db.incomeSourceDao.get("owner", "salary")!!.toDomain().copy(amountMinor = 200000)).getOrThrow()
                release.complete(Unit)
                assertFalse(attempt.await())
            }
            remote.onWrite = {}
            assertTrue(db.incomeSourceDao.get("owner", "salary")!!.pendingSync)
            assertTrue(processor.upload("owner"))
            assertEquals(200000L, remote.rows.getValue("salary").amountMinor)
            assertFalse(db.incomeSourceDao.get("owner", "salary")!!.pendingSync)
            val cloud = remote.rows.getValue("salary").copy(lastConfirmedMonth = "2026-11")
            processor.reconcile("owner", listOf(cloud))
            assertEquals("2026-11", db.incomeSourceDao.get("owner", "salary")!!.lastConfirmedMonth)
            processor.reconcile("owner", listOf(cloud.copy(lastConfirmedMonth = "2026-10")))
            assertEquals("2026-11", db.incomeSourceDao.get("owner", "salary")!!.lastConfirmedMonth)
        } finally { db.close() }
    }

    private class FakeRemote : IncomeSourceRemoteDataSource {
        var offline = true
        var onWrite: suspend () -> Unit = {}
        val rows = mutableMapOf<String, IncomeSource>()
        override fun observe(uid: String): Flow<List<IncomeSource>> = flowOf(rows.values.filter { it.userId == uid })
        override suspend fun write(source: IncomeSource): IncomeSource {
            check(!offline)
            onWrite()
            val saved = source.copy(pendingSync = false, lastConfirmedMonth = IncomePolicy.latestMonth(rows[source.id]?.lastConfirmedMonth, source.lastConfirmedMonth))
            rows[source.id] = saved
            return saved
        }
    }
    private class TestAuth : AuthRepository {
        override val currentUser = MutableStateFlow<User?>(User("owner", null, null))
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
        override suspend fun signOut() { currentUser.value = null }
    }
}
