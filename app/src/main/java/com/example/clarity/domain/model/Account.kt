package com.example.clarity.domain.model

import java.util.UUID

enum class AccountError {
    SIGN_IN_REQUIRED, OWNER_CHANGE, NAME_BLANK, NAME_DUPLICATE, FIELD_TOO_LONG,
    CURRENCY_INVALID, DEFAULT_REQUIRED, NOT_FOUND, ALREADY_EXISTS
}

class AccountException(val error: AccountError, message: String? = null) : Exception(message)

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String,
    val bankName: String,
    val accountType: String? = null,
    val currency: String = "LKR",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
