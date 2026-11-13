package com.example.clarity.data.repository

import android.content.SharedPreferences
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.WidgetQuickEntryRepository
import javax.inject.Inject

class WidgetQuickEntryRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences
) : WidgetQuickEntryRepository {

    override fun lastAmount(type: TransactionType, category: String): String? =
        prefs.getString(key(type, category), null)

    override fun saveLastAmount(type: TransactionType, category: String, amount: String) {
        prefs.edit().putString(key(type, category), amount).apply()
    }

    override fun cachedCategories(type: TransactionType): List<String> =
        prefs.getString("widget_categories_${type.name}", null)
            ?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    override fun saveCategories(type: TransactionType, names: List<String>) {
        prefs.edit().putString("widget_categories_${type.name}", names.joinToString(SEPARATOR)).apply()
    }

    private fun key(type: TransactionType, category: String) =
        "widget_last_amount_${type.name}_${TransactionTags.key(category)}"

    private companion object {
        const val SEPARATOR = "\u001F"
    }
}
