package com.example.clarity

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.*
import com.example.clarity.data.remote.FirestoreDataSource
import com.example.clarity.data.repository.*
import com.example.clarity.data.sync.*
import com.example.clarity.domain.model.*
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.repository.*
import com.example.clarity.domain.util.Resource
import com.google.firebase.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.util.UUID

/** Run with firebase.currency-tests.json, demo-clarity-currency, Auth + Firestore emulators. */
@RunWith(AndroidJUnit4::class)
class CurrencySyncIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class Auth : AuthRepository {
        override val currentUser = MutableStateFlow<User?>(null)
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
        override suspend fun signOut() { currentUser.value = null }
    }

    @Test fun networkConstrainedWorkerResolvesAfterRestartAndSynchronizesCompleteSnapshot() = runBlocking {
        val name = "currency-sync-${UUID.randomUUID()}"
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setApplicationId("1:123:android:currency-test")
            .setProjectId("demo-clarity-currency").setApiKey("fake-test-key").build(), name)
        val firebaseAuth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9098) }
        val firestore = FirebaseFirestore.getInstance(app).apply {
            useEmulator("10.0.2.2", 8086)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
        val auth = Auth()
        val uid = withTimeout(20_000) { firebaseAuth.signInAnonymously().await().user!!.uid }
        auth.currentUser.value = User(uid, null, null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var db = Room.databaseBuilder(context, ClarityDatabase::class.java, name).build()
        var transactions: TransactionRepositoryImpl? = null
        var preferences: CurrencyPreferenceRepositoryImpl? = null
        var requests = 0
        var useCache = false
        var pause: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null
        val rates = object : ExchangeRateRepository {
            override suspend fun cached(source: String, target: String): ExchangeRate? {
                if (!useCache) return null
                val now = System.currentTimeMillis()
                return ExchangeRate(source, target, "300", "ExchangeRate-API", now - 1000, now, now + 86_400_000, true)
            }
            override suspend fun latest(source: String, target: String): ExchangeRate {
                requests++
                pause?.let { (started, resume) -> started.complete(Unit); resume.await() }
                val now = System.currentTimeMillis()
                return ExchangeRate(source, target, "300", "ExchangeRate-API", now - 1000, now, now + 86_400_000)
            }
        }
        val dependencies = object : CurrencyWorkerDependencies {
            override fun auth(): AuthRepository = auth
            override fun transactions() = checkNotNull(transactions)
            override fun preferences() = checkNotNull(preferences)
        }
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == CurrencySyncWorker::class.java.name) CurrencySyncWorker(appContext, workerParameters, dependencies) else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        val manager = WorkManager.getInstance(context)
        val scheduler = CurrencyWorkScheduler(context)
        val uploader = object : AccountUploader {
            override suspend fun uploadPending(uid: String, afterAccounts: suspend () -> Unit): Boolean { afterAccounts(); return true }
        }
        fun repository() = TransactionRepositoryImpl(db.transactionDao, FirestoreDataSource(firestore), auth,
            Dispatchers.IO, uploader, scope, rates, preferences ?: TestCurrencies.preferences, scheduler)
        try {
            firestore.disableNetwork().await()
            transactions = repository()
            withTimeout(10_000) { transactions!!.observeTransactions().first { it.data != null } }
            // Offline cache availability can resolve an unfinished snapshot, never a completed one.
            transactions!!.addTransaction(Transaction(id = "pending-cache", amountMinor = 100, currency = "USD")).getOrThrow()
            val unfinished = db.transactionDao.getById("pending-cache")!!
            assertTrue(unfinished.pendingConversion)
            useCache = true
            transactions!!.updateTransaction(unfinished.toDomain().copy(note = "A cache is now available")).getOrThrow()
            transactions!!.addTransaction(Transaction(id = "cached-offline", amountMinor = 100, currency = "USD")).getOrThrow()
            assertFalse(db.transactionDao.getById("pending-cache")!!.pendingConversion)
            assertTrue(db.transactionDao.getById("cached-offline")!!.conversion!!.cached)
            assertEquals(0, requests)
            useCache = false
            val entry = Transaction(id = "foreign", amountMinor = 1250, currency = "USD", targetCurrency = "LKR")
            transactions!!.addTransaction(entry).getOrThrow()
            val pending = db.transactionDao.getById(entry.id)!!
            assertTrue(pending.pendingConversion)
            assertTrue(pending.pendingSync)
            assertEquals(0, requests)
            assertTrue(transactions!!.observeTransactions().first { it.data?.any { row -> row.id == entry.id } == true }.data!!.any { it.id == entry.id })
            transactions!!.stopSync().join()
            db.close()
            db = Room.databaseBuilder(context, ClarityDatabase::class.java, name).build()
            assertEquals(pending, db.transactionDao.getById(entry.id))
            preferences = CurrencyPreferenceRepositoryImpl(db.currencyDao, auth, FirestoreDataSource(firestore), scheduler, Clock.systemUTC(), scope)
            preferences!!.set("JPY").getOrThrow()
            transactions = repository()
            withTimeout(10_000) { transactions!!.observeTransactions().first { it.data != null } }
            // No transaction screen is opened. Only releasing WorkManager's network constraint triggers conversion.
            firestore.enableNetwork().await()
            val work = withTimeout(10_000) {
                manager.getWorkInfosForUniqueWorkFlow("currency-now-$uid").first { it.isNotEmpty() }.first()
            }
            assertEquals(WorkInfo.State.ENQUEUED, work.state)
            WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(work.id)
            val resolved = withTimeout(30_000) {
                db.transactionDao.observeById(entry.id, uid).first { it != null && !it.pendingConversion && !it.pendingSync }!!
            }
            val remote = withTimeout(15_000) { firestore.document("users/$uid/transactions/${entry.id}").get(Source.SERVER).await().toDomain() }
            assertEquals(resolved.toDomain(), remote)
            assertEquals(375000L, remote!!.conversion!!.amountMinor)
            assertEquals(1250L, remote.amountMinor)
            assertEquals("LKR", remote.targetCurrency)
            assertEquals("JPY", firestore.document("users/$uid/preferences/currency").get(Source.SERVER).await().getString("currency"))
            assertEquals(1, requests)
            transactions!!.updateTransaction(resolved.toDomain().copy(note = "Note only")).getOrThrow()
            assertTrue(transactions!!.backgroundSync(uid))
            assertEquals(resolved.conversion, db.transactionDao.getById(entry.id)!!.conversion)
            assertEquals(1, requests)
            // A later unique enqueue can prune the previous WorkSpec; business completion
            // is verified above through Room and a fresh server read, rather than the old ID.
            manager.cancelAllWork().result.get()
            val changed = db.transactionDao.getById(entry.id)!!.toDomain().copy(currency = "EUR")
            transactions!!.updateTransaction(changed).getOrThrow()
            assertTrue(db.transactionDao.getById(entry.id)!!.pendingConversion)
            assertTrue(transactions!!.backgroundSync(uid))
            assertEquals(2, requests)
            assertEquals("LKR", db.transactionDao.getById(entry.id)!!.targetCurrency)

            // Exercise the repository lock and auth checks around real suspended rate requests.
            suspend fun startRace(id: String): Pair<Deferred<Boolean>, CompletableDeferred<Unit>> {
                val started = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
                pause = started to resume
                transactions!!.addTransaction(Transaction(id = id, amountMinor = 100, currency = "USD")).getOrThrow()
                val result = async(Dispatchers.IO) { transactions!!.backgroundSync(uid) }
                check(withTimeoutOrNull(10_000) { started.await(); true } == true) { "No rate request for $id" }
                return result to resume
            }
            val (editing, releaseEdit) = startRace("race-edit")
            val beforeEdit = db.transactionDao.getById("race-edit")!!
            withTimeout(2000) { transactions!!.updateTransaction(beforeEdit.toDomain().copy(amountMinor = 200)).getOrThrow() }
            releaseEdit.complete(Unit); editing.await(); pause = null
            assertTrue(db.transactionDao.getById("race-edit")!!.pendingConversion)
            assertEquals(200L, db.transactionDao.getById("race-edit")!!.amountMinor)
            assertTrue(transactions!!.backgroundSync(uid))

            val (deleting, releaseDelete) = startRace("race-delete")
            withTimeout(2000) { transactions!!.deleteTransaction("race-delete").getOrThrow() }
            releaseDelete.complete(Unit); deleting.await(); pause = null
            assertTrue(db.transactionDao.getById("race-delete")?.isDeleted != false)

            val (switching, releaseUser) = startRace("race-user")
            auth.currentUser.value = User("different-user", null, null)
            releaseUser.complete(Unit); switching.await(); pause = null
            assertTrue(db.transactionDao.getById("race-user")!!.pendingConversion)
            assertEquals(uid, db.transactionDao.getById("race-user")!!.userId)
        } finally {
            auth.currentUser.value = null
            manager.cancelAllWork().result.get()
            transactions?.stopSync()?.join()
            scope.cancel()
            db.close()
            context.deleteDatabase(name)
            firestore.terminate().await()
            app.delete()
        }
    }
}
