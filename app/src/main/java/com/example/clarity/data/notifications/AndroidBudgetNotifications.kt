package com.example.clarity.data.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.clarity.MainActivity
import com.example.clarity.R
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.BudgetNotificationDelivery
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBudgetNotifications @Inject constructor(@ApplicationContext private val context: Context) : BudgetNotificationDelivery {
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun channel() { manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.budget_channel), NotificationManager.IMPORTANCE_DEFAULT)) }
    override fun available(): Boolean {
        channel()
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }
    override fun deliver(status: BudgetStatus) {
        val budget = status.budget
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("clarity-budget://open/${Uri.encode(budget.userId)}/${budget.id}")
            putExtra("budgetOwner", budget.userId); putExtra("budgetId", budget.id)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun money(value: BigInteger) = "${budget.currency} ${BigDecimal(value, Currencies.digits(budget.currency)).toPlainString()}"
        val statusId = when (status.threshold) {
            BudgetThreshold.NEARING -> R.string.budget_nearing
            BudgetThreshold.REACHED -> R.string.budget_reached
            else -> R.string.budget_exceeded
        }
        val feedbackId = when (status.threshold) {
            BudgetThreshold.NEARING -> R.string.budget_feedback_nearing
            BudgetThreshold.REACHED -> R.string.budget_feedback_reached
            else -> R.string.budget_feedback_exceeded
        }
        val details = context.getString(R.string.budget_notification_body, budget.category, budget.month,
            money(status.spent), money(budget.limitMinor.toBigInteger()), context.getString(statusId), context.getString(feedbackId, budget.category)) +
            (if (status.remaining.signum() < 0) " " + context.getString(R.string.budget_exceeded_by, money(-status.remaining)) else "") +
            (if (status.uncounted > 0) " " + context.getString(R.string.budget_incomplete, status.uncounted) else "")
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_budget_notification)
            .setContentTitle(context.getString(R.string.budget_alert_title)).setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details)).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_SECRET).build()
        try { manager.notify(tag(budget.userId, budget.id), 1, notification) } catch (_: SecurityException) { /* In-app status remains available. */ }
    }
    override fun cancel(owner: String, budgetId: String) { manager.cancel(tag(owner, budgetId), 1) }
    override fun cancelOwner(owner: String) {
        manager.activeNotifications.filter { it.tag?.startsWith("budget:${Uri.encode(owner)}:") == true }.forEach { manager.cancel(it.tag, it.id) }
    }
    private fun tag(owner: String, id: String) = "budget:${Uri.encode(owner)}:$id"
    companion object { const val CHANNEL = "budget_thresholds" }
}
