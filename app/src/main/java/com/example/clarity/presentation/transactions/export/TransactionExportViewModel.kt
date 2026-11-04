package com.example.clarity.presentation.transactions.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.data.export.CsvFileWriter
import com.example.clarity.data.export.PdfReportWriter
import com.example.clarity.domain.export.ExportFormat
import com.example.clarity.domain.export.MonthlyTransactionCsv
import com.example.clarity.domain.export.MonthlyTransactionReport
import com.example.clarity.domain.export.ReportModel
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.Resource
import com.example.clarity.domain.util.TransactionCalendar
import com.example.clarity.presentation.transactions.TransactionMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionExportState(
    val month: YearMonth = YearMonth.from(TransactionCalendar.dateAt(System.currentTimeMillis())),
    val format: ExportFormat = ExportFormat.CSV,
    val recordCount: Int = 0,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class TransactionExportViewModel @Inject constructor(
    private val getTransactions: GetTransactionsUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val csvFileWriter: CsvFileWriter,
    private val pdfReportWriter: PdfReportWriter
) : ViewModel() {

    private sealed interface PreparedPayload {
        val count: Int
        val format: ExportFormat

        data class Csv(override val count: Int, val content: String) : PreparedPayload {
            override val format: ExportFormat = ExportFormat.CSV
        }

        data class Pdf(override val count: Int, val report: ReportModel) : PreparedPayload {
            override val format: ExportFormat = ExportFormat.PDF
        }
    }

    private val _uiState = MutableStateFlow(TransactionExportState())
    val uiState: StateFlow<TransactionExportState> = _uiState.asStateFlow()

    private var transactions: List<Transaction> = emptyList()
    private var accountNames: Map<String, String> = emptyMap()
    private var preparedPayload: PreparedPayload? = null

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(getTransactions(), getAccounts()) { txResult, accountsResult ->
                txResult to accountsResult
            }.collect { (result, accounts) ->
                val mappedAccounts = accounts.data
                    ?.associate { account -> account.id to account.name }
                if (mappedAccounts != null) {
                    accountNames = mappedAccounts
                }
                when (result) {
                    is Resource.Loading -> Unit
                    is Resource.Success, is Resource.Error -> {
                        transactions = result.data.orEmpty()
                        val count = MonthlyTransactionCsv.recordsFor(transactions, _uiState.value.month).size
                        _uiState.update {
                            it.copy(loading = false, recordCount = count)
                        }
                    }
                }
            }
        }
    }

    fun previousMonth() {
        _uiState.update { current ->
            val targetMonth = current.month.minusMonths(1)
            val count = MonthlyTransactionCsv.recordsFor(transactions, targetMonth).size
            current.copy(month = targetMonth, recordCount = count)
        }
    }

    fun nextMonth() {
        val currentYearMonth = currentMonth()
        _uiState.update { current ->
            val candidate = current.month.plusMonths(1)
            val targetMonth = if (candidate > currentYearMonth) currentYearMonth else candidate
            val count = MonthlyTransactionCsv.recordsFor(transactions, targetMonth).size
            current.copy(month = targetMonth, recordCount = count)
        }
    }

    fun selectFormat(format: ExportFormat) {
        _uiState.update { current ->
            current.copy(format = format)
        }
    }

    fun prepareExport(): String? {
        val state = _uiState.value
        if (state.recordCount == 0 || state.busy || state.loading) return null
        val records = MonthlyTransactionCsv.recordsFor(transactions, state.month)
        if (records.isEmpty()) return null

        val namesSnapshot = accountNames
        val accountLabel: (String?) -> String = { accountId ->
            if (accountId == null) {
                TransactionMessages.ACCOUNT_UNASSIGNED
            } else {
                namesSnapshot[accountId] ?: TransactionMessages.ACCOUNT_UNAVAILABLE
            }
        }

        preparedPayload = when (state.format) {
            ExportFormat.CSV -> PreparedPayload.Csv(
                count = records.size,
                content = MonthlyTransactionCsv.build(
                    records = records,
                    accountLabel = accountLabel
                )
            )
            ExportFormat.PDF -> PreparedPayload.Pdf(
                count = records.size,
                report = MonthlyTransactionReport.build(
                    records = records,
                    accountLabel = accountLabel,
                    month = state.month,
                    generatedAtMillis = System.currentTimeMillis()
                )
            )
        }

        return state.format.fileName(state.month)
    }

    fun onFileChosen(uri: Uri?) {
        if (uri == null) {
            preparedPayload = null
            return
        }
        val state = _uiState.value
        if (state.busy) return

        val payload = preparedPayload ?: run {
            prepareExport()
            preparedPayload
        } ?: return
        preparedPayload = null

        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val writeResult = when (payload) {
                is PreparedPayload.Csv -> csvFileWriter.write(uri, payload.content)
                is PreparedPayload.Pdf -> pdfReportWriter.write(uri, payload.report)
            }
            val resultMessage = writeResult.fold(
                onSuccess = { ExportMessages.exportSuccess(payload.count, payload.format) },
                onFailure = { ExportMessages.EXPORT_ERROR }
            )
            _uiState.update {
                it.copy(busy = false, message = resultMessage)
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun currentMonth(): YearMonth =
        YearMonth.from(TransactionCalendar.dateAt(System.currentTimeMillis()))
}
