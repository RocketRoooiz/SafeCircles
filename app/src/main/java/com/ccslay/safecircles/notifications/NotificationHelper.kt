package com.ccslay.safecircles.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ccslay.safecircles.MainActivity
import com.ccslay.safecircles.R

class NotificationHelper(private val context: Context) {


    private val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)

    private fun loadShownIds(): MutableSet<Int> {
        return prefs.getStringSet("shown_ids", emptySet())?.map { it.toInt() }?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveShownIds() {
        prefs.edit().putStringSet("shown_ids", shownNotificationIds.map { it.toString() }.toSet()).apply()
    }

    private val shownNotificationIds = loadShownIds()


    companion object {
        private const val CHANNEL_ID = "hazard_alerts"
        private const val CHANNEL_NAME = "Hazard Alerts"
    }

    init {
        // Create the channel once (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(ch)
        }
    }

    fun show(title: String, message: String) = showNotification(title, message)

    fun showNotification(title: String, message: String, openMainOnTap: Boolean = true) {
        val id = (title + "|" + message).hashCode()
        //if (shownNotificationIds.contains(id)) return
        shownNotificationIds.add(id)
        saveShownIds()

        val contentIntent = if (openMainOnTap) {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null


        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // replace with your bell/alert icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (contentIntent != null) builder.setContentIntent(contentIntent)

        // De-dup: stable ID based on content string (prevents spam repeats)


        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, builder.build())
    }
}
