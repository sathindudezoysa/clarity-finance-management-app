package com.example.clarity.presentation.transactions.list

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.R
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.TransactionCalendar
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.TransactionMessages
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TransactionHistoryFilter(val label: String) {
    ALL("All"),
    EXPENSES("Expenses"),
    INCOME("Income"),
    RECEIPTS("Receipt Attached")
}

@Composable
fun TransactionListScreen(
    onBack: () -> Unit = {},
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    topBar: @Composable () -> Unit = {
        TransactionHistoryTopBar(
            onBack = onBack,
            onProfileClick = onNavigateToProfile
        )
    },
    bottomBar: @Composable () -> Unit = {},
    viewModel: TransactionListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferredCurrency by viewModel.preferredCurrency.collectAsStateWithLifecycle()
    val receiptIds by viewModel.receiptTransactionIds.collectAsStateWithLifecycle()
    val accounts by viewModel.accountsList.collectAsStateWithLifecycle()
    val convertedAmounts by viewModel.convertedAmountsMinor.collectAsStateWithLifecycle()

    val resolvedCurrency = remember(preferredCurrency, state.transactions, accounts) {
        preferredCurrency
            ?: state.transactions.firstOrNull()?.targetCurrency
            ?: accounts.firstOrNull { it.isDefault }?.currency
            ?: accounts.firstOrNull()?.currency
            ?: state.transactions.firstOrNull()?.currency
            ?: Currencies.supported.first()
    }

    TransactionListContent(
        state = state,
        preferredCurrency = resolvedCurrency,
        receiptTransactionIds = receiptIds,
        accounts = accounts,
        convertedAmountsMinor = convertedAmounts,
        onBack = onBack,
        onAdd = onAdd,
        onOpen = onOpen,
        topBar = topBar,
        bottomBar = bottomBar
    )
}

@Composable
private fun TransactionHistoryTopBar(
    onBack: () -> Unit,
    onProfileClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface.copy(alpha = 0.92f),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go back",
                        tint = colors.onSurfaceVariant
                    )
                }
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = colors.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Profile",
                        tint = colors.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionListContent(
    state: TransactionListState,
    preferredCurrency: String = Currencies.supported.first(),
    receiptTransactionIds: Set<String> = emptySet(),
    accounts: List<Account> = emptyList(),
    convertedAmountsMinor: Map<String, BigInteger> = emptyMap(),
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    topBar: @Composable () -> Unit = {
        TransactionHistoryTopBar(onBack = onBack, onProfileClick = {})
    },
    bottomBar: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val currentNowMonth = remember { YearMonth.now() }
    val initialMonth = remember(state.transactions) {
        state.transactions.maxByOrNull { it.dateEpochMillis }?.let {
            YearMonth.from(TransactionCalendar.dateAt(it.dateEpochMillis))
        } ?: currentNowMonth
    }

    var selectedMonthStr by rememberSaveable { mutableStateOf(initialMonth.toString()) }
    var userChangedMonth by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialMonth) {
        if (!userChangedMonth && state.transactions.isNotEmpty()) {
            selectedMonthStr = initialMonth.toString()
        }
    }

    val selectedMonth = remember(selectedMonthStr) {
        runCatching { YearMonth.parse(selectedMonthStr) }.getOrDefault(currentNowMonth)
    }

    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFilterName by rememberSaveable { mutableStateOf(TransactionHistoryFilter.ALL.name) }
    val selectedFilter = remember(selectedFilterName) {
        runCatching { TransactionHistoryFilter.valueOf(selectedFilterName) }.getOrDefault(TransactionHistoryFilter.ALL)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    fun resolveAmountMinor(tx: Transaction): BigInteger {
        convertedAmountsMinor[tx.id]?.let { return it }
        if (tx.currency.equals(preferredCurrency, ignoreCase = true)) {
            return BigInteger.valueOf(tx.amountMinor)
        }
        val snapshot = tx.conversion
        if (tx.targetCurrency.equals(preferredCurrency, ignoreCase = true) && snapshot != null) {
            return BigInteger.valueOf(snapshot.amountMinor)
        }
        return BigInteger.valueOf(tx.amountMinor)
    }

    val accountScopedTransactions = remember(state.transactions, selectedAccountId) {
        if (selectedAccountId == null) state.transactions
        else state.transactions.filter { it.accountId == selectedAccountId }
    }

    val monthTransactions = remember(accountScopedTransactions, selectedMonth) {
        accountScopedTransactions.filter { tx ->
            YearMonth.from(TransactionCalendar.dateAt(tx.dateEpochMillis)) == selectedMonth
        }
    }

    val previousMonth = remember(selectedMonth) { selectedMonth.minusMonths(1) }
    val prevMonthTransactions = remember(accountScopedTransactions, previousMonth) {
        accountScopedTransactions.filter { tx ->
            YearMonth.from(TransactionCalendar.dateAt(tx.dateEpochMillis)) == previousMonth
        }
    }

    val totalSpentMinor = remember(monthTransactions, convertedAmountsMinor, preferredCurrency) {
        monthTransactions
            .filter { it.type == TransactionType.EXPENSE && it.category != "Cash Top-Up" }
            .fold(BigInteger.ZERO) { sum, tx -> sum + resolveAmountMinor(tx) }
    }

    val prevSpentMinor = remember(prevMonthTransactions, convertedAmountsMinor, preferredCurrency) {
        prevMonthTransactions
            .filter { it.type == TransactionType.EXPENSE && it.category != "Cash Top-Up" }
            .fold(BigInteger.ZERO) { sum, tx -> sum + resolveAmountMinor(tx) }
    }

    val totalIncomeMinor = remember(monthTransactions, convertedAmountsMinor, preferredCurrency) {
        monthTransactions
            .filter { it.type == TransactionType.INCOME && it.category != "Cash Top-Up" }
            .fold(BigInteger.ZERO) { sum, tx -> sum + resolveAmountMinor(tx) }
    }

    val filteredTransactions = remember(
        monthTransactions,
        selectedFilter,
        searchQuery,
        receiptTransactionIds,
        state.accountNames
    ) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        monthTransactions.filter { tx ->
            val matchesFilter = when (selectedFilter) {
                TransactionHistoryFilter.ALL -> true
                TransactionHistoryFilter.EXPENSES -> tx.type == TransactionType.EXPENSE
                TransactionHistoryFilter.INCOME -> tx.type == TransactionType.INCOME
                TransactionHistoryFilter.RECEIPTS -> tx.id in receiptTransactionIds
            }
            val accountName = if (tx.isCash) "Cash Wallet" else state.accountNames[tx.accountId].orEmpty()
            val matchesSearch = query.isEmpty() ||
                tx.category.lowercase(Locale.ROOT).contains(query) ||
                tx.note.lowercase(Locale.ROOT).contains(query) ||
                accountName.lowercase(Locale.ROOT).contains(query)
            matchesFilter && matchesSearch
        }
    }

    val groupedByDate = remember(filteredTransactions) {
        filteredTransactions
            .sortedWith(compareByDescending<Transaction> { it.dateEpochMillis }.thenByDescending { it.createdAt })
            .groupBy { TransactionCalendar.dateAt(it.dateEpochMillis) }
            .toSortedMap(compareByDescending { it })
    }

    val selectedAccountLabel = remember(selectedAccountId, accounts, state.accountNames) {
        when {
            selectedAccountId == null -> "All Accounts"
            selectedAccountId?.startsWith(Account.CASH_WALLET_PREFIX) == true -> "Cash Wallet"
            else -> accounts.firstOrNull { it.id == selectedAccountId }?.name
                ?: state.accountNames[selectedAccountId]
                ?: "Account"
        }
    }

    val spentComparisonLabel = remember(totalSpentMinor, prevSpentMinor, previousMonth) {
        val prevMonthShort = previousMonth.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
        if (prevSpentMinor > BigInteger.ZERO) {
            val diff = totalSpentMinor - prevSpentMinor
            val pct = BigDecimal(diff.abs())
                .multiply(BigDecimal(100))
                .divide(BigDecimal(prevSpentMinor), 0, RoundingMode.HALF_UP)
                .toInt()
            if (diff <= BigInteger.ZERO) {
                "$pct% less than $prevMonthShort"
            } else {
                "$pct% more than $prevMonthShort"
            }
        } else {
            val count = monthTransactions.count { it.type == TransactionType.EXPENSE }
            "$count expenses"
        }
    }

    val incomeSubtitleLabel = remember(totalIncomeMinor, totalSpentMinor) {
        if (totalIncomeMinor > BigInteger.ZERO && totalIncomeMinor >= totalSpentMinor) {
            "On monthly target"
        } else if (totalIncomeMinor > BigInteger.ZERO) {
            "Monthly income"
        } else {
            "No income recorded"
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = topBar,
        bottomBar = bottomBar
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 92.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Month & Quick Account Selector Bar
                item(key = "period-account-bar") {
                    PeriodAndAccountBar(
                        selectedMonth = selectedMonth,
                        selectedAccountLabel = selectedAccountLabel,
                        accounts = accounts,
                        accountNames = state.accountNames,
                        onPreviousMonth = {
                            userChangedMonth = true
                            selectedMonthStr = selectedMonth.minusMonths(1).toString()
                        },
                        onNextMonth = {
                            userChangedMonth = true
                            selectedMonthStr = selectedMonth.plusMonths(1).toString()
                        },
                        onAccountSelected = { selectedAccountId = it }
                    )
                }

                // 2. High-Level Financial Metric Summary Card
                item(key = "summary-card") {
                    SummaryMetricsCard(
                        currency = preferredCurrency,
                        totalSpentMinor = totalSpentMinor,
                        totalIncomeMinor = totalIncomeMinor,
                        spentSubtitle = spentComparisonLabel,
                        incomeSubtitle = incomeSubtitleLabel
                    )
                }

                // 3. Filter Pills Bar
                item(key = "filter-chips") {
                    FilterChipsRow(
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilterName = it.name }
                    )
                }

                // 4. Search Bar
                item(key = "search-bar") {
                    SearchInputBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" }
                    )
                }

                // Error Banner (preserved for tests & offline sync diagnostic)
                state.error?.let { errorMsg ->
                    item(key = "error-banner") {
                        Surface(
                            color = colors.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMsg,
                                color = colors.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                // 5. Loading / Empty / Grouped Transactions
                if (state.isLoading) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colors.primary)
                        }
                    }
                } else if (groupedByDate.isEmpty()) {
                    item(key = "empty-state") {
                        EmptyTransactionsCard(
                            hasActiveFilters = selectedFilter != TransactionHistoryFilter.ALL ||
                                searchQuery.isNotBlank() ||
                                selectedAccountId != null,
                            onResetFilters = {
                                selectedFilterName = TransactionHistoryFilter.ALL.name
                                searchQuery = ""
                                selectedAccountId = null
                            }
                        )
                    }
                } else {
                    items(
                        items = groupedByDate.entries.toList(),
                        key = { it.key.toEpochDay() }
                    ) { (date, dayTransactions) ->
                        val dayNetMinor = dayTransactions.fold(BigInteger.ZERO) { acc, tx ->
                            val amt = resolveAmountMinor(tx)
                            if (tx.type == TransactionType.INCOME) acc + amt else acc - amt
                        }
                        DateTransactionGroup(
                            date = date,
                            transactions = dayTransactions,
                            dayNetMinor = dayNetMinor,
                            preferredCurrency = preferredCurrency,
                            accountNames = state.accountNames,
                            receiptTransactionIds = receiptTransactionIds,
                            onOpenTransaction = onOpen
                        )
                    }
                }
            }

            // 6. Floating Bottom Action Buttons (Export + Add)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            exportTransactionsCsv(
                                context = context,
                                month = selectedMonth,
                                transactions = filteredTransactions,
                                accountNames = state.accountNames
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export",
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Export",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = colors.onSurface
                            )
                        )
                    }
                }

                val addDescription = "Add transaction"
                FloatingActionButton(
                    onClick = onAdd,
                    modifier = Modifier.semantics { contentDescription = addDescription }
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable
private fun PeriodAndAccountBar(
    selectedMonth: YearMonth,
    selectedAccountLabel: String,
    accounts: List<Account>,
    accountNames: Map<String, String>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAccountSelected: (String?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPreviousMonth)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = selectedMonth.format(monthFormatter),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.onSurface
                        )
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = colors.surfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNextMonth)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Box {
            Surface(
                shape = CircleShape,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .clickable { accountMenuExpanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                    )
                    Text(
                        text = selectedAccountLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select account",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = accountMenuExpanded,
                onDismissRequest = { accountMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("All Accounts") },
                    onClick = {
                        accountMenuExpanded = false
                        onAccountSelected(null)
                    }
                )
                val dropdownAccounts = if (accounts.isNotEmpty()) {
                    accounts.map { it.id to if (it.isCashWallet) "Cash Wallet" else it.name }
                } else {
                    accountNames.entries.map { it.key to it.value }
                }
                dropdownAccounts.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            accountMenuExpanded = false
                            onAccountSelected(id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricsCard(
    currency: String,
    totalSpentMinor: BigInteger,
    totalIncomeMinor: BigInteger,
    spentSubtitle: String,
    incomeSubtitle: String
) {
    val colors = MaterialTheme.colorScheme
    val symbol = currencySymbol(currency)
    val (spentMajor, spentFraction) = splitFormattedAmount(totalSpentMinor, currency)
    val (incomeMajor, incomeFraction) = splitFormattedAmount(totalIncomeMinor, currency)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle decorative wave chart in bottom right
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 4.dp)
                    .width(120.dp)
                    .height(48.dp)
            ) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(0f, h * 0.83f)
                    cubicTo(w * 0.16f, h * 0.79f, w * 0.29f, h * 0.31f, w * 0.45f, h * 0.5f)
                    cubicTo(w * 0.62f, h * 0.68f, w * 0.75f, h * 0.16f, w, h * 0.25f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path = path, color = colors.primary.copy(alpha = 0.08f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total Spent Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.onSurfaceVariant)
                        )
                        Text(
                            text = "TOTAL SPENT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                letterSpacing = 0.6.sp,
                                color = colors.onSurfaceVariant
                            )
                        )
                    }

                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = colors.onSurface
                                )
                            ) {
                                append("$symbol$spentMajor")
                            }
                            if (spentFraction.isNotEmpty()) {
                                withStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                ) {
                                    append(spentFraction)
                                }
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = HistoryIcons.TrendingDown,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = spentSubtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(colors.outlineVariant.copy(alpha = 0.5f))
                )

                // Total Income Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.tertiary)
                        )
                        Text(
                            text = "TOTAL INCOME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                letterSpacing = 0.6.sp,
                                color = colors.onSurfaceVariant
                            )
                        )
                    }

                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = colors.tertiary
                                )
                            ) {
                                append("$symbol$incomeMajor")
                            }
                            if (incomeFraction.isNotEmpty()) {
                                withStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = colors.tertiary.copy(alpha = 0.8f)
                                    )
                                ) {
                                    append(incomeFraction)
                                }
                            }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = incomeSubtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = colors.tertiary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: TransactionHistoryFilter,
    onFilterSelected: (TransactionHistoryFilter) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransactionHistoryFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                shape = CircleShape,
                color = if (isSelected) colors.secondaryContainer else colors.surface,
                border = if (isSelected) null else BorderStroke(1.dp, colors.outlineVariant),
                shadowElevation = if (isSelected) 0.dp else 1.dp,
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .clickable { onFilterSelected(filter) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    } else if (filter == TransactionHistoryFilter.RECEIPTS) {
                        Icon(
                            painter = painterResource(R.drawable.ic_receipt_verified),
                            contentDescription = null,
                            tint = colors.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isSelected) colors.onSecondaryContainer else colors.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search merchant, category, or note...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = colors.onSurface
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DateTransactionGroup(
    date: LocalDate,
    transactions: List<Transaction>,
    dayNetMinor: BigInteger,
    preferredCurrency: String,
    accountNames: Map<String, String>,
    receiptTransactionIds: Set<String>,
    onOpenTransaction: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isPositiveDay = dayNetMinor > BigInteger.ZERO
    val netFormatted = formatSignedCurrency(
        amountMinor = dayNetMinor.abs(),
        currency = preferredCurrency,
        isPositive = isPositiveDay,
        isZero = dayNetMinor == BigInteger.ZERO
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatGroupDateHeader(date),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.onSurface
                )
            )
            Text(
                text = netFormatted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (isPositiveDay) colors.tertiary else colors.onSurfaceVariant
                )
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                transactions.forEachIndexed { index, tx ->
                    if (index > 0) {
                        Divider(
                            color = colors.outlineVariant.copy(alpha = 0.5f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    TransactionHistoryRow(
                        transaction = tx,
                        accountNames = accountNames,
                        hasReceipt = tx.id in receiptTransactionIds,
                        onClick = { onOpenTransaction(tx.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionHistoryRow(
    transaction: Transaction,
    accountNames: Map<String, String>,
    hasReceipt: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isIncome = transaction.type == TransactionType.INCOME
    val primaryTitle = transaction.category
    val accountLabel = when {
        transaction.isCash -> "Cash Wallet"
        transaction.accountId == null -> TransactionMessages.ACCOUNT_UNASSIGNED
        else -> accountNames[transaction.accountId] ?: TransactionMessages.ACCOUNT_UNAVAILABLE
    }
    val subtitle = accountLabel
    val formattedAmount = formatSignedCurrency(
        amountMinor = BigInteger.valueOf(transaction.amountMinor),
        currency = transaction.currency,
        isPositive = isIncome,
        isZero = false
    )
    val timeLabel = formatTransactionTime(transaction.createdAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isIncome) colors.tertiary.copy(alpha = 0.16f) else colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIconFor(transaction.category, isIncome),
                    contentDescription = null,
                    tint = if (isIncome) colors.tertiary else colors.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = primaryTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = colors.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (hasReceipt) {
                        Icon(
                            painter = painterResource(R.drawable.ic_receipt_verified),
                            contentDescription = "Receipt Attached",
                            tint = colors.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isIncome) colors.tertiary else colors.onSurface
                ),
                textAlign = TextAlign.End
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant
                ),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun EmptyTransactionsCard(
    hasActiveFilters: Boolean,
    onResetFilters: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = HistoryIcons.ReceiptLong,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(30.dp)
            )
        }

        Text(
            text = "No Transactions Found",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = colors.onSurface
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = if (hasActiveFilters) {
                "No transactions match your active filter criteria."
            } else {
                "No transactions recorded for this period."
            },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                color = colors.onSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )

        if (hasActiveFilters) {
            Button(
                onClick = onResetFilters,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "Reset All Filters",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimary
                    )
                )
            }
        }
    }
}

private fun exportTransactionsCsv(
    context: Context,
    month: YearMonth,
    transactions: List<Transaction>,
    accountNames: Map<String, String>
) {
    val header = "Date,Type,Category,Account,Currency,Amount,Note"
    val rows = transactions.joinToString("\n") { tx ->
        val date = TransactionCalendar.dateAt(tx.dateEpochMillis).toString()
        val type = tx.type.name
        val category = tx.category.replace("\"", "\"\"")
        val account = (if (tx.isCash) "Cash Wallet" else accountNames[tx.accountId] ?: "").replace("\"", "\"\"")
        val amount = MoneyUtils.formatMinor(tx.amountMinor, tx.currency)
        val note = tx.note.replace("\"", "\"\"")
        "\"$date\",\"$type\",\"$category\",\"$account\",\"${tx.currency}\",\"$amount\",\"$note\""
    }
    val csv = "$header\n$rows"
    runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Clarity Transactions - $month")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export transactions"))
    }
}

private fun formatGroupDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val shortFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
    return when (date) {
        today -> "Today, ${date.format(shortFormatter)}"
        yesterday -> "Yesterday, ${date.format(shortFormatter)}"
        else -> date.format(dayOfWeekFormatter)
    }
}

private fun formatTransactionTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun splitFormattedAmount(amountMinor: BigInteger, currency: String): Pair<String, String> {
    val digits = runCatching { Currencies.digits(currency) }.getOrDefault(2)
    val value = BigDecimal(amountMinor, digits)
    val integerPart = value.toBigInteger()
    val symbols = DecimalFormatSymbols(Locale.US)
    val groupedMajor = DecimalFormat("#,##0", symbols).format(integerPart)
    if (digits <= 0) return groupedMajor to ""
    val plain = MoneyUtils.formatMinor(amountMinor, currency)
    val dotIndex = plain.indexOf('.')
    val fraction = if (dotIndex >= 0) plain.substring(dotIndex) else "." + "0".repeat(digits)
    return groupedMajor to fraction
}

private fun formatSignedCurrency(
    amountMinor: BigInteger,
    currency: String,
    isPositive: Boolean,
    isZero: Boolean
): String {
    val symbol = currencySymbol(currency)
    val (major, fraction) = splitFormattedAmount(amountMinor.abs(), currency)
    val sign = when {
        isZero -> ""
        isPositive -> "+"
        else -> "-"
    }
    return "$sign$symbol$major$fraction"
}

private fun currencySymbol(code: String): String = when (code.uppercase(Locale.ROOT)) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "LKR" -> "Rs "
    "INR" -> "₹"
    "JPY" -> "¥"
    "AUD" -> "A$"
    "CAD" -> "C$"
    "SGD" -> "S$"
    "KWD" -> "KD "
    else -> "$code "
}

internal fun categoryIconFor(category: String, isIncome: Boolean): ImageVector {
    if (isIncome) return AppIcons.Wallet
    val key = category.lowercase(Locale.ROOT)
    return when {
        key.contains("food") || key.contains("dining") || key.contains("cafe") || key.contains("coffee") -> HistoryIcons.Cafe
        key.contains("grocer") || key.contains("shop") || key.contains("market") -> HistoryIcons.ShoppingCart
        key.contains("travel") || key.contains("transit") || key.contains("transport") || key.contains("car") || key.contains("uber") || key.contains("fuel") -> HistoryIcons.DirectionsCar
        key.contains("bill") || key.contains("sub") || key.contains("util") || key.contains("entertain") -> HistoryIcons.Subscriptions
        else -> HistoryIcons.ReceiptLong
    }
}

private object HistoryIcons {
    val Cafe: ImageVector by lazy {
        ImageVector.Builder("Cafe", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 3f)
            horizontalLineTo(4f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 2.21f, 1.79f, 4f, 4f, 4f)
            horizontalLineToRelative(6f)
            curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(2f)
            curveToRelative(1.11f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f)
            close()
            moveTo(20f, 8f)
            horizontalLineToRelative(-2f)
            verticalLineTo(5f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(3f)
            close()
            moveTo(4f, 19f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
        }.build()
    }

    val ShoppingCart: ImageVector by lazy {
        ImageVector.Builder("ShoppingCart", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 18f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            reflectiveCurveTo(5.9f, 22f, 7f, 22f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
            moveTo(1f, 2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            lineToRelative(3.6f, 7.59f)
            lineToRelative(-1.35f, 2.45f)
            curveToRelative(-0.16f, 0.28f, -0.25f, 0.61f, -0.25f, 0.96f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(-2f)
            horizontalLineTo(7.42f)
            curveToRelative(-0.14f, 0f, -0.25f, -0.11f, -0.25f, -0.25f)
            lineToRelative(0.03f, -0.12f)
            lineToRelative(0.9f, -1.63f)
            horizontalLineToRelative(7.45f)
            curveToRelative(0.75f, 0f, 1.41f, -0.41f, 1.75f, -1.03f)
            lineToRelative(3.58f, -6.49f)
            curveToRelative(0.08f, -0.14f, 0.12f, -0.31f, 0.12f, -0.48f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            horizontalLineTo(5.21f)
            lineToRelative(-0.94f, -2f)
            horizontalLineTo(1f)
            close()
            moveTo(17f, 18f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            reflectiveCurveToRelative(0.89f, 2f, 1.99f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
        }.build()
    }

    val DirectionsCar: ImageVector by lazy {
        ImageVector.Builder("DirectionsCar", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
            moveTo(18.92f, 6.01f)
            curveTo(18.72f, 5.42f, 18.16f, 5f, 17.5f, 5f)
            horizontalLineToRelative(-11f)
            curveToRelative(-0.66f, 0f, -1.21f, 0.42f, -1.42f, 1.01f)
            lineTo(3f, 12f)
            verticalLineToRelative(8f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(1f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-1f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(1f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(1f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-8f)
            lineToRelative(-2.08f, -5.99f)
            close()
            moveTo(6.5f, 16f)
            curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
            reflectiveCurveTo(5.67f, 13f, 6.5f, 13f)
            reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
            reflectiveCurveTo(7.33f, 16f, 6.5f, 16f)
            close()
            moveTo(17.5f, 16f)
            curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
            reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
            reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
            reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
            close()
            moveTo(5f, 11f)
            lineToRelative(1.5f, -4.5f)
            horizontalLineToRelative(11f)
            lineTo(19f, 11f)
            horizontalLineTo(5f)
            close()
        }.build()
    }

    val Subscriptions: ImageVector by lazy {
        ImageVector.Builder("Subscriptions", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 8f)
            horizontalLineTo(4f)
            verticalLineTo(6f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(2f)
            close()
            moveTo(18f, 2f)
            horizontalLineTo(6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(12f)
            verticalLineTo(2f)
            close()
            moveTo(22f, 12f)
            verticalLineToRelative(8f)
            curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
            verticalLineToRelative(-8f)
            curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
            close()
            moveTo(16f, 16f)
            lineToRelative(-6f, -3.27f)
            verticalLineToRelative(6.53f)
            lineTo(16f, 16f)
            close()
        }.build()
    }

    val ReceiptLong: ImageVector by lazy {
        ImageVector.Builder("ReceiptLong", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
            moveTo(19.5f, 3.5f)
            lineTo(18f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(15f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(12f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(9f, 2f)
            lineTo(7.5f, 3.5f)
            lineTo(6f, 2f)
            verticalLineToRelative(14f)
            horizontalLineTo(3f)
            verticalLineToRelative(3f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            horizontalLineToRelative(12f)
            curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
            verticalLineTo(2f)
            lineToRelative(-1.5f, 1.5f)
            close()
            moveTo(15f, 20f)
            horizontalLineTo(6f)
            curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
            verticalLineToRelative(-1f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(2f)
            close()
            moveTo(19f, 19f)
            curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
            reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
            verticalLineToRelative(-3f)
            horizontalLineTo(8f)
            verticalLineTo(5f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(14f)
            close()
        }.build()
    }

    val TrendingDown: ImageVector by lazy {
        ImageVector.Builder("TrendingDown", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 18f)
            lineToRelative(2.29f, -2.29f)
            lineToRelative(-4.88f, -4.88f)
            lineToRelative(-4f, 4f)
            lineTo(2f, 7.41f)
            lineTo(3.41f, 6f)
            lineToRelative(6f, 6f)
            lineToRelative(4f, -4f)
            lineToRelative(6.3f, 6.29f)
            lineTo(22f, 12f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(-6f)
            close()
        }.build()
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionListPreview() {
    ClarityTheme {
        TransactionListContent(
            state = TransactionListState(
                isLoading = false,
                transactions = listOf(
                    Transaction(
                        id = "1",
                        type = TransactionType.EXPENSE,
                        amountMinor = 525,
                        category = "Dining",
                        note = "Blue Bottle Coffee",
                        currency = "USD",
                        targetCurrency = "USD"
                    ),
                    Transaction(
                        id = "2",
                        type = TransactionType.INCOME,
                        amountMinor = 310000,
                        category = "Income",
                        note = "TechCorp Payroll",
                        currency = "USD",
                        targetCurrency = "USD"
                    )
                ),
                accountNames = mapOf("1" to "Chase Premier")
            ),
            preferredCurrency = "USD",
            receiptTransactionIds = setOf("1", "2"),
            onBack = {},
            onAdd = {},
            onOpen = {}
        )
    }
}
