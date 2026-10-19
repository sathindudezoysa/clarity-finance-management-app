package com.example.clarity.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetSignals @Inject constructor() {
    val errors = MutableStateFlow<Set<String>>(emptySet())
    val refresh = MutableStateFlow(0L)
    fun failed(uid: String, failed: Boolean) { errors.update { if (failed) it + uid else it - uid } }
    fun refresh() { refresh.update { it + 1 } }
}
