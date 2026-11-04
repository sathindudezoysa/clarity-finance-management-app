package com.example.clarity.presentation.budgets.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.data.export.ByteFileWriter
import com.example.clarity.domain.export.BudgetExportFormat
import com.example.clarity.domain.export.MonthlyBudgetCsv
import com.example.clarity.domain.export.MonthlyBudgetReport
import com.example.clarity.domain.export.MonthlyBudgetXlsx
import com.example.clarity.domain.model.BudgetPolicy
import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.usecase.budget.GetMonthlyBudgetStatusesUseCase
import com.example.clarity.presentation.budgets.budgetStatusText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetExportState(
    val month: YearMonth = YearMonth.now(),
    val currentMonth: YearMonth = YearMonth.now(),
    val format: BudgetExportFormat = BudgetExportFormat.CSV,
    val loading: Boolean = true,
    val rowCount: Int = 0,
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class BudgetExportViewModel @Inject constructor(
    private val getMonthlyBudgetStatuses: GetMonthlyBudgetStatusesUseCase,
    private val byteFileWriter: ByteFileWriter,
    private val clock: Clock
) : ViewModel() {

    private data class PreparedFile(
        val fileName: String,
        val bytes: ByteArray,
        val rowCount: Int,
        val format: BudgetExportFormat
    )

    private val initialMonth: YearMonth = currentYearMonth()
    private val _uiState = MutableStateFlow(
        BudgetExportState(
            month = initialMonth,
            currentMonth = initialMonth
        )
    )
    val uiState: StateFlow<BudgetExportState> = _uiState.asStateFlow()

    private var currentStatuses: List<BudgetStatus> = emptyList()
    private var currentReport: MonthlyBudgetReport.ReportModel? = null
    private var preparedFile: PreparedFile? = null
    private var loadJob: Job? = null

    init {
        loadMonth(initialMonth)
    }

    fun previousMonth() {
        if (_uiState.value.busy) return
        val target = _uiState.value.month.minusMonths(1)
        loadMonth(target)
    }

    fun nextMonth() {
        if (_uiState.value.busy) return
        val maxMonth = currentYearMonth()
        val candidate = _uiState.value.month.plusMonths(1)
        val target = if (candidate > maxMonth) maxMonth else candidate
        if (target != _uiState.value.month) {
            loadMonth(target)
        }
    }

    fun selectFormat(format: BudgetExportFormat) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(format = format) }
    }

    fun prepareFile(): Pair<String, ByteArray>? {
        val state = _uiState.value
        if (state.rowCount == 0 || state.loading || state.busy) return null
        val report = currentReport ?: buildReport(currentStatuses, state.month)
        if (report.rows.isEmpty()) return null

        val fileName = state.format.fileName(state.month)
        val bytes = when (state.format) {
            BudgetExportFormat.CSV -> UTF8_BOM + MonthlyBudgetCsv.build(report).toByteArray(Charsets.UTF_8)
            BudgetExportFormat.XLSX -> MonthlyBudgetXlsx.build(report)
        }
        preparedFile = PreparedFile(
            fileName = fileName,
            bytes = bytes,
            rowCount = report.rows.size,
            format = state.format
        )
        return fileName to bytes
    }

    fun onFileChosen(uri: Uri?) {
        if (uri == null) {
            preparedFile = null
            return
        }
        if (_uiState.value.busy) return

        val prepared = preparedFile ?: run {
            prepareFile()
            preparedFile
        } ?: return
        preparedFile = null

        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = byteFileWriter.write(uri, prepared.bytes)
            val resultMessage = result.fold(
                onSuccess = { BudgetExportMessages.saveSuccess(prepared.rowCount, prepared.format) },
                onFailure = { BudgetExportMessages.SAVE_ERROR }
            )
            _uiState.update {
                it.copy(busy = false, message = resultMessage)
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadMonth(month: YearMonth) {
        loadJob?.cancel()
        val nowMonth = currentYearMonth()
        _uiState.update {
            it.copy(
                month = month,
                currentMonth = nowMonth,
                loading = true
            )
        }
        loadJob = viewModelScope.launch {
            val result = getMonthlyBudgetStatuses(month)
            result.fold(
                onSuccess = { statuses ->
                    currentStatuses = statuses
                    currentReport = buildReport(statuses, month)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            rowCount = statuses.size
                        )
                    }
                },
                onFailure = { error ->
                    currentStatuses = emptyList()
                    currentReport = null
                    val errMsg = if (error.message?.contains("Sign in required", ignoreCase = true) == true) {
                        BudgetExportMessages.SIGN_IN_REQUIRED
                    } else {
                        BudgetExportMessages.SAVE_ERROR
                    }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            rowCount = 0,
                            message = errMsg
                        )
                    }
                }
            )
        }
    }

    private fun buildReport(
        statuses: List<BudgetStatus>,
        month: YearMonth
    ): MonthlyBudgetReport.ReportModel =
        MonthlyBudgetReport.build(
            statuses = statuses,
            month = month,
            generatedAtMillis = clock.millis(),
            statusLabel = { threshold -> budgetStatusText(threshold) },
            zone = ZoneId.systemDefault()
        )

    private fun currentYearMonth(): YearMonth =
        YearMonth.parse(BudgetPolicy.currentMonth(clock, ZoneId.systemDefault()))

    private companion object {
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
