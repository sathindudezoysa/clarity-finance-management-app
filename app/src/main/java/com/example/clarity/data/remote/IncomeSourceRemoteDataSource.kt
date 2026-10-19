package com.example.clarity.data.remote

import com.example.clarity.data.mapper.toIncomeSource
import com.example.clarity.data.mapper.toRemoteMap
import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.domain.util.IncomePolicy
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject

interface IncomeSourceRemoteDataSource {
    fun observe(uid: String): Flow<List<IncomeSource>>
    suspend fun write(source: IncomeSource): IncomeSource
}

class FirestoreIncomeSourceDataSource @Inject constructor(private val firestore: FirestoreDataSource) : IncomeSourceRemoteDataSource {
    override fun observe(uid: String): Flow<List<IncomeSource>> = callbackFlow {
        val listener = firestore.getCollection("users/$uid/income_sources")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.data?.toIncomeSource(it.id, uid) })
            }
        awaitClose { listener.remove() }
    }
    override suspend fun write(source: IncomeSource): IncomeSource = withTimeoutOrNull(30_000) {
        val doc = firestore.getCollection("users/${source.userId}/income_sources").document(source.id)
        doc.firestore.runTransaction { tx ->
            val snapshot = tx.get(doc)
            val old = snapshot.data?.toIncomeSource(source.id, source.userId)
            require(!snapshot.exists() || old != null) { "Malformed income source" }
            val selected = if (old != null && old.updatedAt >= source.updatedAt) old else source
            val merged = selected.copy(createdAt = old?.createdAt ?: source.createdAt,
                lastConfirmedMonth = IncomePolicy.latestMonth(old?.lastConfirmedMonth, source.lastConfirmedMonth), pendingSync = false)
            tx.set(doc, merged.toRemoteMap())
            merged
        }.await()
    } ?: throw IOException("Income sync timed out")
}
