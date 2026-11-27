package pl.aurora.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Po aktualizacji (zwlaszcza Android 14) system potrafi wylaczyc usluge dostepnosci. Gdy aplikacja ma uprawnienie WRITE_SECURE_SETTINGS
 *  (jednorazowo: adb shell pm grant pl.aurora.guard android.permission.WRITE_SECURE_SETTINGS), wlacza ja sama; w przeciwnym razie
 *  wysyla powiadomienie z prosba o wlaczenie. Wszystko trafia do dziennika. */
object Heal {
    private val cn get() = ComponentName("pl.aurora.guard", "pl.aurora.guard.GuardAccessibilityService").flattenToString()

    fun enabledInSettings(ctx: Context): Boolean = try {
        (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").split(':').any { it.equals(cn, true) || it.endsWith("GuardAccessibilityService") }
    } catch (_: Exception) { false }

    fun check(ctx: Context, why: String) {
        val p = Prefs(ctx)
        if (!p.configured || GuardAccessibilityService.instance != null) return
        val en = enabledInSettings(ctx)
        AppLog.w(ctx, "heal", "usluga dostepnosci nie dziala (${why}); w ustawieniach systemu: ${if (en) "wlaczona" else "WYLACZONA"}")
        if (!en && ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val cur = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (cur.isBlank()) cn else "$cur:$cn")
                Settings.Secure.putInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                AppLog.i(ctx, "heal", "wlaczono usluge dostepnosci automatycznie")
                return
            } catch (e: Exception) { AppLog.e(ctx, "heal", "nie udalo sie wlaczyc uslugi", e) }
        }
        if (!en) notifyUser(ctx)
    }

    private fun notifyUser(ctx: Context) {
        val p = Prefs(ctx)
        if (System.currentTimeMillis() - p.lastHealNote < 3 * 3600_000L) return
        p.lastHealNote = System.currentTimeMillis()
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("heal", "Wymagana akcja", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(ctx, 5, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(9, android.app.Notification.Builder(ctx, "heal").setContentTitle("Aurora Guard: włącz usługę dostępności").setContentText("System wyłączył usługę (np. po aktualizacji). Dotknij, aby włączyć.")
                .setSmallIcon(R.drawable.ic_stat_guard).setContentIntent(pi).setAutoCancel(true).build())
            AppLog.i(ctx, "heal", "wyslano powiadomienie o wlaczeniu uslugi dostepnosci")
        } catch (e: Exception) { AppLog.e(ctx, "heal", "powiadomienie", e) }
    }
}
