package com.example.clarity.data.sync

import android.content.Context
import androidx.work.*
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.remote.BudgetRemoteDataSource
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetSyncManager @Inject constructor(
    @ApplicationContext private val context: Context, private val db: ClarityDatabase,
    private val auth: AuthRepository, private val remote: BudgetRemoteDataSource,
    private val processor: BudgetSyncProcessor, private val signals: BudgetSignals,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val started = AtomicBoolean()
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            auth.currentUser.distinctUntilChangedBy { it?.uid }.collectLatest { user ->
                if (user == null) return@collectLatest
                val work = WorkManager.getInstance(context)
                val name = "budget-${user.uid}"
                try {
                    coroutineScope {
                        launch {
                            remote.observe(user.uid).onEach { processor.reconcile(user.uid, it) }
                                .retryWhen { error, attempt ->
                                    if (error is CancellationException) throw error
                                    signals.failed(user.uid, true)
                                    delay((attempt.coerceAtMost(5) + 1) * 10_000); true
                                }.collect()
                        }
                        launch {
                            var previous = emptySet<Pair<String, Long>>()
                            db.budgetDao.observe(user.uid).collect { rows ->
                                val pending = rows.filter { it.pendingSync }.map { it.id to it.updatedAt }.toSet()
                                if ((pending - previous).isNotEmpty()) work.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE,
                                    OneTimeWorkRequestBuilder<BudgetUploadWorker>().setInputData(workDataOf("owner" to user.uid))
                                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
                                previous = pending
                            }
                        }
                    }
                } finally { work.cancelUniqueWork(name) }
            }
        }
    }
}
