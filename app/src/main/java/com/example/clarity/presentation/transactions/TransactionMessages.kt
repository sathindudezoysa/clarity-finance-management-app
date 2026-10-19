package com.example.clarity.presentation.transactions

object TransactionMessages {
    const val NOT_FOUND = "Transaction not found."
    const val SIGN_IN = "Please sign in."
    const val LOAD_ERROR = "Unable to sync transactions. Your saved local data is still available."
    const val SAVE_ERROR = "Unable to save this transaction. Please try again."
    const val DELETE_ERROR = "Unable to delete this transaction. Please try again."
    const val AMOUNT_INVALID = "Enter a number within the supported range and currency precision (JPY: 0, KWD: 3, others: 2 decimal places)."
    const val AMOUNT_POSITIVE = "Amount must be greater than zero."
    const val TAG_LONG = "Tag must be at most 100 characters."
    const val DESCRIPTION_LONG = "Description must be at most 2000 characters."
    const val ACCOUNT_SELECT = "Select a bank account."
    const val ACCOUNT_REQUIRED = "Create a bank account before saving a transaction."
    const val ACCOUNT_UNASSIGNED = "No account assigned"
    const val ACCOUNT_UNAVAILABLE = "Account unavailable. Choose another account when editing."
    const val ACCOUNTS_LOAD_ERROR = "Unable to load or sync bank accounts. Saved accounts are still available."
    const val CURRENCY_RETRY_FAILED = "Could not queue conversion. Try again."
    const val SAVE_LABEL = "Save"
    const val BACK_LABEL = "Back"
}

