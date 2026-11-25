package pl.aurora.guard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/** Aplikacje dozwolone przy blokadzie calego telefonu: telefon, SMS, kontakty, alarmowe + lista z HA; wyjatki systemowe. */
object Allowed {
    private fun resolve(ctx: Context, i: Intent): String? = ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName

    fun launchers(ctx: Context): Set<String> {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(q, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Pakiety, ktorych nigdy nie zaslaniamy (inaczej telefon przestaje dzialac). */
    fun exempt(ctx: Context): Set<String> {
        val s = HashSet<String>()
        s.add(ctx.packageName); s.add("com.android.systemui"); s.add("android"); s.add("com.android.emergency"); s.add("com.google.android.apps.safetyhub")
        s.addAll(launchers(ctx))
        try {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.forEach { s.add(it.packageName) }
        } catch (_: Exception) { }
        return s
    }

    /** Telefon, SMS, kontakty (domyslne aplikacje systemu) - zawsze dozwolone w trybie calego telefonu. */
    fun core(ctx: Context): Set<String> {
        val s = LinkedHashSet<String>()
        try { (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage?.let { s.add(it) } } catch (_: Exception) { }
        resolve(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))?.let { s.add(it) }
        try { Telephony.Sms.getDefaultSmsPackage(ctx)?.let { s.add(it) } } catch (_: Exception) { }
        resolve(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))?.let { s.add(it) }
        resolve(ctx, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))?.let { s.add(it) }
        s.add("com.android.server.telecom"); s.add("com.android.phone"); s.add("com.android.incallui")
        return s
    }

    fun all(ctx: Context, prefs: Prefs): Set<String> = core(ctx) + prefs.allowed + exempt(ctx)

    /** Aplikacje do pokazania jako przyciski na ekranie blokady (tylko te z ikona w launcherze). */
    fun launchable(ctx: Context, prefs: Prefs): List<Pair<String, String>> {
        val pm = ctx.packageManager
        return (core(ctx) + prefs.allowed).filter { it != ctx.packageName }.mapNotNull { p ->
            val li = pm.getLaunchIntentForPackage(p) ?: return@mapNotNull null
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { return@mapNotNull null }
            li.component?.packageName?.let { label to p }
        }.distinctBy { it.second }
    }
}
