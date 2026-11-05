package com.example.clarity.presentation.accounts.list

import com.example.clarity.domain.model.Account

data class AccountListState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val accountBalances: Map<String, String> = emptyMap(),
    val error: String? = null,
    val actionError: String? = null,
    val changingDefaultId: String? = null
)
