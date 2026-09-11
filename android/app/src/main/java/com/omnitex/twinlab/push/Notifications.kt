package com.omnitex.twinlab.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.omnitex.twinlab.MainActivity
import com.omnitex.twinlab.R

const val ALERT_CHANNEL = "twinlab_alerts"

fun ensureChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(
            ALERT_CHANNEL, "TwinLab Alerts", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Asset threshold, fuel-theft and consumable alerts" }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

fun showAlertNotification(ctx: Context, title: String, body: String, deviceId: String?) {
    ensureChannel(ctx)

    val intent = Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("deviceId", deviceId)
    }
    val pi = PendingIntent.getActivity(
        ctx,
        deviceId?.hashCode() ?: 0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val n = NotificationCompat.Builder(ctx, ALERT_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pi)
        .build()

    NotificationManagerCompat.from(ctx).notify(deviceId?.hashCode() ?: 1, n)
}
