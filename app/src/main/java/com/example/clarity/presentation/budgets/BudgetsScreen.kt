package com.example.clarity.presentation.budgets

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.model.BudgetPeriod
import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.repository.BudgetOverview
import com.example.clarity.presentation.theme.ClarityTheme

private fun isLivePeriod(status: BudgetStatus, state: BudgetsState): Boolean = when (status.budget.period) {
    BudgetPeriod.MONTHLY -> status.activePeriod == state.currentMonth
    BudgetPeriod.YEARLY -> status.activePeriod == state.currentYear
}

@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    selectedId: String? = null,
    onOpenEditor: (String?) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BudgetsContent(
        state = state, selectedId = selectedId, onBack = onBack,
        onCreate = {
            viewModel.edit()
            onOpenEditor(null)
        },
        onEdit = { budget ->
            viewModel.edit(budget)
            onOpenEditor(budget.id)
        },
        onDisable = viewModel::disable, onRetry = viewModel::retryCurrency,
        notificationPermission = { BudgetNotificationPermission() },
        bottomBar = bottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsContent(
    state: BudgetsState, selectedId: String?, onBack: () -> Unit, onCreate: () -> Unit,
    onEdit: (Budget) -> Unit, onDisable: (String) -> Unit, onRetry: (String) -> Unit,
    notificationPermission: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    val listState = rememberLazyListState()
    // Highlighting/navigation only selects a budget already loaded from the authenticated owner's stream.
    val rows = state.overview.statuses.filter { it.budget.active }
    LaunchedEffect(selectedId, rows.map { it.budget.id }) {
        val index = rows.indexOfFirst { it.budget.id == selectedId }
        // Exactly one header item precedes the budget rows.
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(BudgetUiText.TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = BudgetUiText.BACK) }
                }
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (state.overview.ownerId != null) {
                FloatingActionButton(
                    onClick = { if (!state.busy) onCreate() },
                    modifier = Modifier.semantics { contentDescription = BudgetUiText.CREATE }
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { BudgetsHeader(state, rows.isEmpty(), notificationPermission) }
            items(rows, key = { it.budget.id }) { status ->
                BudgetCard(status, selected = status.budget.id == selectedId,
                    live = isLivePeriod(status, state),
                    onEdit = { onEdit(status.budget) }, onDisable = { onDisable(status.budget.id) },
                    onRetry = { onRetry(status.budget.id) }, enabled = !state.busy)
            }
            val disabled = state.overview.statuses.filter { !it.budget.active }
            if (disabled.isNotEmpty()) item(key = "disabled-section") { BudgetSectionLabel(BudgetUiText.DISABLED_SECTION) }
            items(disabled, key = { "disabled-${it.budget.id}" }) { status ->
                DisabledBudgetRow(status, onClick = { onEdit(status.budget) }, enabled = !state.busy)
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun BudgetsHeader(state: BudgetsState, empty: Boolean, notificationPermission: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
fun BudgetDashboardSummary(onOpen: (String?) -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BudgetDashboardCard(state, onOpen)
}

// Rendered inside the dashboard's vertically scrolling Column: no lazy lists or nested scrolling.
@Composable
private fun BudgetDashboardCard(state: BudgetsState, onOpen: (String?) -> Unit) {
    val alerts = state.overview.statuses.filter { it.budget.active && isLivePeriod(it, state) &&
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

private fun areAppNotificationsEnabled(context: Context): Boolean {
    val permissionGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

@Composable
private fun BudgetNotificationPermission() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsEnabled by remember { mutableStateOf(areAppNotificationsEnabled(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = areAppNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        notificationsEnabled = areAppNotificationsEnabled(context)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (notificationsEnabled) return

    var rationale by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("budget_permission", 0) }
    var asked by remember { mutableStateOf(prefs.getBoolean("asked", false)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        notificationsEnabled = areAppNotificationsEnabled(context)
    }
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
        BudgetsContent(state, selectedId, onBack = {}, onCreate = {}, onEdit = {}, onDisable = {}, onRetry = {})
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
