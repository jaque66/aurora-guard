package pl.aurora.guard

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.util.Calendar

/** Czas spedzony w aplikacjach (dostep do uzycia: Ustawienia -> Dostep do danych o uzyciu). */
object Usage {
    fun hasAccess(ctx: Context): Boolean = try {
        val ao = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val m = if (Build.VERSION.SDK_INT >= 29) ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        m == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    private fun dayStart(back: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -back); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Minuty dzis w aplikacji (liczone ze zdarzen, wraz z trwajaca teraz sesja). */
    fun minutesToday(ctx: Context, pkg: String): Int {
        if (!hasAccess(ctx)) return 0
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis(); val ev = usm.queryEvents(dayStart(), now); val e = UsageEvents.Event()
            var total = 0L; var start = 0L
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.packageName != pkg) continue
                @Suppress("DEPRECATION")
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> if (start == 0L) start = e.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (start != 0L) { total += e.timeStamp - start; start = 0L }
                }
            }
            if (start != 0L) total += now - start
            (total / 60000).toInt()
        } catch (_: Exception) { 0 }
    }

    private fun launchable(ctx: Context): Set<String> =
        ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet() - ctx.packageName

    /** Dzis: lista (pakiet, nazwa, minuty) malejaco + suma, oraz suma minut z ostatnich 7 dni (najstarszy pierwszy). */
    fun report(ctx: Context): Triple<List<Triple<String, String, Int>>, Int, List<Int>> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = ctx.packageManager; val ok = launchable(ctx); val now = System.currentTimeMillis()
        val today = usm.queryAndAggregateUsageStats(dayStart(), now).filter { it.key in ok && it.value.totalTimeInForeground >= 60000 }
            .map { (p, s) -> Triple(p, try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString().take(40) } catch (_: Exception) { p }, (s.totalTimeInForeground / 60000).toInt()) }
            .sortedByDescending { it.third }
        return Triple(today.take(12), today.sumOf { it.third }, emptyList<Int>())
    }

    /** Historia: ostatnie 14 dni - data, suma minut i top 8 aplikacji danego dnia (system trzyma dane o uzyciu ok. tygodnia, HA gromadzi je dalej). */
    fun days(ctx: Context): List<Triple<String, Int, List<Triple<String, String, Int>>>> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = ctx.packageManager; val ok = launchable(ctx); val now = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return (13 downTo 0).mapNotNull { b ->
            val from = dayStart(b); val to = if (b == 0) now else dayStart(b - 1)
            val m = try { usm.queryAndAggregateUsageStats(from, to).filter { it.key in ok && it.value.totalTimeInForeground >= 60000 } } catch (_: Exception) { return@mapNotNull null }
            if (m.isEmpty()) return@mapNotNull null
            val apps = m.map { (p, s) -> Triple(p, try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString().take(40) } catch (_: Exception) { p }, (s.totalTimeInForeground / 60000).toInt()) }.sortedByDescending { it.third }
            Triple(fmt.format(java.util.Date(from + 3600_000L)), apps.sumOf { it.third }, apps.take(8))
        }
    }
}
