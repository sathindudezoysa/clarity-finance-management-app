package com.example.clarity.domain.model

data class User(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String? = null,
    val preferredCurrency: String? = null,
    val isSetupComplete: Boolean = false
)
