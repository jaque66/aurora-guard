package pl.aurora.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

/** Usluga pierwszoplanowa: trzyma polaczenie z HA i co 5 min raportuje status. */
class GuardService : Service() {
    override fun onCreate() { super.onCreate(); running = true }

    private var client: HaClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            Thread { client?.reportStatus() }.start()
            handler.postDelayed(this, 300_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("guard", "Aurora Guard", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "guard").setContentTitle("Aurora Guard działa").setContentText("Blokady aplikacji zarządzane z Home Assistanta")
            .setSmallIcon(R.drawable.ic_stat_guard).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        val prefs = Prefs(this)
        if (prefs.configured && client == null) {
            client = HaClient(this) { Policy.apply(this); GuardAccessibilityService.instance?.rulesChanged() }.also { it.start() }
            handler.postDelayed(tick, 20_000L)
        }
        Policy.apply(this)
        Watchdog.schedule(this)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        client?.stop(); client = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var running = false
        fun start(ctx: Context) {
            val i = Intent(ctx, GuardService::class.java)
            try { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) } catch (_: Exception) { }
        }
    }
}
