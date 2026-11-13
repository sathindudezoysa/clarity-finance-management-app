package com.example.clarity.presentation.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.presentation.theme.ClarityTheme
import dagger.hilt.android.AndroidEntryPoint

/** Translucent pop-up opened from the widget so the user can type an amount with the keyboard. */
@AndroidEntryPoint
class QuickAmountActivity : ComponentActivity() {
    private val viewModel: QuickAmountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        val category = intent.getStringExtra(EXTRA_CATEGORY)
        if (type == null || category.isNullOrBlank()) {
            finishAndRemoveTask()
            return
        }
        window.setBackgroundDrawableResource(android.R.color.transparent)
        viewModel.start(type, category)
        setContent {
            ClarityTheme(applyWindowStyling = false) {
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(state.signedOut) { if (state.signedOut) finishAndRemoveTask() }
                LaunchedEffect(state.isSaved) {
                    if (state.isSaved) {
                        finishAndRemoveTask()
                    }
                }
                QuickAmountDialog(
                    state = state,
                    onAmountChange = viewModel::onAmountChange,
                    onSubmit = viewModel::submit,
                    onDismiss = ::finishAndRemoveTask
                )
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_CATEGORY = "extra_category"

        fun intent(context: Context, type: TransactionType, category: String): Intent =
            Intent(context, QuickAmountActivity::class.java)
                // Own task so the app's main screen is never pulled up behind the pop-up.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                // Unique data keeps each tile's PendingIntent distinct (extras are ignored when matching).
                .setData(android.net.Uri.fromParts("clarity-quick", "${type.name}/$category", null))
                .putExtra(EXTRA_TYPE, type.name)
                .putExtra(EXTRA_CATEGORY, category)
    }
}
