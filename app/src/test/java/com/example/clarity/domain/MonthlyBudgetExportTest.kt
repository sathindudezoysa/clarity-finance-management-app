package com.example.clarity.domain

import com.example.clarity.domain.export.BudgetExportFormat
import com.example.clarity.domain.export.MonthlyBudgetCsv
import com.example.clarity.domain.export.MonthlyBudgetReport
import com.example.clarity.domain.export.MonthlyBudgetXlsx
import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.model.BudgetPolicy
import com.example.clarity.domain.model.BudgetResolution
import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.model.BudgetThreshold
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class MonthlyBudgetExportTest {

    private val colomboZone: ZoneId = ZoneId.of("Asia/Colombo")

    private fun statusLabel(threshold: BudgetThreshold): String = when (threshold) {
        BudgetThreshold.NORMAL -> "Below warning threshold"
        BudgetThreshold.NEARING -> "Nearing budget limit"
        BudgetThreshold.REACHED -> "Budget reached"
        BudgetThreshold.EXCEEDED -> "Budget exceeded"
    }

    @Test
    fun `statusesForMonth filters active monthly budgets with start up to month and sorts by category then currency`() {
        val targetMonth = YearMonth.of(2026, 10)
        val foodStartedEarlier = Budget(
            id = BudgetPolicy.id("2026-08", "Food"),
            userId = "u1",
            month = "2026-08",
            category = "Food",
            limitMinor = 100_000,
            currency = "USD",
            active = true
        )
        val foodLkr = Budget(
            id = BudgetPolicy.id("2026-10", "food"),
            userId = "u1",
            month = "2026-10",
            category = "food",
            limitMinor = 200_000,
            currency = "LKR",
            active = true
        )
        val billsBudget = Budget(
            id = BudgetPolicy.id("2026-10", "Bills"),
            userId = "u1",
            month = "2026-10",
            category = "Bills",
            limitMinor = 50_000,
            currency = "LKR",
            active = true
        )
        val yearlyBudget = Budget(
            id = BudgetPolicy.id("2026", "Travel"),
            userId = "u1",
            month = "2026",
            category = "Travel",
            limitMinor = 500_000,
            currency = "LKR",
            active = true
        )
        val disabledBudget = Budget(
            id = BudgetPolicy.id("2026-09", "Shopping"),
            userId = "u1",
            month = "2026-09",
            category = "Shopping",
            limitMinor = 100_000,
            currency = "LKR",
            active = false
        )
        val futureStartBudget = Budget(
            id = BudgetPolicy.id("2026-11", "Rent"),
            userId = "u1",
            month = "2026-11",
            category = "Rent",
            limitMinor = 300_000,
            currency = "LKR",
            active = true
        )

        val statuses = BudgetPolicy.statusesForMonth(
            budgets = listOf(
                foodStartedEarlier,
                yearlyBudget,
                disabledBudget,
                futureStartBudget,
                foodLkr,
                billsBudget
            ),
            transactions = emptyList(),
            resolutions = emptyList(),
            month = targetMonth,
            zone = colomboZone
        )

        assertEquals(3, statuses.size)
        assertEquals(listOf("Bills", "food", "Food"), statuses.map { it.budget.category })
        assertEquals(listOf("LKR", "LKR", "USD"), statuses.map { it.budget.currency })
        // Keeps original budget id and start month while setting activePeriod to targetMonth
        val foodUsdStatus = statuses[2]
        assertEquals(foodStartedEarlier.id, foodUsdStatus.budget.id)
        assertEquals("2026-08", foodUsdStatus.budget.month)
        assertEquals("2026-10", foodUsdStatus.activePeriod)
    }

    @Test
    fun `statusesForMonth counts only target month expenses in non-UTC zone and handles foreign currency resolutions`() {
        val targetMonth = YearMonth.of(2026, 10)
        val budget = Budget(
            id = BudgetPolicy.id("2026-06", "Food"),
            userId = "u1",
            month = "2026-06",
            category = "Food",
            limitMinor = 100_000,
            currency = "LKR",
            active = true
        )

        val startOfOctColombo = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, colomboZone)
            .toInstant().toEpochMilli()
        val endOfOctColombo = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, colomboZone)
            .toInstant().toEpochMilli() - 1

        val txBeforeOct = Transaction(
            id = "tx-prev",
            userId = "u1",
            type = TransactionType.EXPENSE,
            amountMinor = 10_000,
            currency = "LKR",
            category = "Food",
            dateEpochMillis = startOfOctColombo - 1
        )
        val txStartOct = Transaction(
            id = "tx-start",
            userId = "u1",
            type = TransactionType.EXPENSE,
            amountMinor = 15_000,
            currency = "LKR",
            category = "  food ",
            dateEpochMillis = startOfOctColombo
        )
        val txEndOctConverted = Transaction(
            id = "tx-converted",
            userId = "u1",
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            currency = "USD",
            targetCurrency = "LKR",
            conversion = ConversionSnapshot(
                rate = "300",
                provider = "ExchangeRate-API", rateAt = startOfOctColombo, fetchedAt = startOfOctColombo, cached = false,
                amountMinor = 25_000
            ),
            pendingConversion = false,
            category = "FOOD",
            dateEpochMillis = endOfOctColombo
        )
        val txIncomeIgnored = Transaction(
            id = "tx-income",
            userId = "u1",
            type = TransactionType.INCOME,
            amountMinor = 99_000,
            currency = "LKR",
            category = "Food",
            dateEpochMillis = startOfOctColombo + 1_000
        )
        val txOtherCategoryIgnored = Transaction(
            id = "tx-other-cat",
            userId = "u1",
            type = TransactionType.EXPENSE,
            amountMinor = 99_000,
            currency = "LKR",
            category = "Transport",
            dateEpochMillis = startOfOctColombo + 1_000
        )
        val txForeignUncounted = Transaction(
            id = "tx-uncounted",
            userId = "u1",
            type = TransactionType.EXPENSE,
            amountMinor = 5_000,
            currency = "EUR",
            pendingConversion = true,
            category = "Food",
            dateEpochMillis = startOfOctColombo + 5_000,
            updatedAt = 42L
        )
        val txNovIgnored = Transaction(
            id = "tx-nov",
            userId = "u1",
            type = TransactionType.EXPENSE,
            amountMinor = 20_000,
            currency = "LKR",
            category = "Food",
            dateEpochMillis = endOfOctColombo + 1
        )

        val transactions = listOf(
            txBeforeOct,
            txStartOct,
            txEndOctConverted,
            txIncomeIgnored,
            txOtherCategoryIgnored,
            txForeignUncounted,
            txNovIgnored
        )

        val withoutResolution = BudgetPolicy.statusesForMonth(
            budgets = listOf(budget),
            transactions = transactions,
            resolutions = emptyList(),
            month = targetMonth,
            zone = colomboZone
        ).single()

        assertEquals(BigInteger.valueOf(40_000), withoutResolution.spent)
        assertEquals(1, withoutResolution.uncounted)
        assertEquals(budget.id, withoutResolution.budget.id)
        assertEquals("2026-06", withoutResolution.budget.month)
        assertEquals("2026-10", withoutResolution.activePeriod)

        val withResolution = BudgetPolicy.statusesForMonth(
            budgets = listOf(budget),
            transactions = transactions,
            resolutions = listOf(
                BudgetResolution(
                    transactionId = "tx-uncounted",
                    transactionVersion = 42L,
                    currency = "LKR",
                    amountMinor = 12_500
                )
            ),
            month = targetMonth,
            zone = colomboZone
        ).single()

        assertEquals(BigInteger.valueOf(52_500), withResolution.spent)
        assertEquals(0, withResolution.uncounted)
    }

    @Test
    fun `MonthlyBudgetReport rounds usedPercent DOWN formats JPY and KWD negative remaining and builds currency summary`() {
        val month = YearMonth.of(2026, 10)
        // 9999 / 10000 = 99.99% -> must round DOWN to 99.9
        val lkrNear = BudgetStatus(
            budget = Budget(BudgetPolicy.id("2026-09", "Food"), "u1", "2026-09", "Food", 10_000, "LKR"),
            spent = BigInteger.valueOf(9_999),
            uncounted = 1,
            activePeriod = "2026-10"
        )
        // Over limit -> negative remaining
        val lkrExceeded = BudgetStatus(
            budget = Budget(BudgetPolicy.id("2026-10", "Rent"), "u1", "2026-10", "Rent", 10_000, "LKR"),
            spent = BigInteger.valueOf(12_550),
            uncounted = 2,
            activePeriod = "2026-10"
        )
        // JPY (0 decimal digits) at exact limit (REACHED)
        val jpyReached = BudgetStatus(
            budget = Budget(BudgetPolicy.id("2026-10", "Transit"), "u1", "2026-10", "Transit", 5_000, "JPY"),
            spent = BigInteger.valueOf(5_000),
            uncounted = 0,
            activePeriod = "2026-10"
        )
        // KWD (3 decimal digits)
        val kwdNormal = BudgetStatus(
            budget = Budget(BudgetPolicy.id("2026-10", "Utilities"), "u1", "2026-10", "Utilities", 10_000, "KWD"),
            spent = BigInteger.valueOf(2_500),
            uncounted = 0,
            activePeriod = "2026-10"
        )

        val report = MonthlyBudgetReport.build(
            statuses = listOf(lkrNear, lkrExceeded, jpyReached, kwdNormal),
            month = month,
            generatedAtMillis = 1760958000000L,
            statusLabel = ::statusLabel,
            zone = colomboZone
        )

        assertEquals("Clarity - Monthly Budget Report", report.title)
        assertTrue(report.generatedLine.startsWith("Generated on "))
        assertEquals(4, report.rows.size)

        val row0 = report.rows[0]
        assertEquals("100.00", row0.limit)
        assertEquals("99.99", row0.spent)
        assertEquals("0.01", row0.remaining)
        assertEquals("99.9", row0.usedPercent)
        assertEquals("Nearing budget limit", row0.status)
        assertEquals("2026-09", row0.budgetStartMonth)

        val row1 = report.rows[1]
        assertEquals("100.00", row1.limit)
        assertEquals("125.50", row1.spent)
        assertEquals("-25.50", row1.remaining)
        assertEquals("125.5", row1.usedPercent)
        assertEquals("Budget exceeded", row1.status)

        val rowJpy = report.rows[2]
        assertEquals("5000", rowJpy.limit)
        assertEquals("5000", rowJpy.spent)
        assertEquals("0", rowJpy.remaining)
        assertEquals("100.0", rowJpy.usedPercent)
        assertEquals("Budget reached", rowJpy.status)

        val rowKwd = report.rows[3]
        assertEquals("10.000", rowKwd.limit)
        assertEquals("2.500", rowKwd.spent)
        assertEquals("7.500", rowKwd.remaining)
        assertEquals("25.0", rowKwd.usedPercent)
        assertEquals("Below warning threshold", rowKwd.status)

        assertEquals(3, report.summaries.size)
        val lkrSummary = report.summaries.first { it.currency == "LKR" }
        assertEquals(2, lkrSummary.budgetCount)
        assertEquals("200.00", lkrSummary.totalLimit)
        assertEquals("225.49", lkrSummary.totalSpent)
        assertEquals("-25.49", lkrSummary.totalRemaining)
        assertEquals(1, lkrSummary.atOrOverLimitCount)
        assertEquals(3, lkrSummary.totalUncounted)

        val jpySummary = report.summaries.first { it.currency == "JPY" }
        assertEquals(1, jpySummary.atOrOverLimitCount)
    }

    @Test
    fun `MonthlyBudgetCsv outputs exact header CRLF quoting formula injection on Category and leaves numbers untouched`() {
        val month = YearMonth.of(2026, 10)
        val emptyReport = MonthlyBudgetReport.build(
            statuses = emptyList(),
            month = month,
            generatedAtMillis = 0L,
            statusLabel = ::statusLabel,
            zone = colomboZone
        )
        assertEquals(MonthlyBudgetCsv.HEADER + "\r\n", MonthlyBudgetCsv.build(emptyReport))

        val formulaBudget = BudgetStatus(
            budget = Budget("2026-10_id1", "u1", "2026-10", "=1+1, \"quoted\"", 10_000, "LKR"),
            spent = BigInteger.valueOf(15_000),
            uncounted = 2,
            activePeriod = "2026-10"
        )
        val report = MonthlyBudgetReport.build(
            statuses = listOf(formulaBudget),
            month = month,
            generatedAtMillis = 0L,
            statusLabel = ::statusLabel,
            zone = colomboZone
        )

        val csv = MonthlyBudgetCsv.build(report)
        val lines = csv.split("\r\n")
        assertEquals(3, lines.size)
        assertEquals(
            "Month,Category,Limit,Spent,Remaining,Used (%),Status,Currency,Uncounted Expenses,Budget Start Month,Budget ID",
            lines[0]
        )
        assertEquals(
            "2026-10,\"'=1+1, \"\"quoted\"\"\",100.00,150.00,-50.00,150.0,Budget exceeded,LKR,2,2026-10,2026-10_id1",
            lines[1]
        )
        assertEquals("", lines[2])
        assertEquals("clarity-budgets-2026-10.csv", BudgetExportFormat.CSV.fileName(month))
        assertEquals("clarity-budgets-2026-10.xlsx", BudgetExportFormat.XLSX.fileName(month))
    }

    @Test
    fun `MonthlyBudgetXlsx writes valid ZIP with Content_Types first well-formed XML numeric cells escaped chars and frozen pane`() {
        val month = YearMonth.of(2026, 10)
        // Category has XML special chars & < > " ', a formula char '=', and an invalid XML 1.0 control char \u0007
        val trickyCategory = "=Food & <Dining> \"Special\" 'Cafe'\u0007"
        val status = BudgetStatus(
            budget = Budget("2026-09_id1", "u1", "2026-09", trickyCategory, 150_000, "LKR"),
            spent = BigInteger.valueOf(175_050),
            uncounted = 3,
            activePeriod = "2026-10"
        )
        val report = MonthlyBudgetReport.build(
            statuses = listOf(status),
            month = month,
            generatedAtMillis = 1760958000000L,
            statusLabel = ::statusLabel,
            zone = colomboZone
        )

        val xlsxBytes = MonthlyBudgetXlsx.build(report)
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
            }
        }

        val entryNames = entries.keys.toList()
        assertEquals("[Content_Types].xml", entryNames.first())
        assertEquals(
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
                "xl/worksheets/sheet2.xml"
            ),
            entryNames
        )

        val dbFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val parsedDocs = entries.mapValues { (_, xml) ->
            dbFactory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }

        // Check workbook sheet names
        val workbookDoc = parsedDocs.getValue("xl/workbook.xml")
        val sheets = workbookDoc.getElementsByTagName("sheet")
        assertEquals(2, sheets.length)
        assertEquals("Budgets", (sheets.item(0) as Element).getAttribute("name"))
        assertEquals("Summary", (sheets.item(1) as Element).getAttribute("name"))

        // Check sheet1 (Budgets): frozen pane, numeric cells without t attribute, no formula prefix on category
        val sheet1Doc = parsedDocs.getValue("xl/worksheets/sheet1.xml")
        val panes = sheet1Doc.getElementsByTagName("pane")
        assertEquals(1, panes.length)
        val pane = panes.item(0) as Element
        assertEquals("frozen", pane.getAttribute("state"))
        assertEquals("1", pane.getAttribute("ySplit"))
        assertEquals("A2", pane.getAttribute("topLeftCell"))

        val cells = sheet1Doc.getElementsByTagName("c")
        val cellByRef = (0 until cells.length)
            .map { cells.item(it) as Element }
            .associateBy { it.getAttribute("r") }

        // B2 is Category: inlineStr, no leading apostrophe, control char stripped
        val b2 = cellByRef.getValue("B2")
        assertEquals("inlineStr", b2.getAttribute("t"))
        assertEquals("=Food & <Dining> \"Special\" 'Cafe'", b2.textContent)

        // Numeric cells C2 (Limit), D2 (Spent), E2 (Remaining), F2 (Used %), I2 (Uncounted) have no "t" attribute
        for ((ref, expectedValue) in listOf(
            "C2" to "1500.00",
            "D2" to "1750.50",
            "E2" to "-250.50",
            "F2" to "116.7",
            "I2" to "3"
        )) {
            val cell = cellByRef.getValue(ref)
            assertFalse("Expected numeric cell $ref to have no 't' attribute", cell.hasAttribute("t"))
            assertEquals(expectedValue, cell.getElementsByTagName("v").item(0).textContent)
        }

        // Also verify empty report produces valid XLSX with both sheets
        val emptyReport = MonthlyBudgetReport.build(
            statuses = emptyList(),
            month = month,
            generatedAtMillis = 0L,
            statusLabel = ::statusLabel,
            zone = colomboZone
        )
        val emptyXlsx = MonthlyBudgetXlsx.build(emptyReport)
        ZipInputStream(ByteArrayInputStream(emptyXlsx)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                dbFactory.newDocumentBuilder().parse(ByteArrayInputStream(zis.readBytes()))
                zis.closeEntry()
            }
        }
    }
}

