package com.example.clarity.domain.util

import java.math.BigDecimal
import java.math.BigInteger

object MoneyUtils {
    fun parseMinor(text: String): Result<Long> = runCatching {
        val value = text.trim()
        require(value.matches(Regex("[+-]?[0-9]+(?:\\.[0-9]{1,2})?"))) {
            "Enter a number with at most two decimal places"
        }
        BigDecimal(value).movePointRight(2).longValueExact()
    }

    fun formatMinor(amountMinor: Long): String = BigDecimal.valueOf(amountMinor, 2).toPlainString()

    // Totals can exceed Long even though every individual stored amount fits in Long.
    fun formatMinor(amountMinor: BigInteger): String = BigDecimal(amountMinor, 2).toPlainString()
}
