package com.example.clarity

import com.example.clarity.data.local.ProofPreviewCache
import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.data.sync.ProofUploadChanges
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class ProofSyncReviewTest {
    @get:Rule val temporary = TemporaryFolder()
    private val row = TransactionProofEntity("owner", "transaction", "proof", "Receipt.pdf", "application/pdf", 100, 1, null, true, 1)

    @Test fun acknowledgementsDoNotScheduleRedundantWorkButNewVerificationDoes() {
        val tracker = ProofUploadChanges()
        val second = row.copy(id = "second")
        assertTrue(tracker.hasNewWork(listOf(row, second)))
        assertFalse(tracker.hasNewWork(listOf(row, second)))
        assertFalse(tracker.hasNewWork(listOf(second)))
        assertTrue(tracker.hasNewWork(listOf(second.copy(revision = 2, verifiedAmountMinor = 1250))))
        assertFalse(tracker.hasNewWork(emptyList()))
        assertTrue(tracker.hasNewWork(listOf(row)))
    }
    @Test fun independentOwnersNeverShareAnUploadGeneration() {
        val tracker = ProofUploadChanges()
        assertTrue(tracker.hasNewWork(listOf(row)))
        assertTrue(tracker.hasNewWork(listOf(row.copy(userId = "other"))))
    }
    @Test fun stalePreviewsExpireWhileTheCurrentlyOpenedFileRemainsAvailable() {
        val root = temporary.newFolder("previews")
        val now = System.currentTimeMillis()
        val old = File(root, "old.pdf").apply { writeText("old"); setLastModified(now - ProofPreviewCache.MAX_AGE_MILLIS - 1000) }
        val active = File(root, "active.pdf").apply { writeText("current"); setLastModified(old.lastModified()) }
        val fresh = File(root, "fresh.pdf").apply { writeText("fresh"); setLastModified(now) }
        ProofPreviewCache.prune(root, keep = active, now = now)
        assertFalse(old.exists())
        assertEquals("current", active.readText())
        assertTrue(fresh.isFile)
    }
    @Test fun previewBudgetRemovesOldestCopiesAndPreservesOriginalEvidenceOutsideCache() {
        val root = temporary.newFolder("previews")
        val original = temporary.newFile("private-proof").apply { writeText("original evidence") }
        val now = System.currentTimeMillis()
        val files = (0..5).map { index -> File(root, "$index.pdf").apply {
            RandomAccessFile(this, "rw").use { it.setLength(10L * 1024 * 1024) }
            setLastModified(now - (10 - index) * 1000)
        } }
        ProofPreviewCache.prune(root, keep = files.last(), now = now)
        assertFalse(files.first().exists())
        assertTrue(files.last().exists())
        assertTrue(root.listFiles()!!.sumOf { it.length() } <= ProofPreviewCache.MAX_BYTES)
        assertEquals("original evidence", original.readText())
    }
    @Test fun manySmallPreviewsAreBoundedByCountAsWellAsBytes() {
        val root = temporary.newFolder("previews")
        repeat(ProofPreviewCache.MAX_FILES + 10) { File(root, "$it.pdf").writeText("small") }
        ProofPreviewCache.prune(root)
        assertTrue(root.listFiles()!!.size <= ProofPreviewCache.MAX_FILES)
    }
}
