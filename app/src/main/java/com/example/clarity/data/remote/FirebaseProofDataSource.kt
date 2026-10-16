package com.example.clarity.data.remote

import android.net.Uri
import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.domain.model.ProofPolicy
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

class FirebaseProofDataSource @Inject constructor() : ProofRemoteDataSource {
    // Lazy resolution lets the offline queue work before a Storage bucket is provisioned.
    private val storage get() = FirebaseStorage.getInstance()
    private fun folder(uid: String, transactionId: String): String {
        ProofPolicy.requireId(uid); ProofPolicy.requireId(transactionId)
        return "users/$uid/transactions/$transactionId/proofs"
    }
    private fun reference(row: TransactionProofEntity) = storage.reference.child(folder(row.userId, row.transactionId)).child(row.id)

    override suspend fun upload(proof: TransactionProofEntity, file: File): Unit = request {
        ProofPolicy.requireId(proof.id)
        val metadata = StorageMetadata.Builder().setContentType(proof.mimeType)
            .setCustomMetadata("displayName", proof.displayName)
            .setCustomMetadata("createdAt", proof.createdAt.toString())
            .setCustomMetadata("revision", proof.revision.toString())
            .setCustomMetadata("verifiedAmountMinor", proof.verifiedAmountMinor?.toString().orEmpty()).build()
        val task = reference(proof).putFile(Uri.fromFile(file), metadata)
        try { task.await() } catch (cancel: CancellationException) { task.cancel(); throw cancel }
        Unit
    }

    override suspend fun list(uid: String, transactionId: String): List<TransactionProofEntity> = request {
        val items = mutableListOf<TransactionProofEntity>()
        var token: String? = null
        do {
            val page = if (token == null) storage.reference.child(folder(uid, transactionId)).list(100).await()
                else storage.reference.child(folder(uid, transactionId)).list(100, token).await()
            for (ref in page.items) {
                val metadata = ref.metadata.await()
                val mime = metadata.contentType.orEmpty()
                // Treat malformed legacy objects as unavailable rather than granting a viewer arbitrary content.
                if (mime !in ProofPolicy.mimeTypes || metadata.sizeBytes !in 1..ProofPolicy.MAX_BYTES) continue
                if (!ref.name.matches(Regex("[A-Za-z0-9_-]{1,128}"))) continue
                val created = metadata.getCustomMetadata("createdAt")?.toLongOrNull() ?: continue
                val revision = metadata.getCustomMetadata("revision")?.toLongOrNull() ?: continue
                val verified = metadata.getCustomMetadata("verifiedAmountMinor")?.toLongOrNull()?.takeIf { it > 0 }
                val name = metadata.getCustomMetadata("displayName")?.take(120) ?: "Proof"
                items += TransactionProofEntity(uid, transactionId, ref.name, name, mime, metadata.sizeBytes,
                    created, verified, false, revision)
            }
            token = page.pageToken
        } while (token != null)
        items
    }

    override suspend fun download(proof: TransactionProofEntity, destination: File): Unit = request {
        val bytes = reference(proof).getBytes(ProofPolicy.MAX_BYTES).await()
        ProofPolicy.validate(proof.mimeType, bytes.size.toLong())
        require(com.example.clarity.data.local.ProofFileSignature.matches(proof.mimeType, bytes)) { "Invalid proof contents" }
        val temporary = File(destination.parentFile, destination.name + "." + java.util.UUID.randomUUID() + ".download")
        try {
            temporary.writeBytes(bytes)
            check(temporary.renameTo(destination)) { "Could not save proof" }
        } finally { temporary.delete() }
        Unit
    }
    private suspend fun <T : Any> request(block: suspend () -> T): T =
        withTimeoutOrNull(30_000) { block() } ?: throw java.io.IOException("Storage request timed out")
}
