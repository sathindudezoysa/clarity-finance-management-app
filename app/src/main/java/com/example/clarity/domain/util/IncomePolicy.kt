package com.example.clarity.domain.util

import com.example.clarity.domain.model.IncomeSource
import java.security.MessageDigest
import java.time.YearMonth

object IncomePolicy {
    fun validId(id: String) = id.isNotBlank() && id.length <= 128 && id != "." && id != ".." &&
        id.none { it == '/' || it == '\\' || it.isISOControl() }
    fun validate(source: IncomeSource) {
        require(validId(source.id) && validId(source.accountId))
        require(source.name.isNotBlank() && source.name.length <= 100 && source.name.none { it.isISOControl() })
        require(source.amountMinor > 0 && source.createdAt >= 0 && source.updatedAt >= source.createdAt)
        source.lastConfirmedMonth?.let { require(validMonth(it)) }
    }
    fun validMonth(value: String) = value.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")) && value.take(4) != "0000"
    fun latestMonth(a: String?, b: String?): String? = listOfNotNull(a, b).maxOrNull()
    fun transactionId(uid: String, sourceId: String, month: YearMonth): String {
        require(validId(uid) && validId(sourceId))
        val digest = MessageDigest.getInstance("SHA-256").digest("$uid\n$sourceId\n$month".toByteArray())
        return "income-" + digest.joinToString("") { "%02x".format(it) }
    }
}
