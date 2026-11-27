package pl.aurora.guard

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Straznik: co ~15 min sprawdza, czy usluga dziala; jesli system ja ubil (np. po aktualizacji) - uruchamia ponownie. */
class Watchdog : JobService() {
    override fun onStartJob(p: JobParameters?): Boolean {
        val prefs = Prefs(this)
        if (prefs.configured && !GuardService.running) { AppLog.w(this, "watchdog", "usluga nie dziala - uruchamiam"); GuardService.start(this) }
        try { Heal.check(this, "watchdog") } catch (_: Throwable) { }
        if (prefs.autoUpdate && System.currentTimeMillis() - prefs.lastUpdateCheck > 20 * 3600 * 1000L) {
            Thread {
                try {
                    val info = Updater.latest(prefs); prefs.lastUpdateCheck = System.currentTimeMillis()
                    if (Updater.isNewer(info.version, BuildConfigVersion.NAME)) notifyUpdate(info.version)
                } catch (_: Exception) { }
                p?.let { jobFinished(it, false) }
            }.start()
            return true
        }
        return false
    }

    private fun notifyUpdate(v: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("update", "Aktualizacje", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val pi = android.app.PendingIntent.getActivity(this, 0, android.content.Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(7, android.app.Notification.Builder(this, "update").setContentTitle("Aurora Guard: dostępna aktualizacja " + v).setContentText("Dotknij, aby pobrać i zainstalować").setSmallIcon(R.drawable.ic_stat_guard).setContentIntent(pi).setAutoCancel(true).build())
    }

    override fun onStopJob(p: JobParameters?): Boolean = true

    companion object {
        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (js.allPendingJobs.any { it.id == 77 }) return
            js.schedule(JobInfo.Builder(77, ComponentName(ctx, Watchdog::class.java)).setPeriodic(15 * 60 * 1000L).setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE).build())
        }
    }
}
