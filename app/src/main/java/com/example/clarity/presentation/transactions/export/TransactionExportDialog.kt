package com.example.clarity.presentation.transactions.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.export.ExportFormat
import com.example.clarity.domain.util.TransactionCalendar
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionExportDialog(
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
    viewModel: TransactionExportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMonth = remember {
        YearMonth.from(TransactionCalendar.dateAt(System.currentTimeMillis()))
    }
    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType),
        onResult = viewModel::onFileChosen
    )
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType),
        onResult = viewModel::onFileChosen
    )

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            onResult(message)
            viewModel.clearMessage()
            onDismiss()
        }
    }

    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    }

    AlertDialog(
        onDismissRequest = {
            if (!state.busy) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = ExportMessages.DIALOG_TITLE,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = viewModel::previousMonth,
                        enabled = !state.busy
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = ExportMessages.PREVIOUS_MONTH
                        )
                    }
                    Text(
                        text = state.month.format(monthFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = viewModel::nextMonth,
                        enabled = !state.busy && state.month < currentMonth
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = ExportMessages.NEXT_MONTH
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = ExportMessages.FORMAT_LABEL,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = state.format == ExportFormat.CSV,
                        onClick = { viewModel.selectFormat(ExportFormat.CSV) },
                        enabled = !state.busy,
                        label = { Text(text = ExportMessages.FORMAT_CSV) }
                    )
                    FilterChip(
                        selected = state.format == ExportFormat.PDF,
                        onClick = { viewModel.selectFormat(ExportFormat.PDF) },
                        enabled = !state.busy,
                        label = { Text(text = ExportMessages.FORMAT_PDF) }
                    )
                }

                if (state.loading || state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = ExportMessages.recordCountText(state.recordCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fileName = viewModel.prepareExport()
                    if (fileName != null) {
                        when (state.format) {
                            ExportFormat.CSV -> createCsvLauncher.launch(fileName)
                            ExportFormat.PDF -> createPdfLauncher.launch(fileName)
                        }
                    }
                },
                enabled = state.recordCount > 0 && !state.busy && !state.loading
            ) {
                Text(text = ExportMessages.EXPORT_BUTTON)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.busy
            ) {
                Text(text = ExportMessages.CANCEL_BUTTON)
            }
        }
    )
}
