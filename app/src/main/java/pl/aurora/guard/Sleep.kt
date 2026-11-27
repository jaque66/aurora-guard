package pl.aurora.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Tryb nocny: w ustawionych godzinach aplikacja rozlacza sie z HA i nie raportuje (oszczednosc baterii); blokady dzialaja dalej lokalnie na ostatnio znanych regulach. */
object Sleep {
    fun isNow(p: Prefs): Boolean {
        if (!p.sleepOn) return false
        val c = Calendar.getInstance(); val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE); val a = p.sleepFrom; val b = p.sleepTo
        return if (a == b) false else if (a < b) m in a until b else (m >= a || m < b)
    }

    private fun at(min: Int, afterNow: Boolean = true): Long {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, min / 60); set(Calendar.MINUTE, min % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (afterNow && c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Najblizsza godzina budzenia (ms). */
    fun wakeAt(p: Prefs): Long = at(p.sleepTo)

    /** Alarm na najblizsza granice (usypianie / budzenie) - budzi usluge, gdy telefon jest w uspieniu. */
    fun scheduleNext(ctx: Context) {
        val p = Prefs(ctx); val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, 11, Intent(ctx, SleepReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (!p.sleepOn || p.sleepFrom == p.sleepTo) { am.cancel(pi); return }
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, minOf(at(p.sleepFrom), at(p.sleepTo)) + 3000L, pi) } catch (_: Exception) { }
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { val s = GuardService.instance; if (s != null) s.evaluate() else if (Prefs(ctx).configured) GuardService.start(ctx) }
}
