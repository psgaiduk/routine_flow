package com.routineflow.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.routineflow.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OvertimeNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val alertSound by lazy {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            listOf("routine_flow_overtime_v2", "routine_flow_overtime_v3", "routine_flow_overtime_v4", "routine_flow_overtime_v5")
                .forEach(notificationManager::deleteNotificationChannel)
            val preferences = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
            if (!preferences.getBoolean("reset_first_channel_v1", false)) {
                notificationManager.deleteNotificationChannel(CHANNEL_ID)
                preferences.edit().putBoolean("reset_first_channel_v1", true).apply()
            }
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notification_channel_description)
                setSound(alertSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 180, 350)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notifyOvertime(chainName: String, actionName: String, reminderNumber: Int, overtimeSeconds: Long) {
        playAlarmSignal()
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
            .setSound(alertSound)
            .setVibrate(longArrayOf(0, 350, 180, 350))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setTimeoutAfter(5_000L)
            .setOnlyAlertOnce(false)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification) }
    }

    private fun playAlarmSignal() {
        runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                setDataSource(context, alertSound)
                setOnCompletionListener { it.release() }
            }
            player.setOnPreparedListener {
                it.start()
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching {
                        if (it.isPlaying) it.stop()
                        it.release()
                    }
                }, 700L)
            }
            player.prepareAsync()
        }
    }

    fun updateTimer(chainName: String, actionName: String, elapsedSeconds: Long, totalSeconds: Long, overtime: Boolean) {
        val text = if (overtime) {
            context.getString(R.string.notification_timer_overtime, actionName)
        } else {
            context.getString(R.string.notification_timer_running, actionName)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("${context.getString(R.string.app_name)} · $chainName")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(totalSeconds.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), elapsedSeconds.coerceAtMost(totalSeconds).coerceAtLeast(0).toInt(), false)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun clearTimer() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            NotificationManagerCompat.from(context).cancel(ALERT_NOTIFICATION_ID)
        }
    }

    private fun formatSeconds(seconds: Long): String = if (seconds >= 60) {
        context.getString(R.string.duration_minutes_seconds, seconds / 60, seconds % 60)
    } else context.getString(R.string.duration_seconds, seconds)

    private companion object { const val CHANNEL_ID = "routine_flow_overtime"; const val NOTIFICATION_ID = 4101; const val ALERT_NOTIFICATION_ID = 4102 }
}
