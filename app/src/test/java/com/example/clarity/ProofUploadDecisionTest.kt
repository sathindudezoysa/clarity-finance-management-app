package com.example.clarity

import com.example.clarity.data.sync.ProofUploadDecision
import com.example.clarity.data.sync.proofUploadDecision
import com.example.clarity.domain.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProofUploadDecisionTest {
    private val user = User("owner", null, null)

    @Test fun restoringAuthRetriesThenUploadsAfterRestoration() = runBlocking {
        val users = MutableStateFlow<User?>(null)
        var calls = 0
        assertEquals(ProofUploadDecision.RETRY, proofUploadDecision("owner", users) { calls++; true })
        assertEquals(0, calls)
        users.value = user
        assertEquals(ProofUploadDecision.COMPLETE, proofUploadDecision("owner", users) { calls++; true })
        assertEquals(1, calls)
    }
    @Test fun anotherOwnerRetiresWorkWithoutUploading() = runBlocking {
        assertEquals(ProofUploadDecision.COMPLETE, proofUploadDecision("owner", MutableStateFlow(User("other", null, null))) { error("Must not upload") })
    }
    @Test fun authLossAfterUploadDoesNotDropWork() = runBlocking {
        val users = MutableStateFlow<User?>(user)
        assertEquals(ProofUploadDecision.RETRY, proofUploadDecision("owner", users) { users.value = null; true })
    }
    @Test fun transientUploadFailureRetries() = runBlocking {
        assertEquals(ProofUploadDecision.RETRY, proofUploadDecision("owner", MutableStateFlow(user)) { false })
    }
    @Test fun unexpectedFailureRetries() = runBlocking {
        assertEquals(ProofUploadDecision.RETRY, proofUploadDecision("owner", MutableStateFlow(user)) { throw java.io.IOException("Offline") })
    }
    @Test fun cancellationPropagates() = runBlocking {
        try {
            proofUploadDecision("owner", MutableStateFlow(user)) { throw CancellationException("Stopped") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
