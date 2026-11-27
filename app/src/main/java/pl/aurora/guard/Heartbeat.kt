package pl.aurora.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Heartbeat budzony przez AlarmManager (setAndAllowWhileIdle): dziala tez przy wygaszonym ekranie i w Doze, gdy zwykly Handler stoi.
 *  Co ~10 min: upewnia sie, ze usluga zyje, polaczenie z HA jest aktywne, i wysyla status. */
object Heartbeat {
    private const val EVERY = 10 * 60_000L

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, 12, Intent(ctx, HeartbeatReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + EVERY, pi) } catch (_: Exception) { }
    }
}

class HeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        if (!prefs.configured) return
        Heartbeat.schedule(ctx)
        if (Sleep.isNow(prefs)) return
        try { Heal.check(ctx, "heartbeat") } catch (_: Throwable) { }
        val s = GuardService.instance
        if (s == null) { AppLog.w(ctx, "heartbeat", "usluga nie dziala - uruchamiam"); GuardService.start(ctx); return }
        val pr = goAsync()
        Thread { try { s.heartbeat() } catch (_: Exception) { } finally { pr.finish() } }.start()
    }
}
