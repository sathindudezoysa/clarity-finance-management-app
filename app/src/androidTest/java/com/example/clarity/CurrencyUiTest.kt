package com.example.clarity

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.CurrencyPicker
import com.example.clarity.presentation.transactions.ConversionInfo
import com.example.clarity.presentation.transactions.edit.TransactionEditContent
import com.example.clarity.presentation.transactions.edit.TransactionEditState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the repository's accessibility harness; the pinned Espresso cannot drive Android 17. */
@RunWith(AndroidJUnit4::class)
class CurrencyUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun currencySelectionSurvivesSaveableStateRestoration() {
        var registry = SaveableStateRegistry(null) { true }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                var selected by rememberSaveable { mutableStateOf("LKR") }
                ClarityTheme { CurrencyPicker(selected, { selected = it }) }
            }
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent(content = content) }
            click("Currency: LKR"); click("JPY")
            await { text(it, "Currency: JPY") }
            scenario.onActivity { activity ->
                val saved = registry.performSave()
                dispose(activity)
                registry = SaveableStateRegistry(saved) { true }
                activity.setContent(content = content)
            }
            instrumentation.waitForIdleSync()
            await { text(it, "Currency: JPY") }
        }
    }

    @Test fun cachedQuoteShowsExactEquivalentAndProviderTime() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ClarityTheme { ConversionInfo("KWD", ConversionSnapshot(1234, "1.234", "ExchangeRate-API", 1000, 2000, true), false) }
            } }
            await { text(it, "Reporting equivalent: KWD 1.234") }
            await { it.text?.contains("Cached source-to-target rate 1.234") == true && it.text?.contains("1970-01-01T00:00:01Z") == true }
            await { it.isClickable && text(it, "Rates By Exchange Rate API") }
        }
    }

    @Test fun unavailablePreviewAllowsRetryAndRestoresDraftAmount() {
        var retries = 0
        var registry = SaveableStateRegistry(null) { true }
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                ClarityTheme { TransactionEditContent(TransactionEditState(previewError = true), {}, { _, _, _, _, _ -> }, {}, {},
                    onPreview = { retries++ }) }
            }
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent(content = content) }
            val amount = await { it.isEditable }
            assertTrue(amount.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "12.50") }))
            await { it.isEditable && it.text?.contains("12.50") == true }
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            click("Retry conversion")
            scenario.onActivity { activity ->
                assertTrue(retries > 0)
                val saved = registry.performSave()
                dispose(activity)
                registry = SaveableStateRegistry(saved) { true }
                activity.setContent(content = content)
            }
            instrumentation.waitForIdleSync()
            await { it.isEditable && it.text?.contains("12.50") == true }
            await { it.text?.contains("Conversion unavailable.") == true }
        }
    }

    private fun dispose(activity: ComponentActivity) {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        (root.getChildAt(0) as androidx.compose.ui.platform.ComposeView).disposeComposition()
    }

    private fun text(node: AccessibilityNodeInfo, value: String): Boolean =
        node.text?.toString() == value || (0 until node.childCount).any { index -> node.getChild(index)?.let { text(it, value) } == true }

    private fun click(value: String) {
        assertTrue(await { it.isClickable && text(it, value) }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun await(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { child -> find(child)?.let { return it } }
            return null
        }
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.uiAutomation.rootInActiveWindow?.let { root -> find(root)?.let { return it } }
            SystemClock.sleep(40)
        }
        throw AssertionError("Expected accessible currency control was not found")
    }
}
