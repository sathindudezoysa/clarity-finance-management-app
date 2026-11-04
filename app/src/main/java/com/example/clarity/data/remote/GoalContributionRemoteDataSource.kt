package com.example.clarity.data.remote

import com.example.clarity.data.mapper.toGoalContribution
import com.example.clarity.data.mapper.toRemoteMap
import com.example.clarity.domain.model.GoalContribution
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject

interface GoalContributionRemoteDataSource {
    fun observe(uid: String): Flow<List<GoalContribution>>
    suspend fun write(contribution: GoalContribution): GoalContribution
}

class FirestoreGoalContributionDataSource @Inject constructor(
    private val firestore: FirestoreDataSource
) : GoalContributionRemoteDataSource {

    override fun observe(uid: String): Flow<List<GoalContribution>> = callbackFlow {
        val listener = firestore.getCollection("users/$uid/goal_contributions")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.data?.toGoalContribution(it.id, uid) })
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun write(contribution: GoalContribution): GoalContribution = withTimeoutOrNull(30_000) {
        val doc = firestore.getCollection("users/${contribution.userId}/goal_contributions").document(contribution.id)
        doc.firestore.runTransaction { tx ->
            val snapshot = tx.get(doc)
            val remote = snapshot.data?.toGoalContribution(contribution.id, contribution.userId)
            require(!snapshot.exists() || remote != null) { "Malformed goal contribution" }
            if (remote != null) {
                remote
            } else {
                val saved = contribution.copy(pendingSync = false)
                tx.set(doc, saved.toRemoteMap())
                saved
            }
        }.await()
    } ?: throw IOException("Goal contribution sync timed out")
}

