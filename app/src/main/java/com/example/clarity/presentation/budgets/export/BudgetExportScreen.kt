package com.example.clarity.presentation.budgets.export

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.export.BudgetExportFormat
import com.example.clarity.presentation.theme.ClarityTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BudgetExportScreen(
    onBack: () -> Unit,
    viewModel: BudgetExportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BudgetExportFormat.CSV.mimeType),
        onResult = viewModel::onFileChosen
    )
    val createXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BudgetExportFormat.XLSX.mimeType),
        onResult = viewModel::onFileChosen
    )

    BackHandler(enabled = state.busy) {}

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            viewModel.clearMessage()
            snackbarHostState.showSnackbar(message)
        }
    }

    BudgetExportContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = {
            if (!state.busy) {
                onBack()
            }
        },
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectFormat = viewModel::selectFormat,
        onSaveToDevice = {
            val prepared = viewModel.prepareFile()
            if (prepared != null) {
                val (fileName, _) = prepared
                when (state.format) {
                    BudgetExportFormat.CSV -> createCsvLauncher.launch(fileName)
                    BudgetExportFormat.XLSX -> createXlsxLauncher.launch(fileName)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetExportContent(
    state: BudgetExportState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectFormat: (BudgetExportFormat) -> Unit,
    onSaveToDevice: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    }
    val actionsEnabled = state.rowCount > 0 && !state.loading && !state.busy

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = BudgetExportMessages.SCREEN_TITLE,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !state.busy
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = BudgetExportMessages.BACK,
                            tint = colors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = colors.background
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(
                        onClick = onSaveToDevice,
                        enabled = actionsEnabled,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = BudgetExportMessages.SAVE_TO_DEVICE,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. MONTH card
                BudgetExportSection(title = BudgetExportMessages.SECTION_MONTH) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onPreviousMonth,
                            enabled = !state.busy
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = BudgetExportMessages.PREVIOUS_MONTH
                            )
                        }
                        Text(
                            text = state.month.format(monthFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center
                        )
                        IconButton(
                            onClick = onNextMonth,
                            enabled = !state.busy && state.month < state.currentMonth
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = BudgetExportMessages.NEXT_MONTH
                            )
                        }
                    }
                }

                // 2. FORMAT card
                BudgetExportSection(title = BudgetExportMessages.SECTION_FORMAT) {
                    BudgetFormatOptionRow(
                        title = BudgetExportMessages.FORMAT_CSV,
                        description = BudgetExportMessages.CSV_DESCRIPTION,
                        selected = state.format == BudgetExportFormat.CSV,
                        enabled = !state.busy,
                        onClick = { onSelectFormat(BudgetExportFormat.CSV) }
                    )
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colors.onSurface.copy(alpha = 0.08f)
                    )
                    BudgetFormatOptionRow(
                        title = BudgetExportMessages.FORMAT_XLSX,
                        description = BudgetExportMessages.XLSX_DESCRIPTION,
                        selected = state.format == BudgetExportFormat.XLSX,
                        enabled = !state.busy,
                        onClick = { onSelectFormat(BudgetExportFormat.XLSX) }
                    )
                }

                // 3. SUMMARY card
                BudgetExportSection(title = BudgetExportMessages.SECTION_SUMMARY) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (state.loading || state.busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(
                                text = BudgetExportMessages.rowCountText(state.rowCount),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (state.rowCount > 0) {
                                    colors.onSurface
                                } else {
                                    colors.onSurfaceVariant
                                }
                            )
                        }
                        Text(
                            text = BudgetExportMessages.YEARLY_NOTE,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetExportSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun BudgetFormatOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BudgetExportScreenPreview() {
    ClarityTheme {
        BudgetExportContent(
            state = BudgetExportState(
                month = YearMonth.of(2026, 10),
                currentMonth = YearMonth.of(2026, 10),
                format = BudgetExportFormat.CSV,
                loading = false,
                rowCount = 3,
                busy = false
            ),
            onBack = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onSelectFormat = {},
            onSaveToDevice = {}
        )
    }
}
