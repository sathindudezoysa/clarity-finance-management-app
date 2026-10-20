package com.example.clarity.data.sync

import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetAlertManager @Inject constructor(
    private val budgets: BudgetRepository, private val auth: AuthRepository,
    private val notifications: BudgetNotificationDelivery, private val evaluator: BudgetAlertEvaluator, private val signals: BudgetSignals,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val started = AtomicBoolean()
    fun start() {
        if (!started.compareAndSet(false, true)) return
        // Evaluation is local and independent of WorkManager's network constraint.
        // Resolve the current device timezone on each refresh, including after timezone/clock changes.
        scope.launch { while (isActive) { signals.refresh(); delay(30_000) } }
        scope.launch {
            auth.currentUser.distinctUntilChangedBy { it?.uid }.collectLatest { user ->
                if (user == null) return@collectLatest
                try {
                    budgets.observe().retryWhen { error, _ ->
                        if (error is CancellationException) throw error
                        delay(5_000); true
                    }.collect { overview ->
                        if (overview.ownerId != user.uid || auth.currentUser.first()?.uid != user.uid) return@collect
                        evaluator.evaluate(overview)
                    }
                } finally { notifications.cancelOwner(user.uid) }
            }
        }
    }
}
