package com.example.clarity.presentation.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.presentation.budgets.BudgetDashboardSummary
import com.example.clarity.presentation.common.AppIcons
import com.example.clarity.presentation.income.IncomeMonthlyPrompt

@Composable
fun MainScreen(
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToCashWallet: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToIncome: () -> Unit = {},
    onNavigateToBudgets: (String?) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToTransactions,
                modifier = Modifier.semantics { contentDescription = "Add transaction" }
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Home", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.surfaceVariant)
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = "Settings", tint = colors.primary)
                }
            }

            // Hero Net Worth Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.primary,
                contentColor = colors.onPrimary,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(colors.primary, lerp(colors.primary, colors.onBackground, 0.25f))
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(onClick = viewModel::togglePrivacy)
                        ) {
                            Text(
                                "TOTAL NET WORTH",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.secondary
                            )
                            Icon(
                                imageVector = if (state.isHidden) AppIcons.VisibilityOff else AppIcons.Visibility,
                                contentDescription = if (state.isHidden) "Show balances" else "Hide balances",
                                tint = colors.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (state.isHidden) "••••••••" else state.netWorth,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                state.currency,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.secondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FlowStatBox(
                                modifier = Modifier.weight(1f),
                                icon = AppIcons.ArrowDownward,
                                label = "Income",
                                amount = if (state.isHidden) "••••••" else "${state.currency} ${state.income}",
                                subtitle = state.incomeSubtitle,
                                onClick = onNavigateToIncome
                            )
                            FlowStatBox(
                                modifier = Modifier.weight(1f),
                                icon = AppIcons.ArrowUpward,
                                label = "Expenses",
                                amount = if (state.isHidden) "••••••" else "${state.currency} ${state.expenses}",
                                subtitle = state.expenseSubtitle,
                                onClick = onNavigateToTransactions
                            )
                        }
                    }
                }
            }

            IncomeMonthlyPrompt(onReview = onNavigateToIncome)

            // Cash Wallet Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onNavigateToCashWallet)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Wallet,
                                contentDescription = "Cash Wallet",
                                tint = colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "CASH WALLET",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurfaceVariant
                                )
                                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.tertiary))
                            }
                            Text(
                                text = if (state.isHidden) "••••" else "${state.currency} ${state.cashBalance}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Physical cash balance", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.surfaceVariant,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onNavigateToCashWallet)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Top Up",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary
                            )
                        }
                    }
                }
            }

            BudgetDashboardSummary(onOpen = onNavigateToBudgets)

            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun FlowStatBox(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    amount: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.onPrimary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(14.dp)
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
        }
        Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onPrimary)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.secondary.copy(alpha = 0.8f))
    }
}
