package com.example.clarity.presentation.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalEditScreen(
    onBack: () -> Unit,
    viewModel: GoalEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onBack()
        }
    }

    GoalEditContent(
        state = state,
        onBack = onBack,
        onRetryLoad = viewModel::loadInitialState,
        onNameChanged = viewModel::onNameChanged,
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onTargetAmountChanged = viewModel::onTargetAmountChanged,
        onCurrencyChanged = viewModel::onCurrencyChanged,
        onTargetDateSelected = viewModel::onTargetDateSelected,
        onSave = viewModel::save
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalEditContent(
    state: GoalEditState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onTargetAmountChanged: (String) -> Unit,
    onCurrencyChanged: (String) -> Unit,
    onTargetDateSelected: (Long?) -> Unit,
    onSave: () -> Unit
) {
    val titleText = if (state.isEditMode) "Edit Goal" else "Create Goal"
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    val selectedCurrency = state.currency.ifBlank { Currencies.supported.first() }
    val canEditCurrency = !state.isSaving && !state.isEditMode

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDateUtcMillis.takeIf { it != 0L }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let(onTargetDateSelected)
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.fatalError != null -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GoalErrorBanner(
                            message = state.fatalError,
                            onRetry = onRetryLoad
                        )
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Goals")
                        }
                    }
                }

                else -> {
                    val colors = MaterialTheme.colorScheme
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        state.formError?.let { errorMsg ->
                            GoalErrorBanner(
                                message = errorMsg,
                                onRetry = onSave
                            )
                        }

                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onNameChanged,
                            leadingIcon = {
                                Icon(
                                    imageVector = AppIcons.Target,
                                    contentDescription = null,
                                    tint = colors.primary
                                )
                            },
                            label = { Text("Goal *") },
                            placeholder = { Text("Emergency Fund") },
                            singleLine = true,
                            enabled = !state.isSaving,
                            isError = state.nameError != null,
                            supportingText = {
                                val error = state.nameError
                                if (error != null) {
                                    Text(error)
                                } else {
                                    Text("${state.name.trim().length}/${GoalPolicy.MAX_NAME_LENGTH}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        ExposedDropdownMenuBox(
                            expanded = currencyMenuExpanded,
                            onExpandedChange = { if (canEditCurrency) currencyMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedCurrency,
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (state.isEditMode) Icons.Default.Lock else AppIcons.SwapHoriz,
                                        contentDescription = null,
                                        tint = colors.primary
                                    )
                                },
                                label = { Text("Currency *") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyMenuExpanded)
                                },
                                enabled = canEditCurrency,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = currencyMenuExpanded && canEditCurrency,
                                onDismissRequest = { currencyMenuExpanded = false }
                            ) {
                                Currencies.supported.forEach { code ->
                                    DropdownMenuItem(
                                        text = { Text(code) },
                                        onClick = {
                                            onCurrencyChanged(code)
                                            currencyMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.targetAmount,
                            onValueChange = onTargetAmountChanged,
                            leadingIcon = {
                                Icon(
                                    imageVector = AppIcons.Savings,
                                    contentDescription = null,
                                    tint = colors.primary
                                )
                            },
                            label = { Text("Target (${state.currency}) *") },
                            placeholder = { Text("0.00") },
                            prefix = { Text("${state.currency} ") },
                            singleLine = true,
                            enabled = !state.isSaving,
                            isError = state.targetAmountError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            supportingText = state.targetAmountError?.let { error ->
                                { Text(error) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val borderColor = if (state.targetDateError != null) {
                                colors.error
                            } else {
                                colors.outline
                            }
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .semantics {
                                        contentDescription = "Target date ${state.targetDateFormatted}"
                                    }
                                    .clickable(
                                        enabled = !state.isSaving,
                                        role = Role.Button
                                    ) {
                                        showDatePicker = true
                                    },
                                border = BorderStroke(1.dp, borderColor),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = colors.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Calendar,
                                            contentDescription = null,
                                            tint = colors.primary
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Date *",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (state.targetDateError != null) {
                                                    colors.error
                                                } else {
                                                    colors.onSurfaceVariant
                                                }
                                            )
                                            Text(
                                                text = state.targetDateFormatted,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = colors.onSurface
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant
                                    )
                                }
                            }

                            state.targetDateError?.let { dateErr ->
                                Text(
                                    text = dateErr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.error,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.description,
                            onValueChange = onDescriptionChanged,
                            leadingIcon = {
                                Icon(
                                    imageVector = AppIcons.Notes,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant
                                )
                            },
                            label = { Text("Note") },
                            placeholder = { Text("Optional") },
                            minLines = 2,
                            maxLines = 4,
                            enabled = !state.isSaving,
                            isError = state.descriptionError != null,
                            supportingText = {
                                val error = state.descriptionError
                                if (error != null) {
                                    Text(error)
                                } else {
                                    Text("${state.description.trim().length}/${GoalPolicy.MAX_DESCRIPTION_LENGTH}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBack,
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = onSave,
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (state.isEditMode) "Save" else "Create")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalEditScreenPreview() {
    ClarityTheme {
        GoalEditContent(
            state = GoalEditState(
                isLoading = false,
                currency = "LKR",
                name = "Emergency Fund",
                description = "Six months of living expenses",
                targetAmount = "500000.00",
                targetDateEpochDay = 21000L,
                selectedDateUtcMillis = 21000L * 86_400_000L,
                targetDateFormatted = "29 Jun 2027"
            ),
            onBack = {},
            onRetryLoad = {},
            onNameChanged = {},
            onDescriptionChanged = {},
            onTargetAmountChanged = {},
            onCurrencyChanged = {},
            onTargetDateSelected = {},
            onSave = {}
        )
    }
}
