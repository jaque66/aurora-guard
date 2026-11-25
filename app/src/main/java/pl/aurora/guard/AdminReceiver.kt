package pl.aurora.guard

import android.app.admin.DeviceAdminReceiver

/** Odbiornik administratora urzadzenia - pozwala pozniej nadac aplikacji uprawnienia Device Owner (opcjonalnie, przez ADB). */
class AdminReceiver : DeviceAdminReceiver()
