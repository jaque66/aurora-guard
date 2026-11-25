package pl.aurora.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Prefs(ctx).configured) { GuardService.start(ctx); Watchdog.schedule(ctx) }
    }
}
