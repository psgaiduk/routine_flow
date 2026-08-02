package com.routineflow.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.routineflow.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OvertimeNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun notifyOvertime(chainName: String, actionName: String, reminderNumber: Int, overtimeSeconds: Long) {
        val text = if (reminderNumber == 0) {
            context.getString(R.string.notification_overtime_started, actionName)
        } else {
            context.getString(R.string.notification_overtime_reminder, actionName, formatSeconds(overtimeSeconds))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("${context.getString(R.string.notification_overtime_title)} · $chainName")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(chainName.hashCode() + actionName.hashCode() + reminderNumber, notification) }
    }

    private fun formatSeconds(seconds: Long): String = if (seconds >= 60) {
        context.getString(R.string.duration_minutes_seconds, seconds / 60, seconds % 60)
    } else context.getString(R.string.duration_seconds, seconds)

    private companion object { const val CHANNEL_ID = "routine_flow_overtime" }
}
