package com.example.clarity.data.remote

import com.example.clarity.data.local.ProofFileSignature
import android.net.Uri
import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.domain.model.ProofPolicy
import com.example.clarity.domain.model.ProofStoragePlanException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

class FirebaseProofDataSource @Inject constructor(
    private val storage: FirebaseStorage
) : ProofRemoteDataSource {
    private fun folder(uid: String, transactionId: String): String {
        ProofPolicy.requireId(uid); ProofPolicy.requireId(transactionId)
        return "users/$uid/transactions/$transactionId/proofs"
    }
    private fun reference(row: TransactionProofEntity) = storage.reference.child(folder(row.userId, row.transactionId)).child(row.id)

    override suspend fun upload(proof: TransactionProofEntity, file: File): Unit = request {
        ProofPolicy.requireId(proof.id)
        val task = reference(proof).putFile(Uri.fromFile(file), metadata(proof))
        try {
            task.await()
        } catch (cancel: CancellationException) {
            task.cancel()
            throw cancel
        } catch (error: StorageException) {
            if (error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND ||
                error.httpResultCode == 404 ||
                error.httpResultCode == 402
            ) {
                throw ProofStoragePlanException(cause = error)
            }
            throw error
        }
        Unit
    }

    private fun metadata(proof: TransactionProofEntity) = StorageMetadata.Builder().setContentType(proof.mimeType)
            .setCustomMetadata("displayName", proof.displayName)
            .setCustomMetadata("createdAt", proof.createdAt.toString())
            .setCustomMetadata("revision", proof.revision.toString())
            .setCustomMetadata("verifiedAmountMinor", proof.verifiedAmountMinor?.toString().orEmpty()).build()

    override suspend fun updateMetadata(proof: TransactionProofEntity): Boolean = request {
        ProofPolicy.requireId(proof.id)
        try {
            reference(proof).updateMetadata(metadata(proof)).await()
            true
        } catch (error: StorageException) {
            if (error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) false else throw error
        }
    }

    override suspend fun list(uid: String, transactionId: String): List<TransactionProofEntity> = request {
        val items = mutableListOf<TransactionProofEntity>()
        var token: String? = null
        try {
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
        } catch (error: StorageException) {
            if (error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND ||
                error.httpResultCode == 404 ||
                error.httpResultCode == 402
            ) {
                throw ProofStoragePlanException(cause = error)
            }
            throw error
        }
        items
    }

    override suspend fun download(proof: TransactionProofEntity, destination: File): Unit = request {
        val ref = reference(proof)
        val metadata = ref.metadata.await()
        ProofPolicy.validate(proof.mimeType, metadata.sizeBytes)
        require(metadata.contentType == proof.mimeType) { "Unexpected proof type" }
        val parent = requireNotNull(destination.parentFile) { "Proof directory is unavailable" }
        check(parent.isDirectory || parent.mkdirs()) { "Could not create proof directory" }
        val temporary = File(parent, destination.name + "." + java.util.UUID.randomUUID() + ".download")
        val task = ref.getFile(temporary)
        task.addOnProgressListener { snapshot ->
            if (snapshot.bytesTransferred > ProofPolicy.MAX_BYTES || snapshot.totalByteCount > ProofPolicy.MAX_BYTES) task.cancel()
        }
        try {
            try { task.await() } catch (cancel: CancellationException) { task.cancel(); throw cancel }
            ProofPolicy.validate(proof.mimeType, temporary.length())
            val header = temporary.inputStream().use(ProofFileSignature::readHeader)
            require(ProofFileSignature.matches(proof.mimeType, header)) { "Invalid proof contents" }
            check(temporary.renameTo(destination)) { "Could not save proof" }
        } finally { temporary.delete() }
        Unit
    }
    private suspend fun <T : Any> request(block: suspend () -> T): T =
        withTimeoutOrNull(30_000) { block() } ?: throw java.io.IOException("Storage request timed out")
}
