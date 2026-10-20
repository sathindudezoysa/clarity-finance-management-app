package com.example.clarity.data.remote

import com.example.clarity.data.mapper.toBudget
import com.example.clarity.data.mapper.toBudgetRemoteMap
import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.model.BudgetPolicy
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject

interface BudgetRemoteDataSource {
    fun observe(uid: String): Flow<List<Budget>>
    suspend fun write(budget: Budget): Budget
}

class FirestoreBudgetDataSource @Inject constructor(private val firestore: FirestoreDataSource) : BudgetRemoteDataSource {
    override fun observe(uid: String): Flow<List<Budget>> = callbackFlow {
        val listener = firestore.getCollection("users/$uid/budgets").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites())
                trySend(snapshot.documents.mapNotNull { it.data?.toBudget(it.id, uid) })
        }
        awaitClose { listener.remove() }
    }
    override suspend fun write(budget: Budget): Budget = withTimeoutOrNull(30_000) {
        val doc = firestore.getCollection("users/${budget.userId}/budgets").document(budget.id)
        doc.firestore.runTransaction { tx ->
            val snapshot = tx.get(doc)
            val old = snapshot.data?.toBudget(budget.id, budget.userId)
            require(!snapshot.exists() || old != null) { "Invalid remote budget" }
            val merged = BudgetPolicy.resolveUpload(budget, old)
            tx.set(doc, merged.toBudgetRemoteMap())
            merged
        }.await()
    } ?: throw IOException("Budget sync timed out")
}
