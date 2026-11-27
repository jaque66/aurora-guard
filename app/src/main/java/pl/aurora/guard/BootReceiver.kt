package pl.aurora.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AppLog.i(ctx, "boot", "odbiornik: ${intent.action}")
        if (Prefs(ctx).configured) { GuardService.start(ctx); Watchdog.schedule(ctx); Heartbeat.schedule(ctx) }
    }
}
