package com.example.clarity.presentation.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

internal val TypeParam = ActionParameters.Key<String>("type")

/** Switches the widget between the Expense and Income tabs. */
class SelectTypeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val type = parameters[TypeParam] ?: return
        updateAppWidgetState(context, glanceId) { it[QuickEntryKeys.type] = type }
        QuickEntryWidget().update(context, glanceId)
    }
}
