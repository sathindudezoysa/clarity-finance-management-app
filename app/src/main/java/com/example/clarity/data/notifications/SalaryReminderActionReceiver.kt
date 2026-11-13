package com.example.clarity.data.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.clarity.domain.usecase.salary.ConfirmSalaryReceivedUseCase
import com.example.clarity.domain.usecase.salary.SnoozeSalaryReminderUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SalaryReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var confirmSalaryReceived: ConfirmSalaryReceivedUseCase
    @Inject lateinit var snoozeSalaryReminder: SnoozeSalaryReminderUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_CONFIRM -> {
                        val result = confirmSalaryReceived()
                        if (result.isSuccess) {
                            dismiss(appContext)
                        } else {
                            Toast.makeText(appContext, "Couldn't add salary. Open Clarity and try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                    ACTION_SNOOZE -> {
                        snoozeSalaryReminder()
                        dismiss(appContext)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(SalaryReminderWorker.NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_CONFIRM = "com.example.clarity.action.SALARY_CONFIRM"
        const val ACTION_SNOOZE = "com.example.clarity.action.SALARY_SNOOZE"
    }
}
