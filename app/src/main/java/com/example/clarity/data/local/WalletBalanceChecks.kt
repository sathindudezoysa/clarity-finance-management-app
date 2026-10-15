package com.example.clarity.data.local

import com.example.clarity.data.local.dao.WalletBalanceReader
import com.example.clarity.domain.model.walletRequire
import java.math.BigInteger

/** Call inside the same Room transaction as the write, including ordinary cash edits/deletes. */
suspend fun checkWalletBalance(dao: WalletBalanceReader, uid: String) = validateWalletBalance(dao.balance(uid).value())

fun validateWalletBalance(balance: BigInteger) {
    walletRequire(balance >= BigInteger.ZERO, "Your cash balance is not enough. Add cash or reduce the amount.")
    walletRequire(balance <= BigInteger.valueOf(Long.MAX_VALUE), "The wallet balance is too large. Use a smaller amount.")
}
