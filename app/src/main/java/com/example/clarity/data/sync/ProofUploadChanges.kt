package com.example.clarity.data.sync

import com.example.clarity.data.local.entity.TransactionProofEntity

/** Enqueue for new edits, not for the queue shrinking as a worker acknowledges uploads. */
class ProofUploadChanges {
    private val revisions = mutableMapOf<Triple<String, String, String>, Long>()

    fun hasNewWork(pending: List<TransactionProofEntity>): Boolean {
        val keys = pending.map { Triple(it.userId, it.transactionId, it.id) }.toSet()
        revisions.keys.retainAll(keys)
        val changed = pending.any { revisions[Triple(it.userId, it.transactionId, it.id)] != it.revision }
        pending.forEach { revisions[Triple(it.userId, it.transactionId, it.id)] = it.revision }
        return changed
    }
}
