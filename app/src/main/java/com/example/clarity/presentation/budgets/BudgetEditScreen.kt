package com.example.clarity.presentation.budgets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.BudgetPeriod
import com.example.clarity.presentation.transactions.edit.TransactionTagField
import kotlinx.coroutines.launch

@Composable
fun BudgetEditScreen(
    budgetId: String? = null,
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var saveSubmitted by rememberSaveable { mutableStateOf(false) }
    var closing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(budgetId, state.overview.ownerId, state.overview.statuses.size) {
        if (!closing && !saveSubmitted && state.editor == null && state.overview.ownerId != null) {
            val target = budgetId?.let { id ->
                state.overview.statuses.firstOrNull { it.budget.id == id }?.budget
            }
            if (budgetId == null || target != null) {
                viewModel.edit(target)
            }
        }
    }

    LaunchedEffect(saveSubmitted, state.busy, state.editor, state.error) {
        if (saveSubmitted && !state.busy) {
            if (state.editor == null && state.error == null) {
                closing = true
                saveSubmitted = false
                onBack()
            } else if (state.error != null) {
                saveSubmitted = false
            }
        }
    }

    val cancel: () -> Unit = {
        if (!state.busy) {
            closing = true
            viewModel.closeEditor()
            onBack()
        }
    }

    BackHandler(enabled = !state.busy, onBack = cancel)

    BudgetEditContent(
        state = state,
        onCategoryChange = viewModel::changeCategory,
        onAmountChange = viewModel::changeAmount,
        onPeriodChange = viewModel::changePeriod,
        onSave = {
            saveSubmitted = true
            viewModel.save()
        },
        onCancel = cancel
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetEditContent(
    state: BudgetsState,
    onCategoryChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onPeriodChange: (BudgetPeriod) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val editor = state.editor
    val busy = state.busy
    val canEditIdentity = editor?.budget == null && !busy
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val limitRequester = remember { BringIntoViewRequester() }
    val tagRequester = remember { BringIntoViewRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (editor?.budget == null) BudgetUiText.CREATE else BudgetUiText.EDIT_TITLE)
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !busy) {
                        Icon(Icons.Default.ArrowBack, contentDescription = BudgetUiText.BACK)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (editor == null) {
                if (state.loading) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedTextField(
                    value = editor.amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(limitRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { limitRequester.bringIntoView() } },
                    label = { Text(budgetLimitLabel(editor.currency)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    )
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editor.period == BudgetPeriod.MONTHLY,
                            onClick = { onPeriodChange(BudgetPeriod.MONTHLY) },
                            label = { Text(BudgetUiText.PERIOD_MONTHLY) },
                            enabled = canEditIdentity
                        )
                        FilterChip(
                            selected = editor.period == BudgetPeriod.YEARLY,
                            onClick = { onPeriodChange(BudgetPeriod.YEARLY) },
                            label = { Text(BudgetUiText.PERIOD_YEARLY) },
                            enabled = canEditIdentity
                        )
                    }
                    Text(
                        text = if (editor.period == BudgetPeriod.YEARLY) BudgetUiText.RESET_YEARLY_HINT else BudgetUiText.RESET_MONTHLY_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(tagRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { tagRequester.bringIntoView() } }
                ) {
                    TransactionTagField(
                        value = editor.category,
                        tags = state.tags,
                        onValueChange = onCategoryChange,
                        enabled = canEditIdentity,
                        error = null,
                        modifier = Modifier.fillMaxWidth(),
                        hint = "Tap a category or type a new one."
                    )
                }
                state.error?.let {
                    BudgetMessageBanner(Icons.Default.Warning, it, isError = true)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(BudgetUiText.CANCEL)
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSave()
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(BudgetUiText.SAVE)
                    }
                }
            }
        }
    }
}
