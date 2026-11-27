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
    override fun onCreate() { super.onCreate(); running = true; instance = this }

    private var client: HaClient? = null
    private var netCb: android.net.ConnectivityManager.NetworkCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            Thread { client?.maybeStatus(); client?.maybeLocate(); client?.maybeUsage() }.start()
            handler.postDelayed(this, 300_000L)
        }
    }

    /** Wywolywane z alarmu (HeartbeatReceiver): wznow polaczenie, jesli padlo, i wyslij status. */
    fun heartbeat() {
        val c = client ?: return
        if (!Prefs(this).connected) c.reconnect()
        c.maybeStatus(); c.maybeLocate(); c.maybeUsage()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("guard", "Aurora Guard", NotificationManager.IMPORTANCE_MIN))
        val n = note(normalText())
        try {
            if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(1, n)
            AppLog.i(this, "service", "usluga pierwszoplanowa uruchomiona")
        } catch (e: Throwable) {
            AppLog.e(this, "service", "startForeground odrzucony przez system - dzialam bez powiadomienia", e)   // dawniej: wyjatek zabijal proces razem z usluga dostepnosci
        }
        try { evaluate(); Policy.apply(this) } catch (e: Throwable) { AppLog.e(this, "service", "evaluate/Policy", e) }
        Watchdog.schedule(this)
        Heartbeat.schedule(this)
        return START_STICKY
    }

    private fun normalText() = if (Prefs(this).locEnabled) "Blokady aplikacji i lokalizacja – zarządzane z Home Assistanta" else "Blokady aplikacji zarządzane z Home Assistanta"
    private fun note(text: String): Notification = Notification.Builder(this, "guard").setContentTitle("Aurora Guard działa").setContentText(text)
        .setSmallIcon(R.drawable.ic_stat_guard).setOngoing(true)
        .setContentIntent(android.app.PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)).build()
    private var wasSleep = false

    /** Tryb nocny: w nocy rozlacza sie z HA (po wyslaniu informacji, ze telefon spi), rano laczy sie ponownie. Blokady dzialaja lokalnie. */
    fun evaluate() {
        val prefs = Prefs(this)
        Sleep.scheduleNext(this)
        val sleeping = Sleep.isNow(prefs)
        if (sleeping) {
            handler.removeCallbacks(tick)
            try { netCb?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(it) } } catch (_: Exception) { }
            netCb = null
            val c = client; client = null
            if (c != null) Thread { c.reportStatus(Sleep.wakeAt(prefs) / 1000 + 1800); c.stop() }.start()
            val t = Sleep.wakeAt(prefs)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = t }
            val n = note("Tryb nocny – połączenie z HA wróci o " + String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)))
            nm.notify(1, n)
            wasSleep = true
            return
        }
        if (wasSleep) { wasSleep = false; (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, note(normalText())) }
        if (prefs.configured && client == null) {
            client = HaClient(this) { Policy.apply(this); GuardAccessibilityService.instance?.rulesChanged() }.also { it.start() }
            handler.postDelayed(tick, 20_000L)
            if (netCb == null && prefs.urlInternal.isNotBlank()) try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                netCb = object : android.net.ConnectivityManager.NetworkCallback() {
                    private var last = 0L
                    private fun change() { val n = System.currentTimeMillis(); if (n - last < 4000) return; last = n; Thread { Thread.sleep(1500); client?.reconnect() }.start() }
                    override fun onAvailable(network: android.net.Network) { change() }
                    override fun onLost(network: android.net.Network) { change() }
                }
                cm.registerDefaultNetworkCallback(netCb!!)
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        running = false; instance = null
        handler.removeCallbacks(tick)
        try { netCb?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(it) } } catch (_: Exception) { }
        netCb = null
        client?.stop(); client = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var running = false
        @Volatile var instance: GuardService? = null
        fun start(ctx: Context) {
            val i = Intent(ctx, GuardService::class.java)
            try { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) } catch (_: Exception) { }
        }
    }
}
