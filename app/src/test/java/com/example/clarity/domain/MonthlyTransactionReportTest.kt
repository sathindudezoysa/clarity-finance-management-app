package com.example.clarity.domain

import com.example.clarity.domain.export.CurrencySummary
import com.example.clarity.domain.export.ExportFormat
import com.example.clarity.domain.export.MonthlyTransactionCsv
import com.example.clarity.domain.export.MonthlyTransactionReport
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyTransactionReportTest {

    private val colomboZone: ZoneId = ZoneId.of("Asia/Colombo")
    private val targetMonth: YearMonth = YearMonth.of(2026, 10)

    private fun tx(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        amountMinor: Long = 1000L,
        category: String = "Food",
        note: String = "Lunch",
        dateEpochMillis: Long = 1_790_812_800_000L,
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
    fun `summary totals per target currency match BigInteger rule and pending rows trigger pending note`() {
        val baseMillis = LocalDate.of(2026, 10, 5)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()

        val lkrIncome = tx(
            id = "1",
            type = TransactionType.INCOME,
            amountMinor = 500_000L, // 5000.00 LKR
            currency = "LKR",
            targetCurrency = "LKR",
            dateEpochMillis = baseMillis
        )
        val usdToLkrExpense = tx(
            id = "2",
            type = TransactionType.EXPENSE,
            amountMinor = 1000L, // 10.00 USD
            currency = "USD",
            targetCurrency = "LKR",
            dateEpochMillis = baseMillis,
            conversion = ConversionSnapshot(
                amountMinor = 300_000L, // 3000.00 LKR
                rate = "300.00",
                provider = "fx",
                rateAt = baseMillis,
                fetchedAt = baseMillis,
                cached = false
            )
        )
        val usdExpense = tx(
            id = "3",
            type = TransactionType.EXPENSE,
            amountMinor = 2550L, // 25.50 USD
            currency = "USD",
            targetCurrency = "USD",
            dateEpochMillis = baseMillis
        )
        val pending1 = tx(
            id = "4",
            type = TransactionType.EXPENSE,
            amountMinor = 9999L,
            currency = "EUR",
            targetCurrency = "LKR",
            dateEpochMillis = baseMillis,
            conversion = null
        )
        val pending2 = tx(
            id = "5",
            type = TransactionType.INCOME,
            amountMinor = 8888L,
            currency = "GBP",
            targetCurrency = "LKR",
            dateEpochMillis = baseMillis,
            conversion = null
        )

        val report = MonthlyTransactionReport.build(
            records = listOf(lkrIncome, usdToLkrExpense, usdExpense, pending1, pending2),
            accountLabel = { "Main Account" },
            month = targetMonth,
            generatedAtMillis = baseMillis,
            zone = colomboZone
        )

        assertEquals(5, report.recordCount)
        assertEquals(
            listOf(
                CurrencySummary(
                    currency = "LKR",
                    income = "5000.00",
                    expense = "3000.00",
                    net = "2000.00"
                ),
                CurrencySummary(
                    currency = "USD",
                    income = "0.00",
                    expense = "25.50",
                    net = "-25.50"
                )
            ),
            report.summaries
        )
        assertEquals(
            "2 record(s) with pending conversion are not included in the totals.",
            report.pendingNote
        )
    }

    @Test
    fun `row amount lines format same currency converted pending JPY and KWD with expense sign`() {
        val baseMillis = LocalDate.of(2026, 10, 12)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()

        val sameCurrencyIncome = tx(
            id = "1",
            type = TransactionType.INCOME,
            amountMinor = 125_000L,
            currency = "LKR",
            targetCurrency = "LKR",
            dateEpochMillis = baseMillis,
            createdAt = 1L
        )
        val convertedExpense = tx(
            id = "2",
            type = TransactionType.EXPENSE,
            amountMinor = 1500L,
            currency = "USD",
            targetCurrency = "LKR",
            dateEpochMillis = baseMillis,
            createdAt = 2L,
            conversion = ConversionSnapshot(
                amountMinor = 450_000L,
                rate = "300.00",
                provider = "fx",
                rateAt = baseMillis,
                fetchedAt = baseMillis,
                cached = false
            )
        )
        val pendingExpense = tx(
            id = "3",
            type = TransactionType.EXPENSE,
            amountMinor = 2000L,
            currency = "USD",
            targetCurrency = "LKR",
            dateEpochMillis = baseMillis,
            createdAt = 3L,
            conversion = null
        )
        val jpyExpense = tx(
            id = "4",
            type = TransactionType.EXPENSE,
            amountMinor = 1500L,
            currency = "JPY",
            targetCurrency = "JPY",
            dateEpochMillis = baseMillis,
            createdAt = 4L
        )
        val kwdIncome = tx(
            id = "5",
            type = TransactionType.INCOME,
            amountMinor = 12345L,
            currency = "KWD",
            targetCurrency = "KWD",
            dateEpochMillis = baseMillis,
            createdAt = 5L
        )

        val report = MonthlyTransactionReport.build(
            records = listOf(sameCurrencyIncome, convertedExpense, pendingExpense, jpyExpense, kwdIncome),
            accountLabel = { "Main" },
            month = targetMonth,
            generatedAtMillis = baseMillis,
            zone = colomboZone
        )

        assertNull(report.summaries.firstOrNull { it.currency == "EUR" })
        assertEquals("1 record(s) with pending conversion are not included in the totals.", report.pendingNote)

        // 1: Same currency income -> 1 line, no second line
        assertEquals("LKR 1250.00", report.rows[0].amountLine1)
        assertNull(report.rows[0].amountLine2)

        // 2: Converted USD -> LKR expense -> 2 lines with negative sign on both
        assertEquals("USD -15.00", report.rows[1].amountLine1)
        assertEquals("= LKR -4500.00", report.rows[1].amountLine2)

        // 3: Pending conversion -> "Conversion pending"
        assertEquals("USD -20.00", report.rows[2].amountLine1)
        assertEquals("Conversion pending", report.rows[2].amountLine2)

        // 4: JPY (0 decimals) expense
        assertEquals("JPY -1500", report.rows[3].amountLine1)
        assertNull(report.rows[3].amountLine2)

        // 5: KWD (3 decimals) income
        assertEquals("KWD 12.345", report.rows[4].amountLine1)
        assertNull(report.rows[4].amountLine2)
    }

    @Test
    fun `row order is oldest first like CSV and metadata formats with fixed zone`() {
        val oct10Millis = LocalDate.of(2026, 10, 10)
            .atStartOfDay(colomboZone)
            .toInstant()
            .toEpochMilli()
        // 2026-10-31 14:45 in Asia/Colombo (+05:30) = 2026-10-31T09:15:00Z
        val generatedMillis = Instant.parse("2026-10-31T09:15:00Z").toEpochMilli()

        val r1 = tx(id = "b", dateEpochMillis = oct10Millis, createdAt = 10L, note = "=NoteB")
        val r2 = tx(id = "a", dateEpochMillis = oct10Millis, createdAt = 10L, note = "NoteA")
        val r3 = tx(id = "c", dateEpochMillis = oct10Millis, createdAt = 5L, note = "NoteC")
        val r4 = tx(id = "d", dateEpochMillis = oct10Millis + 86_400_000L, createdAt = 1L, note = "NoteD")

        val report = MonthlyTransactionReport.build(
            records = listOf(r4, r1, r2, r3),
            accountLabel = { "Cash Wallet" },
            month = targetMonth,
            generatedAtMillis = generatedMillis,
            zone = colomboZone
        )

        assertEquals("Clarity - Monthly Transaction Records", report.title)
        val expectedMonthLabel = targetMonth.format(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        )
        assertEquals(expectedMonthLabel, report.monthLabel)
        assertEquals("Generated on 2026-10-31 14:45", report.generatedLine)
        assertNull(report.pendingNote)
        assertEquals(
            listOf("Date", "Type", "Category", "Account", "Description", "Amount"),
            report.tableHeader
        )
        assertEquals(
            "Long descriptions may be shortened in this PDF. The CSV export contains the full text.",
            report.footerNote
        )

        // Ordering matches CSV: c, a, b, d; and no formula-injection prefixing on "=NoteB"
        assertEquals(listOf("NoteC", "NoteA", "=NoteB", "NoteD"), report.rows.map { it.description })
        assertEquals(listOf("2026-10-10", "2026-10-10", "2026-10-10", "2026-10-11"), report.rows.map { it.date })

        // Verify ExportFormat fileName matches MonthlyTransactionCsv.fileName for CSV and uses .pdf for PDF
        assertEquals(
            MonthlyTransactionCsv.fileName(targetMonth),
            ExportFormat.CSV.fileName(targetMonth)
        )
        assertEquals("clarity-transactions-2026-10.pdf", ExportFormat.PDF.fileName(targetMonth))
    }
}

