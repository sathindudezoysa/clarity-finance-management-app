package com.example.clarity.data.remote

import android.util.Log
import com.example.clarity.data.mapper.mapFirestoreDocuments
import com.example.clarity.data.mapper.toFirestoreLong
import com.example.clarity.data.mapper.toAccount
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.domain.model.Account
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AccountSnapshot(
    val accounts: List<Account>,
    val authoritative: Boolean,
    val remoteIds: Set<String> = accounts.map { it.id }.toSet()
)

interface AccountRemoteDataSource {
    fun observeAccounts(userId: String): Flow<AccountSnapshot>
    suspend fun writeAccounts(userId: String, accounts: List<Account>)
}

class FirestoreAccountDataSource @Inject constructor(
    private val firestore: FirestoreDataSource
) : AccountRemoteDataSource {
    override fun observeAccounts(userId: String): Flow<AccountSnapshot> = callbackFlow {
        val registration = firestore.getCollection("users/$userId/accounts")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) {
                    val documents = snapshot.documents.map { it.id to it.data }
                    val accounts = documents.mapFirestoreDocuments(
                        { data, id -> data.toAccount(id)?.takeIf { it.userId == userId } },
                        { id -> Log.w("AccountRemote", "Skipping malformed account document $id") }
                    )
                    // A malformed document must not look like an authoritative deletion.
                    trySend(AccountSnapshot(
                        accounts,
                        !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites(),
                        documents.map { it.first }.toSet()
                    ))
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun writeAccounts(userId: String, accounts: List<Account>) {
        if (accounts.isEmpty()) return
        require(accounts.all { it.userId == userId }) { "Cannot change account owner" }
        val collection = firestore.getCollection("users/$userId/accounts")
        // Both the old and new default are committed together, including offline retries.
        val batch = collection.firestore.batch()
        val selected = accounts.singleOrNull { it.isDefault }
        if (selected != null) {
            val selection = firestore.getCollection("users/$userId/account_settings").document("default")
            // Room is the offline queue. Read the current pointer before building the batch;
            // getAfter rules reject a concurrent stale switch, which the outbox retries.
            val previousId = selection.get(Source.SERVER).await().data?.get("accountId") as? String
            if (previousId != null && previousId != selected.id && accounts.none { it.id == previousId }) {
                val previous = collection.document(previousId).get(Source.SERVER).await()
                batch.update(collection.document(previousId), "isDefault", false,
                    "updatedAt", maxOf(selected.updatedAt, previous.data?.get("updatedAt").toFirestoreLong() ?: 0L))
            }
            batch.set(selection, mapOf("accountId" to selected.id))
        }
        accounts.forEach { batch.set(collection.document(it.id), it.toFirestoreMap()) }
        batch.commit().await()
    }
}
