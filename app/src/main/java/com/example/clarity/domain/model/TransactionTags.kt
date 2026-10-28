package com.example.clarity.domain.model

import java.security.MessageDigest
import java.util.Locale

data class CategoryTag(
    val id: String,
    val userId: String,
    val name: String,
    val type: TransactionType,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

/** Default and custom category helpers for Expense and Income transactions. */
object TransactionTags {
    val defaultExpenseCategories: List<String> = listOf(
        "General",
        "Groceries",
        "Food",
        "Travel",
        "Bills",
        "Health",
        "Shopping",
        "Savings"
    )

    val defaultIncomeCategories: List<String> = listOf(
        "Salary",
        "Freelance",
        "Business",
        "Investment",
        "Savings",
        "Bonus",
        "Gift",
        "General"
    )

    val predefined: List<String> =
        (defaultExpenseCategories + defaultIncomeCategories).distinctBy { key(it) }

    fun defaultsFor(type: TransactionType): List<String> = when (type) {
        TransactionType.EXPENSE -> defaultExpenseCategories
        TransactionType.INCOME -> defaultIncomeCategories
    }

    fun isDefault(type: TransactionType, value: String): Boolean {
        val k = key(value)
        return defaultsFor(type).any { key(it) == k }
    }

    fun normalize(value: String): String = value.trim().ifBlank { "General" }

    fun key(value: String): String = normalize(value).lowercase(Locale.ROOT)

    fun tagId(type: TransactionType, name: String): String {
        val raw = "${type.name}:${key(name)}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return "${type.name.lowercase(Locale.ROOT)}_" + digest.joinToString("") { "%02x".format(it) }.take(40)
    }
}
