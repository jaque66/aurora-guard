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

/** Ekran ustawien (chroniony PIN-em rodzica), stan polaczenia i lista kontrolna uprawnien - w stylu Aurora. */
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var statusCard: LinearLayout
    private lateinit var permCard: LinearLayout
    private var admSw: android.widget.Switch? = null
    private val fields = LinkedHashMap<String, EditText>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        window.statusBarColor = android.graphics.Color.parseColor("#060A18")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A1230")
        if (prefs.pin.isNotEmpty()) askPin() else build()
    }

    private fun askPin() {
        val et = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "PIN rodzica" }
        AlertDialog.Builder(this).setTitle("Aurora Guard").setView(et).setCancelable(false)
            .setPositiveButton("OK") { _, _ -> if (et.text.toString() == prefs.pin) build() else { Toast.makeText(this, "Zły PIN", Toast.LENGTH_SHORT).show(); finish() } }
            .setNegativeButton("Anuluj") { _, _ -> finish() }.show()
    }

    private fun field(root: LinearLayout, key: String, label: String, value: String, secret: Boolean = false, number: Boolean = false) {
        root.addView(Ui.label(this, label))
        val et = Ui.input(this, value, secret, number)
        fields[key] = et; root.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

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
            })
        })

        root.addView(Ui.section(this, "Stan"))
        statusCard = Ui.card(this); root.addView(statusCard)

        root.addView(Ui.section(this, "Uprawnienia"))
        permCard = Ui.card(this); root.addView(permCard)

        root.addView(Ui.section(this, "Ikona aplikacji"))
        val icoCard = Ui.card(this)
        val icoSw = android.widget.Switch(this).apply { text = "Ukryj ikonę na ekranie głównym"; setTextColor(Ui.INK); textSize = 15f; isChecked = iconHidden() }
        icoCard.addView(icoSw)
        icoCard.addView(TextView(this).apply {
            text = "Ustawienia otworzysz wtedy: dotykając powiadomienia „Aurora Guard działa”, w Ustawienia → Dostępność → Aurora Guard (ikona ⚙), albo wpisując w aplikacji Telefon kod *#*#4827#*#* (nie działa na każdym telefonie)."
            textSize = 12f; setTextColor(Ui.INK2); setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0)
        })
        icoSw.setOnCheckedChangeListener { v, on ->
            if (on == iconHidden()) return@setOnCheckedChangeListener
            if (on) {
                AlertDialog.Builder(this).setTitle("Ukryć ikonę?")
                    .setMessage("Ikona zniknie z ekranu głównego i listy aplikacji. Ustawienia otworzysz z powiadomienia „Aurora Guard działa”, z Ustawienia → Dostępność → Aurora Guard albo kodem *#*#4827#*#* w aplikacji Telefon.")
                    .setPositiveButton("Ukryj") { _, _ -> setIconHidden(true) }
                    .setNegativeButton("Anuluj") { _, _ -> v.isChecked = false }.show()
            } else setIconHidden(false)
        }
        root.addView(icoCard)

        root.addView(Ui.section(this, "Ochrona przed odinstalowaniem"))
        val admCard = Ui.card(this)
        val sw2 = android.widget.Switch(this).apply { text = "Administrator urządzenia"; setTextColor(Ui.INK); textSize = 15f; isChecked = adminActive() }
        admSw = sw2
        admCard.addView(sw2)
        admCard.addView(TextView(this).apply {
            text = "Gdy włączone, w Ustawieniach przycisk „Odinstaluj” jest nieaktywny, dopóki ktoś nie wyłączy tego administratora (Ustawienia → Zabezpieczenia → Administratorzy urządzenia). Pełną, trwałą blokadę daje Device Owner (instrukcja niżej)."
            textSize = 12f; setTextColor(Ui.INK2); setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0)
        })
        sw2.setOnCheckedChangeListener { _, on ->
            if (on == adminActive()) return@setOnCheckedChangeListener
            val cn = android.content.ComponentName(this, AdminReceiver::class.java)
            if (on) startActivity(Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Chroni aplikację Aurora Guard przed odinstalowaniem."))
            else dpm().removeActiveAdmin(cn)
        }
        root.addView(admCard)

        root.addView(Ui.section(this, "Lokalizacja"))
        val locCard = Ui.card(this)
        val sw3 = android.widget.Switch(this).apply { text = "Wysyłaj lokalizację do Home Assistanta"; setTextColor(Ui.INK); textSize = 15f; isChecked = prefs.locEnabled }
        locCard.addView(sw3)
        locCard.addView(TextView(this).apply {
            text = "Telefon przekazuje pozycję rodzicom: poza domem co ${prefs.locAwayMin} min, w domowym Wi-Fi co ${prefs.locHomeMin} min oraz na prośbę z Home Assistanta („Gdzie jest telefon?”). Częstotliwość i włączenie ustawia rodzic w Home Assistancie. Wymaga uprawnienia lokalizacji „Zezwalaj cały czas” i działa tylko przy ustawionym webhooku. Włączona lokalizacja jest widoczna w powiadomieniu aplikacji."
            textSize = 12f; setTextColor(Ui.INK2); setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0)
        })
        sw3.setOnCheckedChangeListener { _, on ->
            prefs.locEnabled = on
            if (on) askLocation()
            stopService(Intent(this, GuardService::class.java)); GuardService.start(this)
        }
        root.addView(locCard)

        root.addView(Ui.section(this, "Połączenie z Home Assistantem"))
        val form = Ui.card(this)
        field(form, "url", "Adres zewnętrzny Home Assistanta (poza domem, np. https://ha.twojadomena.pl)", prefs.url)
        field(form, "urli", "Adres wewnętrzny (w domowym Wi-Fi, np. http://192.168.1.10:8123) – opcjonalnie", prefs.urlInternal)
        field(form, "ssid", "Nazwa domowego Wi-Fi (SSID) – puste = sprawdzanie dostępności adresu wewnętrznego", prefs.homeSsid)
        field(form, "token", "Token dostępu (Profil → Zabezpieczenia → Długoterminowe tokeny)", prefs.token, secret = true)
        field(form, "device", "Identyfikator telefonu (np. dziecko1, dziecko2)", prefs.deviceId)
        val ents = TextView(this).apply { textSize = 11f; setTextColor(Ui.INK2); setPadding(Ui.dp(this@MainActivity, 2), Ui.dp(this@MainActivity, 6), 0, 0) }
        fun upd(id: String) { val s = Prefs.slug(id); ents.text = "Encje w HA wyliczane z identyfikatora:\ninput_boolean.blokada_telefon_$s\ninput_boolean.blokada_telefon_${s}_caly\ntodo.guard_$s, todo.guard_${s}_dozwolone\nsensor.aurora_guard_${s}_rules" }
        upd(prefs.deviceId)
        fields["device"]!!.addTextChangedListener(object : android.text.TextWatcher { override fun afterTextChanged(e: android.text.Editable?) { upd(e.toString()) } override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {} override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {} })
        form.addView(ents)
        field(form, "hook", "Identyfikator webhooka (z Home Assistanta; puste = tryb administratora)", prefs.webhook)
        field(form, "msg", "Komunikat przy zablokowanej aplikacji", prefs.message)
        field(form, "fmsg", "Komunikat przy blokadzie całego telefonu", prefs.fullMessage)
        field(form, "pin", "PIN rodzica do ustawień (puste = bez PIN-u)", prefs.pin, secret = true, number = true)
        form.addView(Ui.primary(this, "Zapisz i uruchom") { save() })
        root.addView(form)
        root.addView(Ui.secondary(this, "Aktualizacje") { startActivity(Intent(this, UpdateActivity::class.java)) })
        root.addView(Ui.secondary(this, "Historia zmian") { showChangelog() })

        root.addView(TextView(this).apply {
            text = "Opcjonalnie: twarde zawieszanie aplikacji (Device Owner) nadaje się z komputera:\nadb shell dpm set-device-owner pl.aurora.guard/.AdminReceiver\n(wymaga braku kont Google na telefonie).\n\nWersja ${BuildConfigVersion.NAME}"
            textSize = 11f; setTextColor(Ui.INK2); setPadding(Ui.dp(this@MainActivity, 4), Ui.dp(this@MainActivity, 8), 0, 0)
        })

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
        if (code == 8 && prefs.locEnabled) askLocation()
        if (::statusCard.isInitialized) refresh()
    }

    private fun dpm() = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
    private fun adminActive() = dpm().isAdminActive(android.content.ComponentName(this, AdminReceiver::class.java))

    private fun alias() = android.content.ComponentName(this, "pl.aurora.guard.LauncherAlias")
    private fun iconHidden(): Boolean = packageManager.getComponentEnabledSetting(alias()) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    private fun setIconHidden(h: Boolean) {
        packageManager.setComponentEnabledSetting(alias(), if (h) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        Toast.makeText(this, if (h) "Ikona ukryta (może potrwać chwilę)" else "Ikona widoczna", Toast.LENGTH_SHORT).show()
    }

    private fun showChangelog() {
        val txt = try { assets.open("changelog.md").bufferedReader(Charsets.UTF_8).readText() } catch (_: Exception) { "Brak pliku historii zmian." }
        val tv = TextView(this).apply { text = txt.replace("**", "").replace("### ", "").replace("## ", "").replace("# ", ""); textSize = 13f; setTextColor(Ui.INK); setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 12)) }
        AlertDialog.Builder(this).setTitle("Historia zmian").setView(ScrollView(this).apply { addView(tv) }).setPositiveButton("Zamknij", null).show()
    }

    private fun save() {
        prefs.url = fields["url"]!!.text.toString(); prefs.urlInternal = fields["urli"]!!.text.toString(); prefs.homeSsid = fields["ssid"]!!.text.toString(); prefs.token = fields["token"]!!.text.toString(); prefs.deviceId = fields["device"]!!.text.toString(); prefs.webhook = fields["hook"]!!.text.toString()
        // nazwy encji zawsze wynikaja z identyfikatora telefonu - zmiana identyfikatora zmienia je automatycznie (wlasne nadpisania czyscimy)
        prefs.lockEntity = ""; prefs.fullEntity = ""; prefs.todoEntity = ""; prefs.allowTodo = ""; prefs.rulesEntity = ""
        prefs.message = fields["msg"]!!.text.toString(); prefs.fullMessage = fields["fmsg"]!!.text.toString(); prefs.pin = fields["pin"]!!.text.toString()
        if (!prefs.configured) { Toast.makeText(this, "Podaj adres i token", Toast.LENGTH_SHORT).show(); return }
        stopService(Intent(this, GuardService::class.java))
        GuardService.start(this)
        Toast.makeText(this, "Zapisano, usługa uruchomiona", Toast.LENGTH_SHORT).show()
        statusCard.postDelayed({ refresh() }, 2500)
    }

    private fun refresh() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val acc = GuardAccessibilityService.instance != null
        val sync = if (prefs.lastSync > 0) java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(prefs.lastSync)) else "—"
        val notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val bat = pm.isIgnoringBatteryOptimizations(packageName)

        statusCard.removeAllViews()
        statusCard.addView(Ui.statusRow(this, "Połączenie z HA" + (if (prefs.urlInternal.isNotBlank() && prefs.urlMode.isNotBlank()) " (${prefs.urlMode})" else ""), if (prefs.connected) "ostatnia synchronizacja $sync" else "brak połączenia – sprawdź adres i token", prefs.connected))
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
        admSw?.let { if (it.isChecked != adminActive()) it.isChecked = adminActive() }
        if (dpm().isDeviceOwnerApp(packageName)) try { dpm().setUninstallBlocked(android.content.ComponentName(this, AdminReceiver::class.java), packageName, true) } catch (_: Exception) { }
        if (::statusCard.isInitialized) refresh()
    }
}

object BuildConfigVersion { const val NAME = "2.2" }
