package com.example.clarity.domain.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MonthlyBudgetXlsx {
    private val BUDGETS_HEADERS = listOf(
        "Month",
        "Category",
        "Limit",
        "Spent",
        "Remaining",
        "Used (%)",
        "Status",
        "Currency",
        "Uncounted Expenses",
        "Budget Start Month",
        "Budget ID"
    )

    private val BUDGETS_COL_WIDTHS = intArrayOf(12, 24, 14, 14, 14, 12, 26, 10, 20, 20, 36)

    private val SUMMARY_HEADERS = listOf(
        "Currency",
        "Budgets",
        "Total Limit",
        "Total Spent",
        "Total Remaining",
        "At or Over Limit",
        "Uncounted Expenses"
    )

    private val SUMMARY_COL_WIDTHS = intArrayOf(14, 12, 16, 16, 16, 18, 20)

    fun build(report: MonthlyBudgetReport.ReportModel): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.setMethod(ZipOutputStream.DEFLATED)
            writeEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeEntry(zip, "_rels/.rels", rootRelsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml())
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            writeEntry(zip, "xl/styles.xml", stylesXml())
            writeEntry(zip, "xl/worksheets/sheet1.xml", budgetsSheetXml(report))
            writeEntry(zip, "xl/worksheets/sheet2.xml", summarySheetXml(report))
        }
        return output.toByteArray()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, xml: String) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        zip.write(xml.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            """</Types>"""

    private fun rootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun workbookXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets>""" +
            """<sheet name="Budgets" sheetId="1" r:id="rId1"/>""" +
            """<sheet name="Summary" sheetId="2" r:id="rId2"/>""" +
            """</sheets>""" +
            """</workbook>"""

    private fun workbookRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>""" +
            """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
            """</Relationships>"""

    private fun stylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2">""" +
            """<font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font>""" +
            """</fonts>""" +
            """<fills count="2">""" +
            """<fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """</fills>""" +
            """<borders count="1">""" +
            """<border><left/><right/><top/><bottom/><diagonal/></border>""" +
            """</borders>""" +
            """<cellStyleXfs count="1">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>""" +
            """</cellStyleXfs>""" +
            """<cellXfs count="2">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            """</cellXfs>""" +
            """</styleSheet>"""

    private fun budgetsSheetXml(report: MonthlyBudgetReport.ReportModel): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("<cols>")
        for (i in BUDGETS_COL_WIDTHS.indices) {
            val colIndex = i + 1
            sb.append("""<col min="$colIndex" max="$colIndex" width="${BUDGETS_COL_WIDTHS[i]}" customWidth="1"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        sb.append("""<row r="1">""")
        for (i in BUDGETS_HEADERS.indices) {
            sb.append(textCell(cellRef(i, 1), BUDGETS_HEADERS[i], style = 1))
        }
        sb.append("</row>")

        report.rows.forEachIndexed { idx, row ->
            val r = idx + 2
            sb.append("""<row r="$r">""")
            sb.append(textCell(cellRef(0, r), row.month))
            sb.append(textCell(cellRef(1, r), row.category))
            sb.append(numberCell(cellRef(2, r), row.limit))
            sb.append(numberCell(cellRef(3, r), row.spent))
            sb.append(numberCell(cellRef(4, r), row.remaining))
            sb.append(numberCell(cellRef(5, r), row.usedPercent))
            sb.append(textCell(cellRef(6, r), row.status))
            sb.append(textCell(cellRef(7, r), row.currency))
            sb.append(numberCell(cellRef(8, r), row.uncounted.toString()))
            sb.append(textCell(cellRef(9, r), row.budgetStartMonth))
            sb.append(textCell(cellRef(10, r), row.budgetId))
            sb.append("</row>")
        }

        sb.append("</sheetData>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun summarySheetXml(report: MonthlyBudgetReport.ReportModel): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<cols>")
        for (i in SUMMARY_COL_WIDTHS.indices) {
            val colIndex = i + 1
            sb.append("""<col min="$colIndex" max="$colIndex" width="${SUMMARY_COL_WIDTHS[i]}" customWidth="1"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        sb.append("""<row r="1">""")
        sb.append(textCell("A1", report.title, style = 1))
        sb.append("</row>")

        sb.append("""<row r="2">""")
        sb.append(textCell("A2", "Month", style = 1))
        sb.append(textCell("B2", report.monthLabel))
        sb.append("</row>")

        sb.append("""<row r="3">""")
        sb.append(textCell("A3", report.generatedLine))
        sb.append("</row>")

        sb.append("""<row r="4"/>""")

        sb.append("""<row r="5">""")
        for (i in SUMMARY_HEADERS.indices) {
            sb.append(textCell(cellRef(i, 5), SUMMARY_HEADERS[i], style = 1))
        }
        sb.append("</row>")

        var currentRow = 6
        for (summary in report.summaries) {
            sb.append("""<row r="$currentRow">""")
            sb.append(textCell(cellRef(0, currentRow), summary.currency))
            sb.append(numberCell(cellRef(1, currentRow), summary.budgetCount.toString()))
            sb.append(numberCell(cellRef(2, currentRow), summary.totalLimit))
            sb.append(numberCell(cellRef(3, currentRow), summary.totalSpent))
            sb.append(numberCell(cellRef(4, currentRow), summary.totalRemaining))
            sb.append(numberCell(cellRef(5, currentRow), summary.atOrOverLimitCount.toString()))
            sb.append(numberCell(cellRef(6, currentRow), summary.totalUncounted.toString()))
            sb.append("</row>")
            currentRow++
        }

        val noteRow = currentRow + 1
        sb.append("""<row r="$noteRow">""")
        sb.append(textCell("A$noteRow", report.footerNote))
        sb.append("</row>")

        sb.append("</sheetData>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun cellRef(colZeroBased: Int, rowOneBased: Int): String {
        val colLetter = ('A'.code + colZeroBased).toChar()
        return "$colLetter$rowOneBased"
    }

    private fun textCell(ref: String, value: String, style: Int = 0): String {
        val styleAttr = if (style > 0) """ s="$style"""" else ""
        val escaped = escapeXml(value)
        return """<c r="$ref" t="inlineStr"$styleAttr><is><t xml:space="preserve">$escaped</t></is></c>"""
    }

    private fun numberCell(ref: String, plainNumber: String): String =
        """<c r="$ref"><v>${escapeXml(plainNumber)}</v></c>"""

    internal fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val code = ch.code
            val validXml10 = code == 0x09 || code == 0x0A || code == 0x0D ||
                (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
            if (!validXml10) continue
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

