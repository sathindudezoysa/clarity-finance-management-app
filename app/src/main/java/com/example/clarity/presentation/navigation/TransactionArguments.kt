package com.example.clarity.presentation.navigation

import androidx.lifecycle.SavedStateHandle

const val TRANSACTION_ID_ARGUMENT = "id"

/** Validate runtime types too: restored/navigation state is not guaranteed to contain a String. */
fun SavedStateHandle.transactionIdArgument(): String? = (get<Any?>(TRANSACTION_ID_ARGUMENT) as? String)
    ?.takeIf { it.isNotBlank() && it.length <= 128 && '/' !in it }
