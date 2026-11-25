package pl.aurora.guard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/** Egzekwowanie blokady: nakladka z uslugi dostepnosci (zawsze) oraz zawieszanie pakietow, gdy aplikacja jest Device Ownerem. */
object Policy {
    fun shouldBlock(prefs: Prefs, pkg: String): Boolean = prefs.locked && pkg in prefs.blocked

    fun isDeviceOwner(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(ctx.packageName)
    }

    fun apply(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!isDeviceOwner(ctx)) return
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(ctx, AdminReceiver::class.java)
        val want: Set<String> = if (prefs.locked) prefs.blocked else emptySet()
        val toUnsuspend = prefs.suspended - want
        try {
            if (toUnsuspend.isNotEmpty()) dpm.setPackagesSuspended(admin, toUnsuspend.toTypedArray(), false)
            if (want.isNotEmpty()) dpm.setPackagesSuspended(admin, want.toTypedArray(), true)
            prefs.suspended = want
        } catch (_: Exception) {
        }
    }
}
