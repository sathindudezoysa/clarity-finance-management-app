package com.example.clarity.domain.model

/** A category offered by the home screen quick entry widget, with its remembered amount. */
data class WidgetCategory(
    val name: String,
    val lastAmount: String? = null
)

data class WidgetWalletSummary(
    val currency: String,
    val balance: String
)
