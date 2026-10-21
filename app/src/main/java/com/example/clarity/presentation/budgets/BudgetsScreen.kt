package com.example.clarity.presentation.budgets

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.model.BudgetPeriod
import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.repository.BudgetOverview
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.edit.TransactionTagField
import kotlinx.coroutines.launch

private fun isLivePeriod(budget: Budget, state: BudgetsState): Boolean = when (budget.period) {
    BudgetPeriod.MONTHLY -> budget.month == state.currentMonth
    BudgetPeriod.YEARLY -> budget.month == state.currentYear
}

@Composable
fun BudgetsScreen(onBack: () -> Unit, selectedId: String? = null, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BudgetsContent(
        state = state, selectedId = selectedId, onBack = onBack,
        onCreate = { viewModel.edit() }, onEdit = { viewModel.edit(it) },
        onDisable = viewModel::disable, onRetry = viewModel::retryCurrency,
        onCategoryChange = viewModel::changeCategory, onMonthChange = viewModel::changeMonth,
        onAmountChange = viewModel::changeAmount, onPeriodChange = viewModel::changePeriod,
        onSave = viewModel::save, onCloseEditor = viewModel::closeEditor,
        notificationPermission = { BudgetNotificationPermission() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsContent(
    state: BudgetsState, selectedId: String?, onBack: () -> Unit, onCreate: () -> Unit,
    onEdit: (Budget) -> Unit, onDisable: (String) -> Unit, onRetry: (String) -> Unit,
    onCategoryChange: (String) -> Unit, onMonthChange: (String) -> Unit, onAmountChange: (String) -> Unit,
    onSave: () -> Unit, onCloseEditor: () -> Unit, notificationPermission: @Composable () -> Unit = {},
    onPeriodChange: (BudgetPeriod) -> Unit = {}
) {
    val listState = rememberLazyListState()
    // Highlighting/navigation only selects a budget already loaded from the authenticated owner's stream.
    val rows = state.overview.statuses.filter { it.budget.active }
    LaunchedEffect(selectedId, rows.map { it.budget.id }) {
        val index = rows.indexOfFirst { it.budget.id == selectedId }
        // Exactly one header item precedes the budget rows.
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }
    Scaffold(topBar = { TopAppBar(title = { Text(BudgetUiText.TITLE) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = BudgetUiText.BACK) }
        }) }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { BudgetsHeader(state, rows.isEmpty(), onCreate, notificationPermission) }
            items(rows, key = { it.budget.id }) { status ->
                BudgetCard(status, selected = status.budget.id == selectedId,
                    live = isLivePeriod(status.budget, state),
                    onEdit = { onEdit(status.budget) }, onDisable = { onDisable(status.budget.id) },
                    onRetry = { onRetry(status.budget.id) }, enabled = !state.busy)
            }
            val disabled = state.overview.statuses.filter { !it.budget.active }
            if (disabled.isNotEmpty()) item(key = "disabled-section") { BudgetSectionLabel(BudgetUiText.DISABLED_SECTION) }
            items(disabled, key = { "disabled-${it.budget.id}" }) { status ->
                DisabledBudgetRow(status, onClick = { onEdit(status.budget) }, enabled = !state.busy)
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
    state.editor?.let { editor ->
        BudgetEditorDialog(editor, state.tags, state.busy, state.error, onCategoryChange, onMonthChange,
            onAmountChange, onPeriodChange, onSave, onCloseEditor)
    }
}

@Composable
private fun BudgetsHeader(state: BudgetsState, empty: Boolean, onCreate: () -> Unit, notificationPermission: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onCreate, enabled = state.overview.ownerId != null && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(BudgetUiText.CREATE)
        }
        Text(BudgetUiText.MONTH_POLICY, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        notificationPermission()
        if (state.loading) Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = BudgetUiText.LOADING })
        }
        if (state.overview.ownerId == null && !state.loading) BudgetMessageBanner(Icons.Default.Info, BudgetUiText.SIGN_IN, isError = false)
        if (state.overview.syncError) BudgetMessageBanner(Icons.Default.Warning, BudgetUiText.SYNC_ERROR, isError = true)
        state.error?.let { BudgetMessageBanner(Icons.Default.Warning, it, isError = true) }
        if (!state.loading && empty) BudgetEmptyState()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditorDialog(editor: BudgetEditor, tags: List<String>, busy: Boolean, error: String?,
    onCategoryChange: (String) -> Unit, onMonthChange: (String) -> Unit, onAmountChange: (String) -> Unit,
    onPeriodChange: (BudgetPeriod) -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val limitRequester = remember { BringIntoViewRequester() }
    val monthRequester = remember { BringIntoViewRequester() }
    val canEditIdentity = editor.budget == null && !busy
    AlertDialog(onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (editor.budget == null) BudgetUiText.CREATE else BudgetUiText.EDIT_TITLE) },
        text = {
            Column(Modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(editor.amount, onAmountChange,
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(limitRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { limitRequester.bringIntoView() } },
                    label = { Text(budgetLimitLabel(editor.currency)) }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done))
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
                OutlinedTextField(editor.month, onMonthChange,
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(monthRequester)
                        .onFocusEvent { if (it.isFocused) scope.launch { monthRequester.bringIntoView() } },
                    label = { Text(if (editor.period == BudgetPeriod.YEARLY) BudgetUiText.YEAR_LABEL else BudgetUiText.MONTH_LABEL) },
                    singleLine = true, enabled = canEditIdentity,
                    keyboardOptions = if (editor.period == BudgetPeriod.YEARLY) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default)
                Box(Modifier.fillMaxWidth()) {
                    TransactionTagField(editor.category, tags, onCategoryChange, canEditIdentity, null,
                        hint = "Tap a category or type a new one.")
                }
                error?.let { BudgetMessageBanner(Icons.Default.Warning, it, isError = true) }
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = !busy) { Text(BudgetUiText.SAVE) } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(BudgetUiText.CANCEL) } })
}

@Composable
fun BudgetDashboardSummary(onOpen: (String?) -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BudgetDashboardCard(state, onOpen)
}

// Rendered inside the dashboard's vertically scrolling Column: no lazy lists or nested scrolling.
@Composable
private fun BudgetDashboardCard(state: BudgetsState, onOpen: (String?) -> Unit) {
    val alerts = state.overview.statuses.filter { it.budget.active && isLivePeriod(it.budget, state) &&
        (it.threshold.level > 0 || it.uncounted > 0) }.take(3)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                BudgetUiText.TITLE,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onOpen(null) }) { Text(BudgetUiText.DASHBOARD_OPEN) }
        }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (alerts.isEmpty()) {
                Text(
                    BudgetUiText.DASHBOARD_EMPTY,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    alerts.forEachIndexed { index, status ->
                        if (index > 0) Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        BudgetDashboardAlert(status) { onOpen(status.budget.id) }
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
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(BudgetUiText.NOTIFICATION_TITLE, style = MaterialTheme.typography.titleSmall)
            }
            Text(BudgetUiText.NOTIFICATION_HELP, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(modifier = Modifier.align(Alignment.End), onClick = {
                val activity = context as? Activity
                if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                } else if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) rationale = true
                else if (asked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                else request()
            }) { Text(BudgetUiText.NOTIFICATION_ACTION) }
        }
    }
    if (rationale) AlertDialog(onDismissRequest = { rationale = false },
        title = { Text(BudgetUiText.RATIONALE_TITLE) }, text = { Text(BudgetUiText.NOTIFICATION_HELP) },
        confirmButton = { TextButton(onClick = { rationale = false; request() }) { Text(BudgetUiText.CONTINUE) } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text(BudgetUiText.CANCEL) } })
}

private fun previewStatus(category: String, spent: Long, limit: Long, month: String = "2026-10", uncounted: Int = 0,
    pendingSync: Boolean = false, active: Boolean = true) = BudgetStatus(
    Budget("${month}_$category", "preview", month, category, limit, "LKR", active = active, pendingSync = pendingSync),
    spent.toBigInteger(), uncounted)

private val previewState = BudgetsState(loading = false, currentMonth = "2026-10", currentYear = "2026", overview = BudgetOverview(
    ownerId = "preview", statuses = listOf(
        previewStatus("Food", 450_000, 1_000_000, pendingSync = true),
        previewStatus("Transport", 850_000, 1_000_000),
        previewStatus("Shopping", 1_000_000, 1_000_000, uncounted = 2),
        previewStatus("Entertainment", 1_250_000, 1_000_000),
        previewStatus("Bills", 1_400_000, 1_000_000, month = "2026-09"),
        previewStatus("Health", 0, 500_000, active = false)
    )))

@Composable
private fun BudgetsPreviewContent(state: BudgetsState, selectedId: String? = null) {
    ClarityTheme {
        BudgetsContent(state, selectedId, onBack = {}, onCreate = {}, onEdit = {}, onDisable = {}, onRetry = {},
            onCategoryChange = {}, onMonthChange = {}, onAmountChange = {}, onSave = {}, onCloseEditor = {})
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun BudgetsContentPreview() = BudgetsPreviewContent(previewState, selectedId = "2026-10_Transport")

@Preview(showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BudgetsContentDarkPreview() = BudgetsPreviewContent(previewState)

@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun BudgetsEmptyPreview() = BudgetsPreviewContent(BudgetsState(loading = false, currentMonth = "2026-10", currentYear = "2026",
    overview = BudgetOverview(ownerId = "preview", syncError = true)))

@Preview(showBackground = true)
@Composable
private fun BudgetsSignedOutPreview() = BudgetsPreviewContent(BudgetsState(loading = false, currentMonth = "2026-10", currentYear = "2026"))

@Preview(showBackground = true)
@Composable
private fun BudgetDashboardPreview() {
    ClarityTheme { BudgetDashboardCard(previewState) {} }
}
