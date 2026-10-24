package com.example.clarity.data.remote

import com.example.clarity.data.mapper.toFinancialGoal
import com.example.clarity.data.mapper.toRemoteMap
import com.example.clarity.domain.model.FinancialGoal
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject

interface GoalRemoteDataSource {
    fun observe(uid: String): Flow<List<FinancialGoal>>
    suspend fun write(goal: FinancialGoal): FinancialGoal
}

class FirestoreGoalDataSource @Inject constructor(
    private val firestore: FirestoreDataSource
) : GoalRemoteDataSource {

    override fun observe(uid: String): Flow<List<FinancialGoal>> = callbackFlow {
        val listener = firestore.getCollection("users/$uid/goals")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.data?.toFinancialGoal(it.id, uid) })
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun write(goal: FinancialGoal): FinancialGoal = withTimeoutOrNull(30_000) {
        val doc = firestore.getCollection("users/${goal.userId}/goals").document(goal.id)
        doc.firestore.runTransaction { tx ->
            val snapshot = tx.get(doc)
            val remote = snapshot.data?.toFinancialGoal(goal.id, goal.userId)
            require(!snapshot.exists() || remote != null) { "Malformed financial goal" }
            if (remote != null && remote.updatedAt > goal.updatedAt) {
                remote
            } else {
                val saved = goal.copy(
                    userId = remote?.userId ?: goal.userId,
                    currency = remote?.currency ?: goal.currency,
                    createdAt = remote?.createdAt ?: goal.createdAt,
                    pendingSync = false
                )
                tx.set(doc, saved.toRemoteMap())
                saved
            }
        }.await()
    } ?: throw IOException("Goal sync timed out")
}
