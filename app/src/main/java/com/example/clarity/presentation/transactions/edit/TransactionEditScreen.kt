package com.example.clarity.presentation.transactions.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.clarity.R
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.formatTransactionDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun TransactionEditScreen(
    onBack: () -> Unit,
    viewModel: TransactionEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.isSuccess) { if (state.isSuccess) onBack() }
    TransactionEditContent(state, onBack, viewModel::save)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditContent(
    state: TransactionEditState,
    onBack: () -> Unit,
    onSave: (TransactionType, String, String, String, Long) -> Unit
) {
    var typeName by rememberSaveable { mutableStateOf(TransactionType.EXPENSE.name) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var dateEpochMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var loadedId by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var dateDialogVisible by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.transaction?.id, state.transaction?.userId) {
        state.transaction?.let { transaction ->
            if (loadedId != transaction.id || loadedOwner != transaction.userId) {
                typeName = transaction.type.name
                amount = MoneyUtils.formatMinor(transaction.amountMinor)
                category = transaction.category
                note = transaction.note
                dateEpochMillis = transaction.dateEpochMillis
                loadedId = transaction.id
                loadedOwner = transaction.userId
            }
        }
    }

    if (dateDialogVisible) {
        // DatePicker uses UTC-midnight dates. Convert calendar days, not raw UTC instants,
        // so selecting a day keeps that same date in the device's local timezone.
        val initialDate = Instant.ofEpochMilli(dateEpochMillis).atZone(ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        LaunchedEffect(pickerState.selectedDateMillis) {
            pickerState.selectedDateMillis?.takeIf { it != initialDate }?.let { selected ->
                dateEpochMillis = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC)
                    .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                dateDialogVisible = false
            }
        }
        DatePickerDialog(
            onDismissRequest = { dateDialogVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selected ->
                        dateEpochMillis = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC)
                            .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    dateDialogVisible = false
                }, enabled = pickerState.selectedDateMillis != null) {
                    Text(stringResource(R.string.transactions_date_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { dateDialogVisible = false }) { Text(stringResource(R.string.transactions_cancel)) } }
        ) { DatePicker(state = pickerState) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (state.isEditMode) R.string.transactions_edit_title else R.string.transactions_add_title)) },
            navigationIcon = { TextButton(onClick = onBack, enabled = !state.isSaving) { Text(stringResource(R.string.transactions_back)) } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (state.isLoading) CircularProgressIndicator()
            else if (state.isEditMode && state.transaction == null) {
                if (state.error == null) Text(stringResource(R.string.transactions_not_found))
            } else {
                LaunchedEffect(Unit) { if (!state.isEditMode) amountFocus.requestFocus() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = typeName == TransactionType.EXPENSE.name,
                        onClick = { typeName = TransactionType.EXPENSE.name },
                        label = { Text(stringResource(R.string.transactions_expense)) }, enabled = !state.isSaving
                    )
                    FilterChip(
                        selected = typeName == TransactionType.INCOME.name,
                        onClick = { typeName = TransactionType.INCOME.name },
                        label = { Text(stringResource(R.string.transactions_income)) }, enabled = !state.isSaving
                    )
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.transactions_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.amountError != null,
                    supportingText = { state.amountError?.let { Text(stringResource(it)) } },
                    enabled = !state.isSaving, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus)
                )
                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    label = { Text(stringResource(R.string.transactions_category)) },
                    placeholder = { Text(stringResource(R.string.transactions_general)) },
                    isError = state.categoryError != null,
                    supportingText = { state.categoryError?.let { Text(stringResource(it)) } },
                    enabled = !state.isSaving, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.transactions_note)) },
                    isError = state.noteError != null,
                    supportingText = { state.noteError?.let { Text(stringResource(it)) } },
                    enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { dateDialogVisible = true }, enabled = !state.isSaving) {
                    Text(stringResource(R.string.transactions_date_value, formatTransactionDate(dateEpochMillis)))
                }
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSave(TransactionType.valueOf(typeName), amount, category, note, dateEpochMillis)
                    },
                    enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.transactions_save)) }
                if (state.isSaving) CircularProgressIndicator()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionEditPreview() {
    ClarityTheme { TransactionEditContent(TransactionEditState(), {}, { _, _, _, _, _ -> }) }
}
