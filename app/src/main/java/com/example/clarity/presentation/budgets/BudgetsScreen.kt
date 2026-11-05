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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.clarity.presentation.navigation.ClarityTopBar
import com.example.clarity.presentation.theme.ClarityTheme

private fun isLivePeriod(status: BudgetStatus, state: BudgetsState): Boolean = when (status.budget.period) {
    BudgetPeriod.MONTHLY -> status.activePeriod == state.currentMonth
    BudgetPeriod.YEARLY -> status.activePeriod == state.currentYear
}

@Composable
fun BudgetsScreen(
    onBack: () -> Unit = {},
    selectedId: String? = null,
    onOpenEditor: (String?) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    topBar: @Composable () -> Unit = {
        ClarityTopBar(
            title = BudgetUiText.TITLE,
            onProfileClick = onNavigateToProfile
        )
    },
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
        onDelete = viewModel::delete, onRetry = viewModel::retryCurrency,
        notificationPermission = { BudgetNotificationPermission() },
        topBar = topBar,
        bottomBar = bottomBar
    )
}

@Composable
fun BudgetsContent(
    state: BudgetsState, selectedId: String?, onBack: () -> Unit = {}, onCreate: () -> Unit,
    onEdit: (Budget) -> Unit, onDelete: (String) -> Unit, onRetry: (String) -> Unit,
    notificationPermission: @Composable () -> Unit = {},
    topBar: @Composable () -> Unit = {
        ClarityTopBar(
            title = BudgetUiText.TITLE,
            onProfileClick = {}
        )
    },
    bottomBar: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var pendingDeleteBudget by remember { mutableStateOf<Budget?>(null) }
    // Highlighting/navigation only selects a budget already loaded from the authenticated owner's stream.
    val rows = state.overview.statuses.filter { it.budget.active }
    LaunchedEffect(selectedId, rows.map { it.budget.id }) {
        val index = rows.indexOfFirst { it.budget.id == selectedId }
        // Exactly one header item precedes the budget rows.
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    pendingDeleteBudget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBudget = null },
            title = { Text(BudgetUiText.DELETE_CONFIRM_TITLE) },
            text = { Text(budgetDeleteConfirmMessage(target.category)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleteId = target.id
                        pendingDeleteBudget = null
                        onDelete(deleteId)
                    }
                ) {
                    Text(
                        text = BudgetUiText.DELETE,
                        color = colors.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBudget = null }) {
                    Text(BudgetUiText.CANCEL)
                }
            }
        )
    }

    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = colors.background,
        floatingActionButton = {
            if (state.overview.ownerId != null) {
                FloatingActionButton(
                    onClick = { if (!state.busy) onCreate() },
                    modifier = Modifier.semantics { contentDescription = BudgetUiText.CREATE }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 14.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    BudgetsHeader(
                        state = state,
                        empty = rows.isEmpty(),
                        onCreate = if (state.overview.ownerId != null && !state.busy) onCreate else null,
                        notificationPermission = notificationPermission
                    )
                }
                items(rows, key = { it.budget.id }) { status ->
                    BudgetCard(
                        status = status,
                        selected = status.budget.id == selectedId,
                        live = isLivePeriod(status, state),
                        onEdit = { onEdit(status.budget) },
                        onDelete = { pendingDeleteBudget = status.budget },
                        onRetry = { onRetry(status.budget.id) },
                        enabled = !state.busy
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetsHeader(
    state: BudgetsState,
    empty: Boolean,
    onCreate: (() -> Unit)?,
    notificationPermission: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        notificationPermission()
        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = colors.primary,
                    modifier = Modifier.semantics { contentDescription = BudgetUiText.LOADING }
                )
            }
        }
        if (state.overview.ownerId == null && !state.loading) BudgetMessageBanner(Icons.Default.Info, BudgetUiText.SIGN_IN, isError = false)
        if (state.overview.syncError) BudgetMessageBanner(Icons.Default.Warning, BudgetUiText.SYNC_ERROR, isError = true)
        state.error?.let { BudgetMessageBanner(Icons.Default.Warning, it, isError = true) }
        if (!state.loading && empty) BudgetEmptyState(onCreate = onCreate)
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
    val colors = MaterialTheme.colorScheme
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
            TextButton(onClick = { onOpen(null) }) {
                Text(BudgetUiText.DASHBOARD_OPEN, color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            colors = CardDefaults.cardColors(
                containerColor = colors.surface,
                contentColor = colors.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            if (alerts.isEmpty()) {
                Text(
                    BudgetUiText.DASHBOARD_EMPTY,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            } else {
                Column {
                    alerts.forEachIndexed { index, status ->
                        if (index > 0) Divider(color = colors.outlineVariant.copy(alpha = 0.35f))
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
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = BudgetUiText.NOTIFICATION_TITLE,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }
            Text(
                text = BudgetUiText.NOTIFICATION_HELP,
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    val activity = context as? Activity
                    if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    } else if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) rationale = true
                    else if (asked) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    else request()
                }
            ) {
                Text(
                    text = BudgetUiText.NOTIFICATION_ACTION,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
        previewStatus("Bills", 1_400_000, 1_000_000, month = "2026-09")
    )))

@Composable
private fun BudgetsPreviewContent(state: BudgetsState, selectedId: String? = null) {
    ClarityTheme {
        BudgetsContent(state, selectedId, onBack = {}, onCreate = {}, onEdit = {}, onDelete = {}, onRetry = {})
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
