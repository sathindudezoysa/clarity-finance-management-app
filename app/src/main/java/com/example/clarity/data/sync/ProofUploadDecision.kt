package com.example.clarity.data.sync

import com.example.clarity.domain.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

enum class ProofUploadDecision { COMPLETE, RETRY }

/** Null may be a restoring session. Only a known different owner can retire this owner's job. */
suspend fun proofUploadDecision(uid: String, users: Flow<User?>, upload: suspend () -> Boolean): ProofUploadDecision {
    fun decision(user: User?, complete: Boolean): ProofUploadDecision = when {
        user == null -> ProofUploadDecision.RETRY
        user.uid != uid || complete -> ProofUploadDecision.COMPLETE
        else -> ProofUploadDecision.RETRY
    }
    return try {
        val initial = users.first()
        if (initial?.uid != uid) decision(initial, false)
        else {
            val complete = upload()
            decision(users.first(), complete)
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        // Do not drop work when auth restoration itself temporarily fails.
        ProofUploadDecision.RETRY
    }
}

