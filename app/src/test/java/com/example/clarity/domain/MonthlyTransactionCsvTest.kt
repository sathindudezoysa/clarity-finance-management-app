package com.example.clarity.domain

import com.example.clarity.domain.export.MonthlyTransactionCsv
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyTransactionCsvTest {

    private val colomboZone: ZoneId = ZoneId.of("Asia/Colombo")
    private val targetMonth: YearMonth = YearMonth.of(2026, 3)

    private fun tx(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        amountMinor: Long = 1000L,
        category: String = "Food",
        note: String = "Lunch",
        dateEpochMillis: Long = 1_772_323_200_000L,
        createdAt: Long = 100L,
        accountId: String? = "acc-1",
        currency: String = "LKR",
        targetCurrency: String = "LKR",
        conversion: ConversionSnapshot? = ConversionSnapshot(
            amountMinor = amountMinor,
            rate = "1",
            provider = "identity",
            rateAt = dateEpochMillis,
            fetchedAt = dateEpochMillis,
            cached = false
        )
    ): Transaction = Transaction(
        id = id,
        userId = "user-1",
        type = type,
        amountMinor = amountMinor,
        category = category,
        note = note,
        dateEpochMillis = dateEpochMillis,
        createdAt = createdAt,
        updatedAt = createdAt,
        accountId = accountId,
        currency = currency,
        targetCurrency = targetCurrency,
        conversion = conversion
    )

    @Test
    fun `recordsFor includes exact month boundaries in zone and excludes outside timestamps`() {
        // Asia/Colombo is UTC+05:30:
        // Start of 2026-03-01 00:00:00.000+05:30 is 2026-02-28T18:30:00.000Z (UTC month is Feb, local month is Mar!)
        val startOfMarchMillis = LocalDate.of(2026, 3, 1)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()
        val endOfMarchMillis = LocalDate.of(2026, 4, 1)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli() - 1L

        val justBeforeMarch = tx(id = "before", dateEpochMillis = startOfMarchMillis - 1L)
        val firstMilliOfMarch = tx(id = "first", dateEpochMillis = startOfMarchMillis)
        val lastMilliOfMarch = tx(id = "last", dateEpochMillis = endOfMarchMillis)
        val firstMilliOfApril = tx(id = "after", dateEpochMillis = endOfMarchMillis + 1L)

        val records = MonthlyTransactionCsv.recordsFor(
            transactions = listOf(firstMilliOfApril, lastMilliOfMarch, justBeforeMarch, firstMilliOfMarch),
            month = targetMonth,
            zone = colomboZone
        )

        assertEquals(listOf("first", "last"), records.map { it.id })
    }

    @Test
    fun `recordsFor keeps both INCOME and EXPENSE and sorts oldest first by date then createdAt then id`() {
        val baseMillis = LocalDate.of(2026, 3, 10)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()

        val t1 = tx(id = "b", type = TransactionType.EXPENSE, dateEpochMillis = baseMillis, createdAt = 10L)
        val t2 = tx(id = "a", type = TransactionType.INCOME, dateEpochMillis = baseMillis, createdAt = 10L)
        val t3 = tx(id = "c", type = TransactionType.EXPENSE, dateEpochMillis = baseMillis, createdAt = 5L)
        val t4 = tx(id = "d", type = TransactionType.INCOME, dateEpochMillis = baseMillis + 1000L, createdAt = 1L)

        val records = MonthlyTransactionCsv.recordsFor(
            transactions = listOf(t4, t1, t2, t3),
            month = targetMonth,
            zone = colomboZone
        )

        assertEquals(listOf("c", "a", "b", "d"), records.map { it.id })
    }

    @Test
    fun `build outputs exact header and CRLF endings`() {
        val march15Millis = LocalDate.of(2026, 3, 15)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()
        val record = tx(
            id = "tx-1",
            type = TransactionType.INCOME,
            amountMinor = 250000L,
            category = "Salary",
            note = "Monthly pay",
            dateEpochMillis = march15Millis
        )

        val csv = MonthlyTransactionCsv.build(
            records = listOf(record),
            accountLabel = { "Main Checking" },
            zone = colomboZone
        )

        assertEquals("clarity-transactions-2026-03.csv", MonthlyTransactionCsv.fileName(targetMonth))
        assertTrue(csv.endsWith("\r\n"))
        val lines = csv.split("\r\n")
        assertEquals(3, lines.size) // header, 1 row, trailing empty after final CRLF
        assertEquals(
            "Date,Type,Category,Account,Description,Amount,Currency,Converted Amount,Converted Currency,Exchange Rate,Conversion Status,Transaction ID",
            lines[0]
        )
        assertEquals(
            "2026-03-15,Income,Salary,Main Checking,Monthly pay,2500.00,LKR,2500.00,LKR,1,Same currency,tx-1",
            lines[1]
        )
        assertEquals("", lines[2])
    }

    @Test
    fun `build RFC 4180 quotes fields containing comma quote and newline`() {
        val march5Millis = LocalDate.of(2026, 3, 5)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()
        val record = tx(
            id = "tx-quote",
            type = TransactionType.EXPENSE,
            amountMinor = 1500L,
            category = "Food, Dining",
            note = "Ordered \"Special\"\nLine 2",
            dateEpochMillis = march5Millis
        )

        val csv = MonthlyTransactionCsv.build(
            records = listOf(record),
            accountLabel = { "My \"Primary\", Account" },
            zone = colomboZone
        )

        val expectedRow =
            "2026-03-05,Expense,\"Food, Dining\",\"My \"\"Primary\"\", Account\",\"Ordered \"\"Special\"\"\nLine 2\",-15.00,LKR,-15.00,LKR,1,Same currency,tx-quote"
        assertEquals(
            MonthlyTransactionCsv.HEADER + "\r\n" + expectedRow + "\r\n",
            csv
        )
    }

    @Test
    fun `build prefixes formula characters on Category Account and Description while leaving negative Amount untouched`() {
        val march8Millis = LocalDate.of(2026, 3, 8)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()
        val r1 = tx(
            id = "tx-1",
            type = TransactionType.EXPENSE,
            amountMinor = 1250L,
            category = "=SUM(A1:A2)",
            note = "-discount",
            dateEpochMillis = march8Millis,
            accountId = "acc-plus"
        )
        val r2 = tx(
            id = "tx-2",
            type = TransactionType.EXPENSE,
            amountMinor = 500L,
            category = "@mention",
            note = "+bonus, extra",
            dateEpochMillis = march8Millis,
            accountId = "acc-normal"
        )

        val csv = MonthlyTransactionCsv.build(
            records = listOf(r1, r2),
            accountLabel = { id -> if (id == "acc-plus") "+Bank" else "Safe" },
            zone = colomboZone
        )

        val lines = csv.split("\r\n")
        assertEquals(
            "2026-03-08,Expense,'=SUM(A1:A2),'+Bank,'-discount,-12.50,LKR,-12.50,LKR,1,Same currency,tx-1",
            lines[1]
        )
        assertEquals(
            "2026-03-08,Expense,'@mention,Safe,\"'+bonus, extra\",-5.00,LKR,-5.00,LKR,1,Same currency,tx-2",
            lines[2]
        )
    }

    @Test
    fun `build formats JPY with 0 decimals and KWD with 3 decimals with sign rules`() {
        val march12Millis = LocalDate.of(2026, 3, 12)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()
        val jpyExpense = tx(
            id = "tx-jpy",
            type = TransactionType.EXPENSE,
            amountMinor = 1500L,
            currency = "JPY",
            targetCurrency = "JPY",
            dateEpochMillis = march12Millis,
            conversion = ConversionSnapshot(
                amountMinor = 1500L,
                rate = "1",
                provider = "identity",
                rateAt = march12Millis,
                fetchedAt = march12Millis,
                cached = false
            )
        )
        val kwdIncome = tx(
            id = "tx-kwd",
            type = TransactionType.INCOME,
            amountMinor = 12345L,
            currency = "KWD",
            targetCurrency = "KWD",
            dateEpochMillis = march12Millis,
            conversion = ConversionSnapshot(
                amountMinor = 12345L,
                rate = "1",
                provider = "identity",
                rateAt = march12Millis,
                fetchedAt = march12Millis,
                cached = false
            )
        )

        val csv = MonthlyTransactionCsv.build(
            records = listOf(jpyExpense, kwdIncome),
            accountLabel = { "Cash Wallet" },
            zone = colomboZone
        )

        val lines = csv.split("\r\n")
        assertEquals(
            "2026-03-12,Expense,Food,Cash Wallet,Lunch,-1500,JPY,-1500,JPY,1,Same currency,tx-jpy",
            lines[1]
        )
        assertEquals(
            "2026-03-12,Income,Food,Cash Wallet,Lunch,12.345,KWD,12.345,KWD,1,Same currency,tx-kwd",
            lines[2]
        )
    }

    @Test
    fun `build outputs Pending Same currency and Converted correctly`() {
        val march20Millis = LocalDate.of(2026, 3, 20)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()
        val pendingTx = tx(
            id = "tx-pending",
            type = TransactionType.EXPENSE,
            amountMinor = 1000L,
            currency = "USD",
            targetCurrency = "LKR",
            dateEpochMillis = march20Millis,
            conversion = null
        )
        val sameCurrencyTx = tx(
            id = "tx-same",
            type = TransactionType.INCOME,
            amountMinor = 50000L,
            currency = "LKR",
            targetCurrency = "LKR",
            dateEpochMillis = march20Millis,
            conversion = ConversionSnapshot(
                amountMinor = 50000L,
                rate = "1",
                provider = "identity",
                rateAt = march20Millis,
                fetchedAt = march20Millis,
                cached = false
            )
        )
        val convertedTx = tx(
            id = "tx-conv",
            type = TransactionType.EXPENSE,
            amountMinor = 1000L,
            currency = "USD",
            targetCurrency = "LKR",
            dateEpochMillis = march20Millis,
            conversion = ConversionSnapshot(
                amountMinor = 300000L,
                rate = "300.00",
                provider = "openexchangerates",
                rateAt = march20Millis,
                fetchedAt = march20Millis,
                cached = true
            )
        )

        val csv = MonthlyTransactionCsv.build(
            records = listOf(pendingTx, sameCurrencyTx, convertedTx),
            accountLabel = { "Main" },
            zone = colomboZone
        )

        val lines = csv.split("\r\n")
        assertEquals(
            "2026-03-20,Expense,Food,Main,Lunch,-10.00,USD,,,,Pending,tx-pending",
            lines[1]
        )
        assertEquals(
            "2026-03-20,Income,Food,Main,Lunch,500.00,LKR,500.00,LKR,1,Same currency,tx-same",
            lines[2]
        )
        assertEquals(
            "2026-03-20,Expense,Food,Main,Lunch,-10.00,USD,-3000.00,LKR,300.00,Converted,tx-conv",
            lines[3]
        )
    }

    @Test
    fun `build on empty list outputs only header line and CRLF`() {
        val csv = MonthlyTransactionCsv.build(
            records = emptyList(),
            accountLabel = { "Main" },
            zone = colomboZone
        )

        assertEquals(MonthlyTransactionCsv.HEADER + "\r\n", csv)
    }
}

