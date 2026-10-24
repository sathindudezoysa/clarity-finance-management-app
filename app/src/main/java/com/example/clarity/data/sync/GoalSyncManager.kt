package com.example.clarity.data.sync

import android.content.Context
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
import com.example.clarity.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class GoalSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ClarityDatabase,
    private val remote: GoalRemoteDataSource,
    private val processor: GoalSyncProcessor,
    private val auth: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            auth.currentUser.distinctUntilChangedBy { it?.uid }.collectLatest { user ->
                if (user != null) {
                    coroutineScope {
                        launch {
                            db.goalDao.observe(user.uid)
                                .map { goals -> goals.any { it.pendingSync } }
                                .distinctUntilChanged()
                                .collect { hasPending ->
                                    if (hasPending) requestUpload(user.uid)
                                }
                        }
                        launch {
                            while (true) {
                                try {
                                    remote.observe(user.uid).collect { remoteGoals ->
                                        processor.reconcile(user.uid, remoteGoals)
                                    }
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    delay(5_000)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun requestUpload(uid: String) {
        val request = OneTimeWorkRequestBuilder<GoalUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .setInputData(workDataOf("owner" to uid))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("goal-upload-$uid", ExistingWorkPolicy.REPLACE, request)
    }
}
