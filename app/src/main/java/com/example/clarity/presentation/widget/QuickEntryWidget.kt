package com.example.clarity.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.SizeMode
import androidx.glance.LocalSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.actionParametersOf
import com.example.clarity.MainActivity
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.WidgetCategory
import com.example.clarity.domain.model.WidgetWalletSummary

/** 6x4 home screen widget for quickly recording cash wallet expenses and income. */
class QuickEntryWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = QuickEntryEntryPoint.from(context)
        // Signed-out users get a sign-in prompt only: nothing is loaded from the account.
        val signedIn = withTimeoutOrNull(3_000L) { entry.currentUser()().first() } != null
        val summary = if (signedIn) runCatching { entry.walletSummary()() }.getOrNull() else null

        if (!signedIn) {
            provideContent { SignedOutContent() }
            return
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val type = prefs.toQuickEntryType()
            val tick = prefs[QuickEntryKeys.refreshTick] ?: 0L
            // Reload the balance whenever the widget is refreshed (e.g. after a transaction).
            var wallet by remember { mutableStateOf(summary) }
            LaunchedEffect(tick) {
                runCatching { entry.walletSummary()() }.getOrNull()?.let { wallet = it }
            }
            // Start from the cached list for this tab so tiles appear instantly, then refresh.
            var categories by remember(type) { mutableStateOf(entry.categories().cached(type)) }
            LaunchedEffect(type, tick) {
                runCatching { entry.categories()(type) }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?.let { categories = it }
            }
            QuickEntryContent(context, type, wallet, categories)
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(QuickEntryWidget::class.java)
            ids.forEach { id ->
                updateAppWidgetState(context, id) { it[QuickEntryKeys.refreshTick] = System.currentTimeMillis() }
                QuickEntryWidget().update(context, id)
            }
        }
    }
}

private val Surface = ColorProvider(Color(0xFFFFFFFF))
private val Tile = ColorProvider(Color(0xFFF1F5F9))
private val Track = ColorProvider(Color(0xFFE8EDF3))
private val Teal = ColorProvider(Color(0xFF006B5E))
private val Red = ColorProvider(Color(0xFFBA1A1A))
private val OnSurface = ColorProvider(Color(0xFF0F172A))
private val Muted = ColorProvider(Color(0xFF64748B))
private val White = ColorProvider(Color(0xFFFFFFFF))

private val BubbleBackgrounds = listOf(
    Color(0xFFCCF5EC), Color(0xFFD7F7E6), Color(0xFFE0F0FF), Color(0xFFE3E4FF),
    Color(0xFFD0F8FF), Color(0xFFFFF0C7), Color(0xFFFFE1E0), Color(0xFFE8EDF3)
)
private val BubbleForegrounds = listOf(
    Color(0xFF005147), Color(0xFF0B6B45), Color(0xFF0B5A96), Color(0xFF3730A3),
    Color(0xFF0E7490), Color(0xFFB45309), Color(0xFFBA1A1A), Color(0xFF475569)
)

@Composable
private fun SignedOutContent() {
    Box(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(Surface)
            .cornerRadius(24.dp).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Clarity", style = TextStyle(color = Teal, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Text("Sign in to use quick entry", style = TextStyle(color = Muted, fontSize = 13.sp))
        }
    }
}

@Composable
private fun QuickEntryContent(
    context: Context,
    type: TransactionType,
    summary: WidgetWalletSummary?,
    categories: List<WidgetCategory>
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(Surface)
            .cornerRadius(24.dp).padding(10.dp)
    ) {
        Header(summary, type)
        Spacer(GlanceModifier.height(8.dp))
        CategoryGrid(context, type, categories)
    }
}

@Composable
private fun Header(summary: WidgetWalletSummary?, type: TransactionType) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.width(34.dp).height(34.dp).cornerRadius(17.dp).background(Teal),
            contentAlignment = Alignment.Center
        ) { Text("C", style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text("CASH", style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(
                text = summary?.let { "${it.currency} ${it.balance}" } ?: "—",
                style = TextStyle(color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        TypeToggle(type)
    }
}

@Composable
private fun TypeToggle(selected: TransactionType) {
    Row(
        modifier = GlanceModifier.cornerRadius(18.dp).background(Track).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleOption("Expense", TransactionType.EXPENSE, selected, Red)
        ToggleOption("Income", TransactionType.INCOME, selected, Teal)
    }
}

@Composable
private fun ToggleOption(label: String, type: TransactionType, selected: TransactionType, color: ColorProvider) {
    val active = type == selected
    Box(
        modifier = GlanceModifier.cornerRadius(15.dp)
            .background(if (active) color else Track)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionRunCallback<SelectTypeAction>(actionParametersOf(TypeParam to type.name))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(color = if (active) White else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun CategoryGrid(context: Context, type: TransactionType, categories: List<WidgetCategory>) {
    if (categories.isEmpty()) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading categories…", style = TextStyle(color = Muted, fontSize = 12.sp))
        }
        return
    }
    // Fit as many tiles as the current widget size allows.
    val size = LocalSize.current
    val columns = ((size.width.value - 20f + GAP) / (MIN_TILE_WIDTH + GAP)).toInt().coerceIn(2, 6)
    val availableHeight = size.height.value - 20f - 40f - 8f
    val rows = ((availableHeight + GAP) / (MIN_TILE_HEIGHT + GAP)).toInt().coerceIn(1, 4)
    val visible = categories.take(columns * rows)
    Column(modifier = GlanceModifier.fillMaxSize()) {
        visible.chunked(columns).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(GAP.dp))
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                row.forEachIndexed { index, category ->
                    if (index > 0) Spacer(GlanceModifier.width(GAP.dp))
                    CategoryTile(context, type, category, rowIndex * columns + index, GlanceModifier.defaultWeight())
                }
                repeat(columns - row.size) {
                    Spacer(GlanceModifier.width(GAP.dp))
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

private const val GAP = 6f
private const val MIN_TILE_WIDTH = 74f
private const val MIN_TILE_HEIGHT = 62f

@Composable
private fun CategoryTile(context: Context, type: TransactionType, category: WidgetCategory, index: Int, modifier: GlanceModifier) {
    val bubble = ColorProvider(BubbleBackgrounds[index % BubbleBackgrounds.size])
    val foreground = ColorProvider(BubbleForegrounds[index % BubbleForegrounds.size])
    Box(
        modifier = modifier.fillMaxHeight().cornerRadius(14.dp).background(Tile)
            .clickable(actionStartIntent(QuickAmountActivity.intent(context, type, category.name))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = GlanceModifier.width(30.dp).height(30.dp).cornerRadius(10.dp).background(bubble),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    category.name.take(1).uppercase(),
                    style = TextStyle(color = foreground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                )
            }
            Text(
                category.name,
                style = TextStyle(color = OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}
