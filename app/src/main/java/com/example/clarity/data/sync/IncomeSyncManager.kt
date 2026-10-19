package com.example.clarity.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.remote.IncomeSourceRemoteDataSource
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncomeSyncManager @Inject constructor(
    @ApplicationContext private val context: Context, private val db: ClarityDatabase,
    private val auth: AuthRepository, private val remote: IncomeSourceRemoteDataSource,
    private val processor: IncomeSyncProcessor, @ApplicationScope private val scope: CoroutineScope
) {
    private val started = AtomicBoolean()
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            auth.currentUser.distinctUntilChangedBy { it?.uid }.collectLatest { user ->
                if (user == null) return@collectLatest
                val work = WorkManager.getInstance(context)
                val name = "income-" + MessageDigest.getInstance("SHA-256").digest(user.uid.toByteArray()).joinToString("") { "%02x".format(it) }
                try {
                    coroutineScope {
                        launch {
                            remote.observe(user.uid).onEach { processor.reconcile(user.uid, it) }
                                .retryWhen { error, attempt ->
                                    if (error is CancellationException) throw error
                                    Log.w("IncomeSync", "Income listener reconnecting", error)
                                    delay((attempt.coerceAtMost(5) + 1) * 10_000); true
                                }.collect()
                        }
                        launch {
                            var previous = emptySet<Pair<String, Long>>()
                            db.incomeSourceDao.observe(user.uid).retryWhen { error, _ ->
                                if (error is CancellationException) throw error
                                delay(5_000); true
                            }.collect { rows ->
                                val pending = rows.filter { it.pendingSync }.map { it.id to it.updatedAt }.toSet()
                                if ((pending - previous).isNotEmpty()) {
                                    work.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE,
                                        OneTimeWorkRequestBuilder<IncomeUploadWorker>().setInputData(workDataOf("owner" to user.uid))
                                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
                                }
                                previous = pending
                            }
                        }
                    }
                } finally { work.cancelUniqueWork(name) }
            }
        }
    }
}
