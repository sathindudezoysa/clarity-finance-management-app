package com.example.clarity.data.repository

import com.example.clarity.data.local.dao.CurrencyDao
import com.example.clarity.data.local.entity.CurrencyPreferenceEntity
import com.example.clarity.data.remote.FirestoreDataSource
import com.example.clarity.data.sync.CurrencyWorkScheduler
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.repository.*
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CurrencyPreferenceRepositoryImpl @Inject constructor(
    private val dao: CurrencyDao,
    private val auth: AuthRepository,
    private val firestore: FirestoreDataSource,
    private val scheduler: CurrencyWorkScheduler,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope
) : CurrencyPreferenceRepository {
    init {
        scope.launch {
            auth.currentUser.distinctUntilChangedBy { it?.uid }.collect { user ->
                user?.let { scheduler.schedule(it.uid) }
            }
        }
    }

    override fun observe(): Flow<CurrencyPreference> = auth.currentUser.distinctUntilChangedBy { it?.uid }.flatMapLatest { user ->
        if (user == null) flowOf(CurrencyPreference())
        else dao.observePreference(user.uid).map { CurrencyPreference(it?.currency ?: "LKR", it?.pendingSync ?: false) }
    }

    override suspend fun current(): String {
        val uid = auth.currentUser.first()?.uid ?: error("Please sign in")
        return dao.preference(uid)?.currency ?: "LKR"
    }

    override suspend fun set(currency: String): Result<Unit> = try {
        Currencies.digits(currency)
        val uid = auth.currentUser.first()?.uid ?: error("Please sign in")
        dao.editPreference(uid, currency, clock.millis())
        scheduler.schedule(uid)
        Result.success(Unit)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }

    suspend fun sync(uid: String): Boolean {
        if (auth.currentUser.first()?.uid != uid) return true
        val local = dao.preference(uid)
        val document = firestore.getCollection("users/$uid/preferences").document("currency")
        val remote = if (local?.pendingSync == true) {
            firestore.getCollection("users/$uid/preferences").firestore.runTransaction { transaction ->
                val snapshot = transaction.get(document)
                val existing = snapshot.data?.let { parse(uid, it) ?: error("Invalid currency preference") }
                if (existing == null || existing.updatedAt < local.updatedAt) {
                    transaction.set(document, mapOf("currency" to local.currency, "updatedAt" to local.updatedAt))
                    local.copy(pendingSync = false)
                } else existing
            }.await()
        } else {
            val snapshot = document.get(Source.SERVER).await()
            if (!snapshot.exists()) null else parse(uid, checkNotNull(snapshot.data)) ?: error("Invalid currency preference")
        }
        if (auth.currentUser.first()?.uid != uid) return true
        if (remote != null) dao.reconcilePreference(remote, local?.takeIf { it.pendingSync })
        return dao.preference(uid)?.pendingSync != true
    }

    private fun parse(uid: String, data: Map<String, Any>): CurrencyPreferenceEntity? {
        val currency = data["currency"] as? String ?: return null
        val updated = data["updatedAt"] as? Long ?: return null
        if (data.keys != setOf("currency", "updatedAt") || currency !in Currencies.supported || updated < 0) return null
        return CurrencyPreferenceEntity(uid, currency, updated, false)
    }
}
