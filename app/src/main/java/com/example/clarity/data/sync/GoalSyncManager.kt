package com.example.clarity.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.remote.GoalRemoteDataSource
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.model.GoalProgressOperationState
import com.example.clarity.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ClarityDatabase,
    private val auth: AuthRepository,
    private val remote: GoalRemoteDataSource,
    private val processor: GoalSyncProcessor,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val started = AtomicBoolean()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            auth.currentUser.distinctUntilChangedBy { it?.uid }.collectLatest { user ->
                if (user == null) return@collectLatest
                val work = WorkManager.getInstance(context)
                val name = workNameForUser(user.uid)
                try {
                    coroutineScope {
                        launch {
                            remote.observe(user.uid)
                                .onEach { processor.reconcile(user.uid, it) }
                                .retryWhen { error, attempt ->
                                    if (error is CancellationException) throw error
                                    Log.w("GoalSync", "Goal listener reconnecting", error)
                                    delay((attempt.coerceAtMost(5) + 1) * 10_000)
                                    true
                                }
                                .collect()
                        }
                        launch {
                            var previous = emptySet<String>()
                            combine(
                                db.goalDao.observe(user.uid),
                                db.goalProgressOperationDao.observeByUser(user.uid)
                            ) { goals, ops ->
                                val pendingGoals = goals
                                    .filter { it.pendingSync }
                                    .map { "goal:${it.id}:${it.updatedAt}" }
                                val pendingOps = ops
                                    .filter { it.state == GoalProgressOperationState.PENDING.name }
                                    .map { "op:${it.id}" }
                                (pendingGoals + pendingOps).toSet()
                            }.retryWhen { error, _ ->
                                if (error is CancellationException) throw error
                                delay(5_000)
                                true
                            }.collect { pending ->
                                if ((pending - previous).isNotEmpty()) {
                                    enqueueUpload(work, name, user.uid)
                                }
                                previous = pending
                            }
                        }
                    }
                } finally {
                    work.cancelUniqueWork(name)
                }
            }
        }
    }

    fun triggerSync(uid: String) {
        val work = WorkManager.getInstance(context)
        enqueueUpload(work, workNameForUser(uid), uid)
    }

    private fun enqueueUpload(work: WorkManager, name: String, uid: String) {
        work.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<GoalUploadWorker>()
                .setInputData(workDataOf("owner" to uid))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
        )
    }

    companion object {
        fun workNameForUser(uid: String): String =
            "goal-" + MessageDigest.getInstance("SHA-256")
                .digest(uid.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

