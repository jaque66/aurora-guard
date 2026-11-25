package pl.aurora.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** Administrator urzadzenia: gdy aktywny, system nie pozwala odinstalowac aplikacji, dopoki administrator nie zostanie wylaczony. Z uprawnieniami Device Owner (ADB) dodatkowo blokuje odinstalowanie na stale. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisabled(context: android.content.Context, intent: android.content.Intent) {
        Hook.tamper(context, "Wyłączono administratora urządzenia (ochrona przed odinstalowaniem)")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Wyłączenie ochrony pozwoli odinstalować Aurora Guard. Rodzic zostanie o tym poinformowany w Home Assistant."
}
