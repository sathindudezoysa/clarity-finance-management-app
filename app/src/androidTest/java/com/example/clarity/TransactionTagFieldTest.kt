package com.example.clarity

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.edit.TransactionTagField
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionTagFieldTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun predefinedAndSavedCustomTagsCanBeSelectedWithOneTapOrEnteredDirectly() {
        // Public accessibility actions avoid the old Espresso input injector, which is
        // incompatible with newer Android versions; no project dependency upgrade is needed.
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    var tag by remember { mutableStateOf("") }
                    ClarityTheme {
                        TransactionTagField(tag, TransactionTags.predefined + "Hobby", { tag = it }, true, null)
                    }
                }
            }

            awaitNode { it.text?.toString() == "General" && !it.isEditable }
            assertTrue(awaitNode { it.isClickable && !it.isEditable && containsText(it, "Food") }
                .performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitNode { it.isEditable && it.text?.toString() == "Food" }
            assertTrue(awaitNode { it.isClickable && !it.isEditable && containsText(it, "Hobby") }
                .performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val input = awaitNode { it.isEditable && it.text?.toString() == "Hobby" }
            val replacement = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "New custom tag")
            }
            assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, replacement))
            awaitNode { it.isEditable && it.text?.toString() == "New custom tag" }
        }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            instrumentation.uiAutomation.rootInActiveWindow?.let { root ->
                findNode(root, predicate)?.let { return it }
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Expected tag control or updated text was not available: " +
            instrumentation.uiAutomation.rootInActiveWindow?.let { describeTree(it) })
    }

    private fun findNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> findNode(child, predicate)?.let { return it } }
        }
        return null
    }

    private fun containsText(node: AccessibilityNodeInfo, text: String): Boolean =
        findNode(node) { it.text?.toString() == text } != null

    private fun describeTree(node: AccessibilityNodeInfo): String {
        val expectedText = TransactionTags.predefined + listOf("Hobby", "New custom tag")
        val text = node.text?.toString()?.takeIf { it in expectedText } ?: "[other]"
        return "${node.className}: $text clickable=${node.isClickable} editable=${node.isEditable} children=[" +
            (0 until node.childCount).mapNotNull { node.getChild(it)?.let(::describeTree) }.joinToString() + "]"
    }
}
