package com.example.clarity.domain.repository

import com.example.clarity.domain.model.TransactionType

interface WidgetQuickEntryRepository {
    fun lastAmount(type: TransactionType, category: String): String?
    fun saveLastAmount(type: TransactionType, category: String, amount: String)
    /** Last category names shown for [type], used to render the widget instantly. */
    fun cachedCategories(type: TransactionType): List<String>
    fun saveCategories(type: TransactionType, names: List<String>)
}
