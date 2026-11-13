package com.example.clarity.presentation.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.clarity.domain.model.TransactionType

/** Keys of the per-widget Glance state: only the selected Expense/Income tab. */
object QuickEntryKeys {
    val type = stringPreferencesKey("type")
    /** Bumped on every refresh so the widget reloads its balance and categories. */
    val refreshTick = longPreferencesKey("refresh_tick")
}

internal fun Preferences.toQuickEntryType(): TransactionType =
    this[QuickEntryKeys.type]?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        ?: TransactionType.EXPENSE

/** UI state for the amount pop-up shown when a widget category is tapped. */
data class QuickAmountState(
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "",
    val currency: String = "",
    val amount: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val signedOut: Boolean = false
)
