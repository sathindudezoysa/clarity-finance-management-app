package com.example.clarity.domain.model

/** Shared choices for income and expenses, including the FR-2.2 examples. */
object TransactionTags {
    fun normalize(value: String): String = value.trim().ifBlank { "General" }
    fun key(value: String): String = normalize(value).lowercase(java.util.Locale.ROOT)
    val predefined: List<String> = listOf("General", "Food", "Travel", "Groceries", "Savings")
}
