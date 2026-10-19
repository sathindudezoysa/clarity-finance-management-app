package com.example.clarity.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.ProofFileStore
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProofSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ClarityDatabase,
    private val auth: AuthRepository,
    private val files: ProofFileStore,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val started = AtomicBoolean()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch {
            runCatching { files.prunePreviews() }.onFailure { Log.w("ProofSync", "Preview cleanup failed", it) }
            auth.currentUser.distinctUntilChangedBy { it?.uid }.collectLatest { user ->
                if (user == null) return@collectLatest
                val work = WorkManager.getInstance(context)
                val name = workName(user.uid)
                val changes = ProofUploadChanges()
                try {
                    database.transactionProofDao.observePending(user.uid).retryWhen { error, _ ->
                        if (error is CancellationException) throw error
                        Log.w("ProofSync", "Outbox observation failed; reconnecting", error)
                        delay(5_000)
                        true
                    }.collect { pending ->
                        if (changes.hasNewWork(pending)) {
                            // A successor handles changes arriving as a running worker finishes.
                            // Acknowledgements alone do not enqueue more jobs.
                            work.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request(user.uid))
                        }
                    }
                } finally { work.cancelUniqueWork(name) }
            }
        }
    }

    companion object {
        fun workName(uid: String): String = "proof-upload-" + MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }

        fun request(uid: String): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ProofUploadWorker>()
            .setInputData(workDataOf(ProofUploadWorker.OWNER_UID to uid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
    }
}
