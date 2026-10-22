package com.example.clarity.presentation.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalEditScreen(
    onBack: () -> Unit,
    viewModel: GoalEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) onBack()
    }
    GoalEditContent(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onNameChange = viewModel::onNameChange,
        onTargetAmountChange = viewModel::onTargetAmountChange,
        onSavedAmountChange = viewModel::onSavedAmountChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onTargetDateSelected = viewModel::onTargetDateSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalEditContent(
    state: GoalEditState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onTargetAmountChange: (String) -> Unit,
    onSavedAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTargetDateSelected: (Long) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val enabled = state.canSave && !state.isSaving && !state.isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditMode) "Edit financial goal" else "Create financial goal")
                },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.isSaving) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (state.isLoading) {
                    CircularProgressIndicator()
                } else if (!state.canSave) {
                    OutlinedButton(onClick = onBack) {
                        Text("Back to Goals")
                    }
                } else {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = { Text("Goal name") },
                        isError = state.nameError != null,
                        supportingText = {
                            Text(state.nameError ?: "Required, up to 100 characters.")
                        },
                        enabled = enabled,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.currency,
                        onValueChange = {},
                        label = { Text("Currency (recorded at creation)") },
                        readOnly = true,
                        enabled = false,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.targetAmount,
                        onValueChange = onTargetAmountChange,
                        label = { Text("Target amount (${state.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.targetAmountError != null,
                        supportingText = {
                            state.targetAmountError?.let { Text(it) }
                        },
                        enabled = enabled,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!state.isEditMode) {
                        OutlinedTextField(
                            value = state.savedAmount,
                            onValueChange = onSavedAmountChange,
                            label = { Text("Current saved amount (${state.currency})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = state.savedAmountError != null,
                            supportingText = {
                                Text(
                                    state.savedAmountError
                                        ?: "Optional starting balance (defaults to 0)."
                                )
                            },
                            enabled = enabled,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = state.currentSavedFormatted,
                            onValueChange = {},
                            label = { Text("Current saved amount (use Update progress to change)") },
                            readOnly = true,
                            enabled = false,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = state.description,
                        onValueChange = onDescriptionChange,
                        label = { Text("Description (optional)") },
                        isError = state.descriptionError != null,
                        supportingText = {
                            Text(state.descriptionError ?: "Up to 500 characters.")
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (state.targetDateError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Target date: ${state.formattedTargetDate}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            state.targetDateError?.let { dateErr ->
                                Text(
                                    text = dateErr,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            val pickerState = rememberDatePickerState(
                                initialSelectedDateMillis = state.selectedDateUtcMillis
                            )
                            LaunchedEffect(state.selectedDateUtcMillis) {
                                if (pickerState.selectedDateMillis != state.selectedDateUtcMillis) {
                                    pickerState.setSelection(state.selectedDateUtcMillis)
                                }
                            }
                            LaunchedEffect(pickerState.selectedDateMillis) {
                                pickerState.selectedDateMillis
                                    ?.takeIf { it != state.selectedDateUtcMillis }
                                    ?.let { selected ->
                                        onTargetDateSelected(selected)
                                    }
                            }
                            DatePicker(
                                state = pickerState,
                                title = null,
                                headline = null,
                                showModeToggle = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            enabled = !state.isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onSave()
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }

                    if (state.isSaving) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalEditPreview() {
    ClarityTheme {
        GoalEditContent(
            state = GoalEditState(
                isLoading = false,
                canSave = true,
                formattedTargetDate = "20 Nov 2026"
            ),
            onBack = {},
            onSave = {},
            onNameChange = {},
            onTargetAmountChange = {},
            onSavedAmountChange = {},
            onDescriptionChange = {},
            onTargetDateSelected = {}
        )
    }
}

