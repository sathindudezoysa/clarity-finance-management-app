package com.example.clarity.presentation.budgets

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.BudgetSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    selectedId: String? = null,
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var scrolledId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedId) {
        if (selectedId == null) scrolledId = null
        viewModel.applySelectedId(selectedId)
    }

    val monthView = state.monthView
    val scrollIndex = BudgetSummary.scrollIndexFor(selectedId, monthView, headerItemCount = 1)
    LaunchedEffect(selectedId, state.handledSelectedId, scrollIndex) {
        if (selectedId != null && state.handledSelectedId == selectedId &&
            scrolledId != selectedId && scrollIndex != null
        ) {
            scrolledId = selectedId
            listState.animateScrollToItem(scrollIndex)
        }
    }

    LaunchedEffect(state.notice) {
        val message = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeNotice()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(BudgetMessages.SCREEN_TITLE) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetMonthSelector(
                        selectedMonth = monthView.month,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth
                    )
                    Text(
                        text = "Calendar months use your device’s current timezone, matching transaction dates. Changing timezone can move spending between months. Existing budgets keep their currency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.edit() },
                        enabled = state.overview.ownerId != null && !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create monthly budget")
                    }
                    BudgetNotificationPermission()
                    if (state.loading) CircularProgressIndicator()
                    if (state.overview.ownerId == null && !state.loading) {
                        Text("Sign in to manage your budgets.")
                    }
                    if (state.overview.syncError) {
                        Text(
                            text = "Cloud sync is unavailable. Local changes are saved and will retry automatically.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.editor == null) {
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    if (!state.loading && state.overview.ownerId != null && monthView.active.isEmpty()) {
                        Text(BudgetMessages.EMPTY_MONTH)
                    }
                }
            }
            items(monthView.summaries, key = { "summary-${it.currency}" }) { summary ->
                BudgetSummaryCard(summary = summary)
            }
            items(monthView.active, key = { it.budget.id }) { status ->
                BudgetCard(
                    status = status,
                    selected = status.budget.id == selectedId,
                    live = status.budget.month == state.currentMonth,
                    onEdit = { viewModel.edit(status.budget) },
                    onDisable = { viewModel.requestDisable(status.budget) },
                    onRetry = { viewModel.retryCurrency(status.budget.id) },
                    enabled = !state.busy
                )
            }
            if (monthView.disabled.isNotEmpty()) {
                item(key = "disabled-header") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Divider()
                        Text(
                            text = "Disabled (${budgetMonthLabel(monthView.month)})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                items(monthView.disabled, key = { "disabled-${it.budget.id}" }) { status ->
                    TextButton(
                        onClick = { viewModel.edit(status.budget) },
                        enabled = !state.busy
                    ) {
                        Text("${budgetCategoryIcon(status.budget.category)} ${status.budget.category} · ${status.budget.month} · Disabled — tap to enable")
                    }
                }
            }
            item(key = "footer") { Spacer(Modifier.height(20.dp)) }
        }
    }

    state.editor?.let { editor ->
        val isCreate = editor.budget == null
        AlertDialog(
            onDismissRequest = { if (!state.busy) viewModel.closeEditor() },
            title = { Text(if (isCreate) "Create monthly budget" else "Edit budget") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BudgetCategorySelector(
                        value = editor.category,
                        tags = state.tags,
                        onValueChange = viewModel::changeCategory,
                        enabled = isCreate && !state.busy,
                        error = editor.categoryError
                    )
                    BudgetMonthStepperField(
                        month = editor.month,
                        onMonthChange = viewModel::changeMonth,
                        enabled = isCreate && !state.busy,
                        error = editor.monthError
                    )
                    OutlinedTextField(
                        value = editor.amount,
                        onValueChange = viewModel::changeAmount,
                        label = { Text("Limit (${editor.currency})") },
                        singleLine = true,
                        enabled = !state.busy,
                        isError = editor.amountError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            Text(editor.amountError ?: "Currency: ${editor.currency}")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val dialogError = editor.duplicateError ?: state.error?.takeIf {
                        it != editor.categoryError && it != editor.monthError && it != editor.amountError
                    }
                    dialogError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::save, enabled = !state.busy) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeEditor, enabled = !state.busy) {
                    Text("Cancel")
                }
            }
        )
    }

    state.confirmDisable?.let { target ->
        DisableBudgetDialog(
            budget = target,
            busy = state.busy,
            onConfirm = viewModel::confirmDisable,
            onDismiss = viewModel::dismissDisable
        )
    }
}

@Composable
fun BudgetDashboardSummary(
    onOpen: (String?) -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column {
        Button(onClick = { onOpen(null) }) { Text("Budgets") }
        state.overview.statuses.filter {
            it.budget.active && it.budget.month == state.currentMonth &&
                (it.threshold.level > 0 || it.uncounted > 0)
        }.take(3).forEach { status ->
            TextButton(onClick = { onOpen(status.budget.id) }) {
                Column {
                    Text(budgetCategoryMonthText(status.budget.category, status.budget.month))
                    Text(budgetStatusText(status.threshold, status.uncounted > 0))
                    Text(
                        budgetSpendingText(
                            budgetMoney(status.spent, status.budget.currency),
                            budgetMoney(status.budget.limitMinor.toBigInteger(), status.budget.currency)
                        )
                    )
                    if (status.remaining.signum() < 0) {
                        Text(budgetExceededByText(budgetMoney(-status.remaining, status.budget.currency)))
                    }
                    if (status.threshold.level > 0) {
                        Text(budgetFeedbackText(status.threshold, status.budget.category))
                    }
                    if (status.uncounted > 0) {
                        Text(budgetIncompleteText(status.uncounted))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetNotificationPermission() {
    val context = LocalContext.current
    var rationale by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("budget_permission", 0) }
    var asked by remember { mutableStateOf(prefs.getBoolean("asked", false)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked = true }
    fun request() {
        prefs.edit().putBoolean("asked", true).apply()
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Text(
        "Optional local notifications tell you when a current monthly budget crosses a threshold. Budget warnings remain available here if notifications are off.",
        style = MaterialTheme.typography.bodySmall
    )
    TextButton(onClick = {
        val activity = context as? Activity
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        } else if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) rationale = true
        else if (asked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        else request()
    }) { Text("Enable or manage budget notifications") }
    if (rationale) AlertDialog(
        onDismissRequest = { rationale = false },
        title = { Text("Budget update") },
        text = { Text("Optional local notifications tell you when a current monthly budget crosses a threshold. Budget warnings remain available here if notifications are off.") },
        confirmButton = { TextButton(onClick = { rationale = false; request() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text("Cancel") } }
    )
}
