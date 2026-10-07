package com.projectkaka.inventory.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.projectkaka.inventory.MainActivity
import com.projectkaka.inventory.R
import java.util.Calendar

/**
 * Manages dispatching care task reminders to:
 * 1. The device's internal Calendar app (via CalendarContract insert Intent).
 * 2. The device's Clock / Alarm app (via AlarmClock Intent).
 * 3. System notifications on the device (via NotificationManager).
 */
object CareReminderManager {

    private const val CHANNEL_ID = "kaka_care_tasks"
    private const val CHANNEL_NAME = "Care & Maintenance Tasks"

    /**
     * Pushes a recurring care task reminder directly to the phone's internal Calendar app.
     */
    fun pushToCalendar(
        context: Context,
        itemName: String,
        taskName: String,
        frequencyDays: Int,
        dueDateMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "Care: $itemName - $taskName")
                putExtra(
                    CalendarContract.Events.DESCRIPTION,
                    "Project Kaka Care Task: '$taskName' for item '$itemName'. Frequency: Every $frequencyDays day(s)."
                )
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueDateMillis)
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                if (frequencyDays > 0) {
                    putExtra(CalendarContract.Events.RRULE, "FREQ=DAILY;INTERVAL=$frequencyDays")
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("CareReminderManager", "Failed to launch calendar intent", e)
            Toast.makeText(context, "No calendar app found on device.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Sets an alarm reminder on the phone's internal Clock / Alarm app.
     */
    fun setAlarm(
        context: Context,
        itemName: String,
        taskName: String,
        hour: Int = 9,
        minutes: Int = 0
    ): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "Care: $itemName - $taskName")
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("CareReminderManager", "Failed to launch alarm intent", e)
            Toast.makeText(context, "No clock/alarm app found on device.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Posts a local notification for an overdue or due care task.
     */
    fun postDueNotification(
        context: Context,
        taskId: Int,
        itemName: String,
        taskName: String,
        overdueDays: Int
    ) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            // Create notification channel on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts and reminders for item care tasks"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                taskId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val statusText = if (overdueDays > 0) {
                "Overdue by $overdueDays day(s)! Item: $itemName"
            } else {
                "Due today for item: $itemName"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Care Task: $taskName")
                .setContentText(statusText)
                .setStyle(NotificationCompat.BigTextStyle().bigText("Task '$taskName' is due for $itemName. $statusText."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(taskId + 10000, notification)
            Toast.makeText(context, "Care notification posted for $taskName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("CareReminderManager", "Failed to post notification", e)
        }
    }
}
