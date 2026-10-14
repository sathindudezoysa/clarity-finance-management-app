package com.example.clarity.domain.model

import java.util.UUID

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
