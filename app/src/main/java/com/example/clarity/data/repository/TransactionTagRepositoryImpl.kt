package com.example.clarity.data.repository

import android.util.Log
import com.example.clarity.data.local.dao.TransactionTagDao
import com.example.clarity.data.local.entity.TransactionTagEntity
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.data.mapper.toTransactionTagEntity
import com.example.clarity.data.remote.FirestoreDataSource
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.TransactionTagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionTagRepositoryImpl @Inject constructor(
    private val dao: TransactionTagDao,
    private val firestore: FirestoreDataSource,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope applicationScope: CoroutineScope
) : TransactionTagRepository {

    private val syncScope = CoroutineScope(applicationScope.coroutineContext + SupervisorJob())

    init {
        syncScope.launch(ioDispatcher) {
            authRepository.currentUser
                .distinctUntilChangedBy { it?.uid }
                .collectLatest { user ->
                    if (user != null) {
                        seedDefaults(user.uid)
                        coroutineScope {
                            launch {
                                dao.observePendingForUser(user.uid)
                                    .distinctUntilChanged()
                                    .catch { error ->
                                        if (error is CancellationException) throw error
                                        Log.w(TAG, "Pending category tags observation error", error)
                                    }
                                    .collect { pending ->
                                        if (pending.isNotEmpty()) {
                                            uploadPending(user.uid)
                                        }
                                    }
                            }
                            launch {
                                observeRemoteTypeTags(user.uid, TransactionType.EXPENSE).collect {}
                            }
                            launch {
                                observeRemoteTypeTags(user.uid, TransactionType.INCOME).collect {}
                            }
                        }
                    }
                }
        }
    }

    override fun observeTags(): Flow<List<String>> = authRepository.currentUser
        .distinctUntilChangedBy { it?.uid }
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else dao.observeTags(user.uid)
                .onStart {
                    seedDefaults(user.uid)
                    emit(emptyList())
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(emptyList())
                }
        }.flowOn(ioDispatcher)

    override fun observeTagsByType(type: TransactionType): Flow<List<String>> = authRepository.currentUser
        .distinctUntilChangedBy { it?.uid }
        .flatMapLatest { user ->
            if (user == null) flowOf(TransactionTags.defaultsFor(type))
            else dao.observeTagsByType(user.uid, type.name)
                .onStart {
                    seedDefaults(user.uid)
                    emit(TransactionTags.defaultsFor(type))
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(TransactionTags.defaultsFor(type))
                }
        }.flowOn(ioDispatcher)

    override suspend fun addCustomTag(name: String, type: TransactionType): Result<Unit> = withContext(ioDispatcher) {
        try {
            val uid = authRepository.currentUser.first()?.uid
                ?: return@withContext Result.failure(
                    TransactionException(TransactionError.SIGN_IN_REQUIRED, "Please sign in")
                )
            val clean = name.trim()
            require(clean.isNotEmpty() && clean.length <= 100) { "Category name must be between 1 and 100 characters." }
            val now = System.currentTimeMillis()
            val isDefault = TransactionTags.isDefault(type, clean)
            val existing = dao.getTag(uid, type.name, clean)
            val entity = TransactionTagEntity(
                userId = uid,
                name = existing?.name ?: clean,
                type = type.name,
                isDefault = existing?.isDefault ?: isDefault,
                createdAt = existing?.createdAt ?: now,
                updatedAt = maxOf(now, (existing?.updatedAt ?: 0L) + 1L),
                pendingSync = !isDefault
            )
            dao.upsert(entity)
            if (!isDefault) {
                syncScope.launch(ioDispatcher) {
                    uploadSingle(entity)
                }
            }
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    private suspend fun seedDefaults(uid: String) {
        runCatching {
            val now = System.currentTimeMillis()
            val expenseDefaults = TransactionTags.defaultExpenseCategories.map { category ->
                TransactionTagEntity(
                    userId = uid,
                    name = category,
                    type = TransactionType.EXPENSE.name,
                    isDefault = true,
                    createdAt = now,
                    updatedAt = now,
                    pendingSync = false
                )
            }
            val incomeDefaults = TransactionTags.defaultIncomeCategories.map { category ->
                TransactionTagEntity(
                    userId = uid,
                    name = category,
                    type = TransactionType.INCOME.name,
                    isDefault = true,
                    createdAt = now,
                    updatedAt = now,
                    pendingSync = false
                )
            }
            dao.insertIgnoreAll(expenseDefaults + incomeDefaults)
        }
    }

    private suspend fun uploadPending(uid: String) {
        val pending = runCatching { dao.getPendingForUser(uid) }.getOrDefault(emptyList())
        for (entity in pending) {
            uploadSingle(entity)
        }
    }

    private fun bucketNameFor(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "income"
        TransactionType.EXPENSE -> "expenses"
    }

    private fun categoryCollectionPath(uid: String, type: TransactionType): String {
        val bucket = bucketNameFor(type)
        return "users/$uid/categories/$bucket/$bucket"
    }

    private suspend fun uploadSingle(entity: TransactionTagEntity) {
        if (entity.isDefault || !entity.pendingSync) return
        try {
            val txType = runCatching { TransactionType.valueOf(entity.type) }.getOrDefault(TransactionType.EXPENSE)
            val bucket = bucketNameFor(txType)
            val docId = TransactionTags.tagId(txType, entity.name)
            // Ensure parent category bucket document (income / expenses) exists under users/{uid}/categories
            firestore.getCollection("users/${entity.userId}/categories")
                .document(bucket)
                .set(
                    mapOf(
                        "type" to txType.name,
                        "userId" to entity.userId,
                        "updatedAt" to entity.updatedAt
                    )
                )
                .await()
            // Sync custom category document inside the respective income or expenses subcollection
            firestore.getCollection(categoryCollectionPath(entity.userId, txType))
                .document(docId)
                .set(entity.toFirestoreMap())
                .await()
            dao.acknowledge(entity.userId, entity.type, entity.name, entity.updatedAt)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "Category tag sync deferred for retry: ${entity.name}", error)
        }
    }

    private fun observeRemoteTypeTags(uid: String, type: TransactionType): Flow<Unit> = callbackFlow {
        val registration = firestore.getCollection(categoryCollectionPath(uid, type))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Category tags listener warning for ${type.name}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    syncScope.launch(ioDispatcher) {
                        val incoming = snapshot.documents.mapNotNull { doc ->
                            doc.data.toTransactionTagEntity(doc.id, uid)?.takeIf { it.type == type.name }
                        }
                        if (incoming.isNotEmpty()) {
                            val toSave = incoming.filter { remote ->
                                val local = dao.getTag(uid, remote.type, remote.name)
                                local == null || (!local.pendingSync && remote.updatedAt >= local.updatedAt)
                            }
                            if (toSave.isNotEmpty()) {
                                dao.upsertAll(toSave)
                            }
                        }
                    }
                    trySend(Unit)
                }
            }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val TAG = "TransactionTagRepo"
    }
}
