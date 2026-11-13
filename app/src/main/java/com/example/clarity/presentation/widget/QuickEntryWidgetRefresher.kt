package com.example.clarity.presentation.widget

import android.content.Context
import com.example.clarity.di.ApplicationScope
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the widget header balance and categories fresh when transactions change in the app. */
@Singleton
class QuickEntryWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getTransactions: GetTransactionsUseCase,
    private val observeUser: com.example.clarity.domain.usecase.auth.ObserveCurrentUserUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    /** Refreshes the widget from the application scope so it survives the pop-up closing. */
    fun refreshNow() {
        scope.launch { runCatching { QuickEntryWidget.updateAll(context) } }
    }

    @OptIn(FlowPreview::class)
    fun start() {
        // Sign in / sign out changes what the widget may show.
        scope.launch {
            observeUser().map { it?.uid }.distinctUntilChanged().catch { }.collect {
                runCatching { QuickEntryWidget.updateAll(context) }
            }
        }
        scope.launch {
            getTransactions().debounce(500).catch { }.collect {
                runCatching { QuickEntryWidget.updateAll(context) }
            }
        }
    }
}
