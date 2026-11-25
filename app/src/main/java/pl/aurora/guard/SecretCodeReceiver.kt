package pl.aurora.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Kod *#*#4827#*#* wpisany w aplikacji Telefon otwiera ustawienia Aurora Guard (gdy ikona jest ukryta). */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
