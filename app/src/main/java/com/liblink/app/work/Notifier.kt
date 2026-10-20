package com.liblink.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.liblink.app.MainActivity
import com.liblink.app.R

// إشعارات انتهاء البناء على GitHub
object Notifier {
    private const val CHANNEL = "builds"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun buildDone(ctx: Context, projectId: String, name: String, number: Int, ok: Boolean, detail: String? = null) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        // النقر على الإشعار يفتح شاشة البناء لهذا المشروع
        val intent = Intent(ctx, MainActivity::class.java).putExtra("project_id", projectId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, projectId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = detail ?: ctx.getString(if (ok) R.string.notif_success_text else R.string.notif_failed_text)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(ctx.getString(if (ok) R.string.notif_success else R.string.notif_failed, name, number))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(projectId.hashCode(), n) } catch (_: SecurityException) { }
    }
}
