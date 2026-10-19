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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.model.BudgetThreshold
import com.example.clarity.presentation.transactions.edit.TransactionTagField
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(onBack: () -> Unit, selectedId: String? = null, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Highlighting/navigation only selects a budget already loaded from the authenticated owner's stream.
    val rows = state.overview.statuses.filter { it.budget.active }
    LaunchedEffect(selectedId, rows.map { it.budget.id }) {
        val index = rows.indexOfFirst { it.budget.id == selectedId }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Budgets") },
        navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column {
                    Text("Calendar months use your device’s current timezone, matching transaction dates. Changing timezone can move spending between months. Existing budgets keep their currency.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { viewModel.edit() }, enabled = state.overview.ownerId != null && !state.busy) { Text("Create monthly budget") }
                    BudgetNotificationPermission()
                    if (state.loading) CircularProgressIndicator()
                    if (state.overview.ownerId == null && !state.loading) Text("Sign in to manage your budgets.")
                    if (state.overview.syncError) Text("Cloud sync is unavailable. Local changes are saved and will retry automatically.", color = MaterialTheme.colorScheme.error)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!state.loading && rows.isEmpty()) Text("Create a category budget to follow your monthly spending.")
                }
            }
            items(rows, key = { it.budget.id }) { status ->
                BudgetCard(status, selected = status.budget.id == selectedId,
                    live = status.budget.month == state.currentMonth,
                    onEdit = { viewModel.edit(status.budget) }, onDisable = { viewModel.disable(status.budget.id) },
                    onRetry = { viewModel.retryCurrency(status.budget.id) }, enabled = !state.busy)
            }
            val disabled = state.overview.statuses.filter { !it.budget.active }
            items(disabled, key = { "disabled-${it.budget.id}" }) { status ->
                TextButton(onClick = { viewModel.edit(status.budget) }, enabled = !state.busy) {
                    Text("${status.budget.category} · ${status.budget.month} · Disabled — tap to enable")
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    state.editor?.let { editor ->
        AlertDialog(onDismissRequest = { if (!state.busy) viewModel.closeEditor() },
            title = { Text(if (editor.budget == null) "Create monthly budget" else "Edit budget") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionTagField(editor.category, state.tags, viewModel::changeCategory, editor.budget == null && !state.busy, null)
                    OutlinedTextField(editor.month, viewModel::changeMonth, label = { Text("Month (YYYY-MM)") }, singleLine = true, enabled = editor.budget == null && !state.busy)
                    OutlinedTextField(editor.amount, viewModel::changeAmount, label = { Text("Limit (${editor.currency})") }, singleLine = true, enabled = !state.busy)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::save, enabled = !state.busy) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::closeEditor, enabled = !state.busy) { Text("Cancel") } })
    }
}

@Composable
private fun BudgetCard(status: BudgetStatus, selected: Boolean, live: Boolean, onEdit: () -> Unit,
    onDisable: () -> Unit, onRetry: () -> Unit, enabled: Boolean) {
    val budget = status.budget
    val warning = live && status.threshold != BudgetThreshold.NORMAL
    val progress = BigDecimal(status.spent).divide(BigDecimal.valueOf(budget.limitMinor), 4, RoundingMode.DOWN).toFloat().coerceIn(0f, 1f)
    Card(colors = CardDefaults.cardColors(containerColor = if (warning || selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(budgetCategoryMonthText(budget.category, budget.month), style = MaterialTheme.typography.titleMedium)
            Text(budgetSpendingText(budgetMoney(status.spent, budget.currency), budgetMoney(budget.limitMinor.toBigInteger(), budget.currency)))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text(budgetStatusText(status.threshold, status.uncounted > 0))
            Text(budgetMoney(status.remaining.abs(), budget.currency).let { amount ->
                if (status.remaining.signum() < 0) budgetExceededByText(amount) else budgetRemainingText(amount) })
            if (warning) Text(budgetFeedbackText(status.threshold, budget.category))
            if (status.uncounted > 0) {
                Text(budgetIncompleteText(status.uncounted))
                TextButton(onClick = onRetry, enabled = enabled) { Text("Resolve uncounted currencies") }
            }
            if (budget.pendingSync) Text("Saved on this device · Waiting to sync", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onEdit, enabled = enabled) { Text("Edit budget") }
                TextButton(onClick = onDisable, enabled = enabled) { Text("Disable") }
            }
        }
    }
}

@Composable
fun BudgetDashboardSummary(onOpen: (String?) -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column {
        Button(onClick = { onOpen(null) }) { Text("Budgets") }
        state.overview.statuses.filter { it.budget.active && it.budget.month == state.currentMonth &&
            (it.threshold.level > 0 || it.uncounted > 0) }.take(3).forEach { status ->
            TextButton(onClick = { onOpen(status.budget.id) }) {
                Column {
                    Text(budgetCategoryMonthText(status.budget.category, status.budget.month))
                    Text(budgetStatusText(status.threshold, status.uncounted > 0))
                    Text(budgetSpendingText(budgetMoney(status.spent, status.budget.currency), budgetMoney(status.budget.limitMinor.toBigInteger(), status.budget.currency)))
                    if (status.remaining.signum() < 0) Text(budgetExceededByText(budgetMoney(-status.remaining, status.budget.currency)))
                    if (status.threshold.level > 0) Text(budgetFeedbackText(status.threshold, status.budget.category))
                    if (status.uncounted > 0) Text(budgetIncompleteText(status.uncounted))
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
    Text("Optional local notifications tell you when a current monthly budget crosses a threshold. Budget warnings remain available here if notifications are off.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = {
        val activity = context as? Activity
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        } else if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) rationale = true
        else if (asked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        else request()
    }) { Text("Enable or manage budget notifications") }
    if (rationale) AlertDialog(onDismissRequest = { rationale = false },
        title = { Text("Budget update") }, text = { Text("Optional local notifications tell you when a current monthly budget crosses a threshold. Budget warnings remain available here if notifications are off.") },
        confirmButton = { TextButton(onClick = { rationale = false; request() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text("Cancel") } })
}
