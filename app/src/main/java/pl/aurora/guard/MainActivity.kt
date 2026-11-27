package pl.aurora.guard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Ekran glowny (chroniony PIN-em rodzica): stan polaczenia i lista kontrolna uprawnien; zebatka otwiera Ustawienia. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var statusCard: LinearLayout
    private lateinit var permCard: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        window.statusBarColor = android.graphics.Color.parseColor("#060A18")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A1230")
        if (prefs.pin.isNotEmpty()) askPin() else build()
    }

    private fun askPin() = Ui.askPin(this, prefs.pin, { build() }, { finish() })

    private fun build() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 28), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 40)) }

        // naglowek
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@MainActivity).apply { setImageResource(R.mipmap.ic_launcher); clipToOutline = true }, LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 56), Ui.dp(this@MainActivity, 56)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(this@MainActivity, 14), 0, 0, 0)
                addView(TextView(this@MainActivity).apply { text = "Aurora Guard"; textSize = 26f; setTextColor(Ui.INK); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
                addView(TextView(this@MainActivity).apply { text = "Blokady aplikacji z Home Assistanta · v${BuildConfigVersion.NAME}"; textSize = 13f; setTextColor(Ui.INK2) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "⚙"; textSize = 28f; setTextColor(Ui.INK); gravity = Gravity.CENTER; contentDescription = "Ustawienia"
                setPadding(Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 6))
                setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
            })
        })

        root.addView(Ui.section(this, "Stan"))
        statusCard = Ui.card(this); root.addView(statusCard)

        root.addView(Ui.section(this, "Uprawnienia"))
        permCard = Ui.card(this); root.addView(permCard)

        setContentView(ScrollView(this).apply { background = Ui.background(); isFillViewport = true; addView(root) })
        refresh()
    }

    private fun askLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 8); return }
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 30) AlertDialog.Builder(this).setTitle("Lokalizacja w tle")
                .setMessage("Na następnym ekranie wybierz: Uprawnienia → Lokalizacja → „Zezwalaj cały czas”.")
                .setPositiveButton("Dalej") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }.setNegativeButton("Anuluj", null).show()
            else requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 9)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (::statusCard.isInitialized) refresh()
    }

    private fun refresh() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val acc = GuardAccessibilityService.instance != null
        val sync = if (prefs.lastSync > 0) java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(prefs.lastSync)) else "—"
        val notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val bat = pm.isIgnoringBatteryOptimizations(packageName)

        statusCard.removeAllViews()
        statusCard.addView(Ui.statusRow(this, "Połączenie z HA" + (if (prefs.urlInternal.isNotBlank() && prefs.urlMode.isNotBlank()) " (${prefs.urlMode})" else ""), if (prefs.connected) "ostatnia synchronizacja $sync" else if (prefs.authRejected) "Home Assistant odrzucił token – utwórz nowy (Profil → Zabezpieczenia) i wklej go w ustawieniach" else "brak połączenia – sprawdź adres i token", prefs.connected))
        statusCard.addView(Ui.statusRow(this, if (prefs.locked) "Blokada WŁĄCZONA" else "Blokada wyłączona", "aplikacji na liście: ${prefs.blocked.size}" + (if (prefs.blocked.isNotEmpty()) "  ${prefs.blocked.take(3)}" else ""), if (prefs.locked) null else true))
        statusCard.addView(Ui.statusRow(this, if (prefs.webhook.isNotBlank()) "Raporty przez webhook" else "Raporty przez REST (admin)", if (prefs.webhook.isNotBlank()) "token może należeć do zwykłego użytkownika" else "token musi należeć do administratora", prefs.webhook.isNotBlank()))
        statusCard.addView(Ui.statusRow(this, "Encje", "${prefs.lockEntity}\n${prefs.todoEntity}", null))
        statusCard.addView(Ui.statusRow(this, if (prefs.fullLocked) "Cały telefon ZABLOKOWANY" else "Cały telefon odblokowany", "dozwolone: telefon, SMS, kontakty" + (if (prefs.allowed.isNotEmpty()) " + ${prefs.allowed.size} z listy" else ""), if (prefs.fullLocked) null else true))
        if (Policy.isDeviceOwner(this)) statusCard.addView(Ui.statusRow(this, "Device Owner", "aplikacje są zawieszane przez system", true))

        permCard.removeAllViews()
        permCard.addView(Ui.statusRow(this, "Usługa dostępności", if (acc) "włączona" else "dotknij, aby włączyć (Aurora Guard → włącz)", acc) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        permCard.addView(Ui.statusRow(this, "Ustawienia ograniczone (Android 13+)", "jeśli nie da się włączyć usługi: Info o aplikacji → ⋮ → Zezwól", null) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
        if (prefs.urlInternal.isNotBlank() && prefs.homeSsid.isNotBlank()) {
            val loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            permCard.addView(Ui.statusRow(this, "Lokalizacja (odczyt nazwy Wi-Fi)", if (loc) "zezwolona" else "dotknij, aby zezwolić – bez tego aplikacja sprawdza dostępność adresu wewnętrznego", loc) { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 7) })
        }
        if (prefs.locEnabled) {
            val bg = Build.VERSION.SDK_INT < 29 || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            permCard.addView(Ui.statusRow(this, "Lokalizacja w tle", if (fine && bg) "włączona" + (if (prefs.webhook.isBlank()) " – ale brak webhooka, nic nie jest wysyłane" else "") else "dotknij, aby zezwolić „cały czas”", fine && bg && prefs.webhook.isNotBlank()) { askLocation() })
        }
        val usage = Usage.hasAccess(this)
        permCard.addView(Ui.statusRow(this, "Dostęp do danych o użyciu aplikacji", if (usage) "włączony (limity czasu i statystyki)" else "dotknij, aby zezwolić – bez tego nie działają limity czasu", usage) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        permCard.addView(Ui.statusRow(this, "Powiadomienia", if (notif) "włączone" else "dotknij, aby zezwolić", notif) { if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) })
        permCard.addView(Ui.statusRow(this, "Bez ograniczeń baterii", if (bat) "włączone" else "dotknij, aby zezwolić (praca w tle)", bat) { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) })
    }

    override fun onResume() {
        super.onResume()
        val dp = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        if (dp.isDeviceOwnerApp(packageName)) try { dp.setUninstallBlocked(android.content.ComponentName(this, AdminReceiver::class.java), packageName, true) } catch (_: Exception) { }
        if (::statusCard.isInitialized) refresh()
    }
}

object BuildConfigVersion { const val NAME = "2.7" }
