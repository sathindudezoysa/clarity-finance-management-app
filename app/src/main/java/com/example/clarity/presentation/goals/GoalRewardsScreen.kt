package com.example.clarity.presentation.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clarity.domain.util.GoalBadge
import com.example.clarity.domain.util.GoalBadgeType
import com.example.clarity.domain.util.GoalProgressPolicy
import com.example.clarity.domain.util.GoalStreakSummary
import com.example.clarity.presentation.theme.ClarityTheme

@Composable
fun GoalRewardsScreen(
    onBack: () -> Unit,
    viewModel: GoalRewardsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoalRewardsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalRewardsContent(
    state: GoalRewardsState,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Achievements & Streaks",
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
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.error?.let { errorMsg ->
                    item(key = "rewards-error") {
                        GoalErrorBanner(
                            message = errorMsg,
                            onRetry = onRetry
                        )
                    }
                }

                if (state.isLoading) {
                    item(key = "rewards-loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    item(key = "rewards-streak-card") {
                        GoalRewardsStreakCard(streak = state.streakSummary)
                    }

                    item(key = "rewards-badges-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Badges",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${state.earnedCount} / ${state.badges.size} earned",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    val rows = state.badges.chunked(2)
                    items(
                        items = rows,
                        key = { pair -> pair.joinToString("-") { it.id } }
                    ) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GoalBadgeCard(
                                badge = pair[0],
                                modifier = Modifier.weight(1f)
                            )
                            if (pair.size > 1) {
                                GoalBadgeCard(
                                    badge = pair[1],
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalRewardsStreakCard(streak: GoalStreakSummary) {
    val colors = MaterialTheme.colorScheme
    val currentLabel = if (streak.currentStreakDays == 1) "1 day" else "${streak.currentStreakDays} days"
    val longestLabel = if (streak.longestStreakDays == 1) "1 day" else "${streak.longestStreakDays} days"
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Current Streak",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = currentLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surfaceVariant,
                    contentColor = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Longest Streak",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = longestLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (streak.isAtRisk) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Streak at risk: ${GoalProgressPolicy.STREAK_AT_RISK_HINT}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else if (streak.contributedToday) {
                Text(
                    text = "You contributed today — your daily streak is active!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = GoalProgressPolicy.MOTIVATION_START,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GoalBadgeCard(
    badge: GoalBadge,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val statusLabel = if (badge.isEarned) "Earned" else "Locked"
    Card(
        modifier = modifier
            .heightIn(min = 120.dp)
            .semantics {
                contentDescription = "${badge.title}, $statusLabel. ${badge.description}"
            },
        border = BorderStroke(
            1.dp,
            if (badge.isEarned) colors.primary else colors.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (badge.isEarned) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (badge.isEarned) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (badge.isEarned) colors.primaryContainer else colors.surfaceVariant,
                    contentColor = if (badge.isEarned) colors.onPrimaryContainer else colors.onSurfaceVariant
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = badge.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalRewardsScreenPreview() {
    ClarityTheme {
        GoalRewardsContent(
            state = GoalRewardsState(
                isLoading = false,
                ownerId = "preview",
                streakSummary = GoalStreakSummary(
                    currentStreakDays = 3,
                    longestStreakDays = 7,
                    isAtRisk = true,
                    contributedToday = false
                ),
                badges = listOf(
                    GoalBadge("b1", GoalBadgeType.FIRST_STEP, "First Step", "Make your first goal contribution", isEarned = true),
                    GoalBadge("b2", GoalBadgeType.STREAK_3, "3-Day Streak", "Contribute 3 days in a row", isEarned = true),
                    GoalBadge("b3", GoalBadgeType.STREAK_7, "7-Day Streak", "Contribute 7 days in a row", isEarned = true),
                    GoalBadge("b4", GoalBadgeType.STREAK_30, "30-Day Streak", "Contribute 30 days in a row", isEarned = false)
                ),
                earnedCount = 3
            ),
            onBack = {},
            onRetry = {}
        )
    }
}

