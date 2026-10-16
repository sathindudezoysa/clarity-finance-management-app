package com.example.clarity.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.clarity.domain.model.ProofPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

data class ImportedProof(val file: File, val displayName: String, val mimeType: String)

class ProofFileStore @Inject constructor(@ApplicationContext private val context: Context) {
    fun file(uid: String, id: String): File {
        ProofPolicy.requireId(id)
        val owner = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.noBackupFilesDir, "proofs/$owner/$id").also { ensureParent(it) }
    }

    suspend fun import(uid: String, id: String, source: String): ImportedProof {
        val uri = Uri.parse(source)
        require(uri.scheme == "content") { "Choose a document using the file picker" }
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        require(mime in ProofPolicy.mimeTypes) { "Choose a photo or PDF" }
        var name = if (mime == "application/pdf") "Proof.pdf" else "Receipt photo"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) name = it.getString(index) ?: name
        }
        name = name.replace(Regex("[\\p{Cntrl}/\\\\]"), "_").trim().take(120).ifBlank { "Proof" }
        val target = file(uid, id)
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "This file is unavailable" }.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= ProofPolicy.MAX_BYTES) { "Choose a file smaller than 10 MB" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            ProofPolicy.validate(mime, target.length())
            val header = target.inputStream().use { input -> ByteArray(64).also { input.read(it) } }
            require(ProofFileSignature.matches(mime, header)) { "The file contents do not match its photo or PDF type" }
            return ImportedProof(target, name, mime)
        } catch (error: Exception) { target.delete(); throw error }
    }

    private fun ensureParent(file: File) {
        val parent = requireNotNull(file.parentFile) { "Proof directory is unavailable" }
        check(parent.isDirectory || parent.mkdirs()) { "Could not create proof directory" }
    }

    fun prunePreviews() = ProofPreviewCache.prune(File(context.cacheDir, "proof-previews"))

    fun share(uid: String, id: String, mimeType: String, displayName: String): String {
        val source = file(uid, id)
        require(source.isFile) { "This proof is unavailable" }
        val owner = requireNotNull(source.parentFile) { "Proof directory is unavailable" }.name
        val extension = when (mimeType) {
            "application/pdf" -> "pdf"; "image/jpeg" -> "jpg"; "image/png" -> "png"
            "image/webp" -> "webp"; "image/gif" -> "gif"; "image/heic" -> "heic"; "image/heif" -> "heif"
            else -> error("Unsupported proof type")
        }
        val target = File(context.cacheDir, "proof-previews/$owner/$id.$extension")
        val root = File(context.cacheDir, "proof-previews")
        ProofPreviewCache.prune(root, keep = target)
        ensureParent(target)
        if (!target.isFile || target.length() != source.length() || target.lastModified() < source.lastModified()) {
            source.copyTo(target, overwrite = true)
        }
        target.setLastModified(System.currentTimeMillis())
        ProofPreviewCache.prune(root, keep = target)
        return FileProvider.getUriForFile(context, "${context.packageName}.proofs", target, displayName).toString()
    }
}
