package com.example.clarity.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.clarity.domain.export.ReportModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PdfReportWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun write(uri: Uri, report: ReportModel): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Unable to open output stream for $uri")
            outputStream.use { stream ->
                val document = PdfDocument()
                try {
                    renderReport(document, report)
                    document.writeTo(stream)
                    stream.flush()
                } finally {
                    document.close()
                }
            }
        }
    }

    private fun renderReport(document: PdfDocument, report: ReportModel) {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val monthPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val graySmallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val summaryTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val summaryBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val secondaryAmountPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_HEADER_BG
            style = Paint.Style.FILL
        }
        val zebraBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ZEBRA_BG
            style = Paint.Style.FILL
        }
        val summaryBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SUMMARY_BG
            style = Paint.Style.FILL
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_DIVIDER
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }

        // Measure all rows in pass 1
        val measuredRows = report.rows.mapIndexed { index, row ->
            val dateLayout = createCellLayout(
                text = row.date,
                paint = bodyTextPaint,
                width = COL_WIDTHS[0] - CELL_PADDING_H * 2
            )
            val typeLayout = createCellLayout(
                text = row.type,
                paint = bodyTextPaint,
                width = COL_WIDTHS[1] - CELL_PADDING_H * 2
            )
            val categoryLayout = createCellLayout(
                text = row.category,
                paint = bodyTextPaint,
                width = COL_WIDTHS[2] - CELL_PADDING_H * 2
            )
            val accountLayout = createCellLayout(
                text = row.account,
                paint = bodyTextPaint,
                width = COL_WIDTHS[3] - CELL_PADDING_H * 2
            )
            val descLayout = createCellLayout(
                text = row.description,
                paint = bodyTextPaint,
                width = COL_WIDTHS[4] - CELL_PADDING_H * 2
            )
            val amount1Layout = createCellLayout(
                text = row.amountLine1,
                paint = bodyTextPaint,
                width = COL_WIDTHS[5] - CELL_PADDING_H * 2,
                alignment = Layout.Alignment.ALIGN_OPPOSITE
            )
            val amount2Layout = row.amountLine2?.let { line2 ->
                createCellLayout(
                    text = line2,
                    paint = secondaryAmountPaint,
                    width = COL_WIDTHS[5] - CELL_PADDING_H * 2,
                    alignment = Layout.Alignment.ALIGN_OPPOSITE
                )
            }

            val amountHeight = amount1Layout.height +
                (if (amount2Layout != null) AMOUNT_LINE_GAP + amount2Layout.height else 0)

            val contentHeight = maxOf(
                dateLayout.height,
                typeLayout.height,
                categoryLayout.height,
                accountLayout.height,
                descLayout.height,
                amountHeight
            )
            val rowHeight = contentHeight + CELL_PADDING_V * 2

            MeasuredRow(
                index = index,
                height = rowHeight.toFloat(),
                dateLayout = dateLayout,
                typeLayout = typeLayout,
                categoryLayout = categoryLayout,
                accountLayout = accountLayout,
                descLayout = descLayout,
                amount1Layout = amount1Layout,
                amount2Layout = amount2Layout
            )
        }

        val firstPageTableTop = measureFirstPageTop(report)
        val regularPageTableTop = MARGIN.toFloat()
        val regularBottomLimit = (PAGE_HEIGHT - MARGIN - FOOTER_RESERVE).toFloat()
        val lastPageBottomLimit = (PAGE_HEIGHT - MARGIN - FOOTER_RESERVE_LAST_PAGE).toFloat()

        // Pass 1: Paginate rows
        val pages = mutableListOf<List<MeasuredRow>>()
        if (measuredRows.isEmpty()) {
            pages.add(emptyList())
        } else {
            var currentPageRows = mutableListOf<MeasuredRow>()
            var currentY = firstPageTableTop + TABLE_HEADER_HEIGHT
            for (row in measuredRows) {
                if (currentPageRows.isNotEmpty() && currentY + row.height > regularBottomLimit) {
                    pages.add(currentPageRows)
                    currentPageRows = mutableListOf()
                    currentY = regularPageTableTop + TABLE_HEADER_HEIGHT
                }
                currentPageRows.add(row)
                currentY += row.height
            }
            if (currentPageRows.isNotEmpty()) {
                // Ensure the footer note on the last page fits without colliding with the last row
                if (currentY > lastPageBottomLimit && currentPageRows.size > 1) {
                    val lastRow = currentPageRows.removeAt(currentPageRows.lastIndex)
                    pages.add(currentPageRows)
                    pages.add(listOf(lastRow))
                } else {
                    pages.add(currentPageRows)
                }
            }
        }

        val totalPages = pages.size

        // Pass 2: Draw pages
        pages.forEachIndexed { pageIndex, pageRows ->
            val pageNumber = pageIndex + 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var y = MARGIN.toFloat()
            if (pageIndex == 0) {
                y = drawFirstPageHeaderAndSummary(
                    canvas = canvas,
                    report = report,
                    startY = y,
                    titlePaint = titlePaint,
                    monthPaint = monthPaint,
                    graySmallPaint = graySmallPaint,
                    summaryTitlePaint = summaryTitlePaint,
                    summaryBodyPaint = summaryBodyPaint,
                    summaryBgPaint = summaryBgPaint,
                    dividerPaint = dividerPaint
                )
            }

            // Draw repeated table header
            drawTableHeader(
                canvas = canvas,
                headers = report.tableHeader,
                topY = y,
                headerBgPaint = headerBgPaint,
                headerTextPaint = headerTextPaint,
                dividerPaint = dividerPaint
            )
            y += TABLE_HEADER_HEIGHT

            // Draw table rows
            for (row in pageRows) {
                if (row.index % 2 == 1) {
                    canvas.drawRect(
                        MARGIN.toFloat(),
                        y,
                        (PAGE_WIDTH - MARGIN).toFloat(),
                        y + row.height,
                        zebraBgPaint
                    )
                }

                val cellTop = y + CELL_PADDING_V
                var x = MARGIN.toFloat()

                drawLayout(canvas, row.dateLayout, x + CELL_PADDING_H, cellTop)
                x += COL_WIDTHS[0]

                drawLayout(canvas, row.typeLayout, x + CELL_PADDING_H, cellTop)
                x += COL_WIDTHS[1]

                drawLayout(canvas, row.categoryLayout, x + CELL_PADDING_H, cellTop)
                x += COL_WIDTHS[2]

                drawLayout(canvas, row.accountLayout, x + CELL_PADDING_H, cellTop)
                x += COL_WIDTHS[3]

                drawLayout(canvas, row.descLayout, x + CELL_PADDING_H, cellTop)
                x += COL_WIDTHS[4]

                drawLayout(canvas, row.amount1Layout, x + CELL_PADDING_H, cellTop)
                if (row.amount2Layout != null) {
                    val line2Top = cellTop + row.amount1Layout.height + AMOUNT_LINE_GAP
                    drawLayout(canvas, row.amount2Layout, x + CELL_PADDING_H, line2Top)
                }

                y += row.height
                canvas.drawLine(
                    MARGIN.toFloat(),
                    y,
                    (PAGE_WIDTH - MARGIN).toFloat(),
                    y,
                    dividerPaint
                )
            }

            drawFooter(
                canvas = canvas,
                pageNumber = pageNumber,
                totalPages = totalPages,
                isLastPage = pageNumber == totalPages,
                footerNote = report.footerNote,
                graySmallPaint = graySmallPaint,
                dividerPaint = dividerPaint
            )

            document.finishPage(page)
        }
    }

    private fun measureFirstPageTop(report: ReportModel): Float {
        var y = MARGIN.toFloat()
        y += 22f // title
        y += 16f // month
        y += 16f // generated line + gap

        // Summary block height
        val summaryLineCount = report.summaries.size.coerceAtLeast(1)
        val hasPending = report.pendingNote != null
        val summaryBoxHeight = 20f + summaryLineCount * 14f + (if (hasPending) 14f else 0f) + 8f
        y += summaryBoxHeight + 12f
        return y
    }

    private fun drawFirstPageHeaderAndSummary(
        canvas: Canvas,
        report: ReportModel,
        startY: Float,
        titlePaint: TextPaint,
        monthPaint: TextPaint,
        graySmallPaint: TextPaint,
        summaryTitlePaint: TextPaint,
        summaryBodyPaint: TextPaint,
        summaryBgPaint: Paint,
        dividerPaint: Paint
    ): Float {
        var y = startY
        canvas.drawText(report.title, MARGIN.toFloat(), y + 16f, titlePaint)
        y += 22f

        canvas.drawText(report.monthLabel, MARGIN.toFloat(), y + 12f, monthPaint)
        y += 16f

        canvas.drawText(report.generatedLine, MARGIN.toFloat(), y + 9f, graySmallPaint)
        y += 16f

        val summaryLineCount = report.summaries.size.coerceAtLeast(1)
        val hasPending = report.pendingNote != null
        val summaryBoxHeight = 20f + summaryLineCount * 14f + (if (hasPending) 14f else 0f) + 8f

        val left = MARGIN.toFloat()
        val right = (PAGE_WIDTH - MARGIN).toFloat()
        canvas.drawRect(left, y, right, y + summaryBoxHeight, summaryBgPaint)
        canvas.drawRect(left, y, right, y + summaryBoxHeight, dividerPaint)

        var innerY = y + 14f
        canvas.drawText(
            "Monthly Summary (${report.recordCount} record${if (report.recordCount == 1) "" else "s"})",
            left + 10f,
            innerY,
            summaryTitlePaint
        )
        innerY += 15f

        if (report.summaries.isEmpty()) {
            canvas.drawText(
                "No converted totals available.",
                left + 10f,
                innerY,
                summaryBodyPaint
            )
            innerY += 14f
        } else {
            for (summary in report.summaries) {
                val line =
                    "${summary.currency}:   Income ${summary.currency} ${summary.income}   |   Expense ${summary.currency} ${summary.expense}   |   Net ${summary.currency} ${summary.net}"
                canvas.drawText(line, left + 10f, innerY, summaryBodyPaint)
                innerY += 14f
            }
        }

        if (report.pendingNote != null) {
            canvas.drawText(report.pendingNote, left + 10f, innerY, graySmallPaint)
        }

        return y + summaryBoxHeight + 12f
    }

    private fun drawTableHeader(
        canvas: Canvas,
        headers: List<String>,
        topY: Float,
        headerBgPaint: Paint,
        headerTextPaint: TextPaint,
        dividerPaint: Paint
    ) {
        val left = MARGIN.toFloat()
        val right = (PAGE_WIDTH - MARGIN).toFloat()
        val bottom = topY + TABLE_HEADER_HEIGHT
        canvas.drawRect(left, topY, right, bottom, headerBgPaint)
        canvas.drawLine(left, topY, right, topY, dividerPaint)
        canvas.drawLine(left, bottom, right, bottom, dividerPaint)

        var x = left
        val baseline = topY + 15f
        headers.forEachIndexed { index, title ->
            val colWidth = COL_WIDTHS.getOrElse(index) { 60 }
            if (index == COL_WIDTHS.lastIndex) {
                val textWidth = headerTextPaint.measureText(title)
                canvas.drawText(
                    title,
                    x + colWidth - CELL_PADDING_H - textWidth,
                    baseline,
                    headerTextPaint
                )
            } else {
                canvas.drawText(title, x + CELL_PADDING_H, baseline, headerTextPaint)
            }
            x += colWidth
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        pageNumber: Int,
        totalPages: Int,
        isLastPage: Boolean,
        footerNote: String,
        graySmallPaint: TextPaint,
        dividerPaint: Paint
    ) {
        val left = MARGIN.toFloat()
        val right = (PAGE_WIDTH - MARGIN).toFloat()
        val footerBaseline = (PAGE_HEIGHT - MARGIN).toFloat()

        if (isLastPage && footerNote.isNotEmpty()) {
            val noteBaseline = footerBaseline - 14f
            canvas.drawText(footerNote, left, noteBaseline, graySmallPaint)
        }

        val dividerY = footerBaseline - (if (isLastPage) 24f else 12f)
        canvas.drawLine(left, dividerY, right, dividerY, dividerPaint)

        canvas.drawText("Clarity", left, footerBaseline, graySmallPaint)

        val pageLabel = "Page $pageNumber of $totalPages"
        val pageLabelWidth = graySmallPaint.measureText(pageLabel)
        val centerX = (PAGE_WIDTH - pageLabelWidth) / 2f
        canvas.drawText(pageLabel, centerX, footerBaseline, graySmallPaint)
    }

    private fun createCellLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        val safeWidth = width.coerceAtLeast(1)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(alignment)
            .setIncludePad(false)
            .setMaxLines(MAX_CELL_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(safeWidth)
            .build()
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private data class MeasuredRow(
        val index: Int,
        val height: Float,
        val dateLayout: StaticLayout,
        val typeLayout: StaticLayout,
        val categoryLayout: StaticLayout,
        val accountLayout: StaticLayout,
        val descLayout: StaticLayout,
        val amount1Layout: StaticLayout,
        val amount2Layout: StaticLayout?
    )

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 36
        const val TABLE_HEADER_HEIGHT = 22f
        const val CELL_PADDING_H = 4
        const val CELL_PADDING_V = 5
        const val AMOUNT_LINE_GAP = 2
        const val MAX_CELL_LINES = 12
        const val FOOTER_RESERVE = 24
        const val FOOTER_RESERVE_LAST_PAGE = 36

        // Sum = 62 + 44 + 88 + 96 + 140 + 93 = 523 (= 595 - 36 * 2)
        val COL_WIDTHS = intArrayOf(62, 44, 88, 96, 140, 93)

        const val COLOR_TEXT_PRIMARY = 0xFF212121.toInt()
        const val COLOR_TEXT_SECONDARY = 0xFF616161.toInt()
        const val COLOR_HEADER_BG = 0xFFEEEEEE.toInt()
        const val COLOR_ZEBRA_BG = 0xFFFAFAFA.toInt()
        const val COLOR_SUMMARY_BG = 0xFFF5F5F5.toInt()
        const val COLOR_DIVIDER = 0xFFBDBDBD.toInt()
    }
}

