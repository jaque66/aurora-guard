package pl.aurora.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Wiadomosc od rodzica: powiadomienie + (gdy usluga dostepnosci dziala) okno na ekranie. */
object Notifier {
    fun parentMessage(ctx: Context, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("msg", "Wiadomości od rodzica", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(41, Notification.Builder(ctx, "msg").setSmallIcon(R.drawable.ic_stat_guard).setContentTitle("Wiadomość od rodzica").setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build())
        Handler(Looper.getMainLooper()).post { GuardAccessibilityService.instance?.showNote("💬", "Wiadomość od rodzica", text) }
    }
}
