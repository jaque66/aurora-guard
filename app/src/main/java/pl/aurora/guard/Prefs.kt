package pl.aurora.guard

import android.content.Context
import android.os.Build

/** Ustawienia aplikacji i ostatnio znane reguly z Home Assistanta (dzialaja tez offline). */
class Prefs(ctx: Context) {
    private val app = ctx.applicationContext
    private val p = app.getSharedPreferences("guard", Context.MODE_PRIVATE)

    /** Adres wewnetrzny HA (w domowej sieci Wi-Fi); puste = zawsze adres zewnetrzny (url). */
    var urlInternal: String
        get() = p.getString("urlInternal", "") ?: ""
        set(v) = p.edit().putString("urlInternal", v.trim().trimEnd('/')).apply()
    /** Nazwa (SSID) domowej sieci Wi-Fi. Puste = wykrywanie przez test dostepnosci adresu wewnetrznego. */
    var homeSsid: String
        get() = p.getString("homeSsid", "") ?: ""
        set(v) = p.edit().putString("homeSsid", v.trim()).apply()
    /** Ostatnio uzyty tryb: "wewnętrzne" / "zewnętrzne". */
    var urlMode: String
        get() = p.getString("urlMode", "") ?: ""
        set(v) = p.edit().putString("urlMode", v).apply()
    @Volatile private var probeAt = 0L
    @Volatile private var probeOk = false

    private fun onWifi(): Boolean = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: Exception) { false }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? = try {
        if (app.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) null
        else {
            val s = ((app.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo?.ssid ?: "").trim('"')
            if (s.isBlank() || s == "<unknown ssid>") null else s
        }
    } catch (_: Exception) { null }

    /** Test dostepnosci adresu wewnetrznego (dowolna odpowiedz HTTP = osiagalny). Tylko w watku roboczym; wynik na 60 s. */
    fun probeInternal(force: Boolean = false): Boolean {
        if (urlInternal.isBlank()) return false
        if (!force && System.currentTimeMillis() - probeAt < 60_000L) return probeOk
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return probeOk
        probeOk = try {
            val c = java.net.URL(urlInternal + "/api/").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 1500; c.readTimeout = 1500; c.requestMethod = "GET"
            c.responseCode; c.disconnect(); true
        } catch (_: Exception) { false }
        probeAt = System.currentTimeMillis()
        return probeOk
    }

    /** Adres, ktorego aplikacja uzywa teraz: wewnetrzny w domowym Wi-Fi, w przeciwnym razie zewnetrzny. */
    fun activeUrl(): String {
        if (urlInternal.isBlank()) { urlMode = ""; return url }
        val internal = if (!onWifi()) false else {
            val ssid = currentSsid()
            if (homeSsid.isNotBlank() && ssid != null) ssid.equals(homeSsid, ignoreCase = true) else probeInternal()
        }
        urlMode = if (internal) "wewnętrzne" else "zewnętrzne"
        return if (internal) urlInternal else url
    }

    var url: String
        get() = p.getString("url", "") ?: ""
        set(v) = p.edit().putString("url", v.trim().trimEnd('/')).apply()
    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) = p.edit().putString("token", v.trim()).apply()
    var deviceId: String
        get() = p.getString("device", "")?.ifBlank { null } ?: slug(Build.MODEL ?: "telefon")
        set(v) = p.edit().putString("device", slug(v)).apply()
    var lockEntity: String
        get() = p.getString("lockEntity", "")?.ifBlank { null } ?: "input_boolean.blokada_telefon_$deviceId"
        set(v) = p.edit().putString("lockEntity", v.trim()).apply()
    var todoEntity: String
        get() = p.getString("todoEntity", "")?.ifBlank { null } ?: "todo.guard_$deviceId"
        set(v) = p.edit().putString("todoEntity", v.trim()).apply()
    var fullEntity: String
        get() = p.getString("fullEntity", "")?.ifBlank { null } ?: "input_boolean.blokada_telefon_${deviceId}_caly"
        set(v) = p.edit().putString("fullEntity", v.trim()).apply()
    var allowTodo: String
        get() = p.getString("allowTodo", "")?.ifBlank { null } ?: "todo.guard_${deviceId}_dozwolone"
        set(v) = p.edit().putString("allowTodo", v.trim()).apply()
    var fullMessage: String
        get() = p.getString("fullMessage", null) ?: "Pora na przerwę od telefonu. Połączenia, wiadomości i kontakty nadal działają."
        set(v) = p.edit().putString("fullMessage", v).apply()
    /** Blokada calego telefonu (input_boolean ..._caly = on). */
    var fullLocked: Boolean
        get() = p.getBoolean("fullLocked", false)
        set(v) = p.edit().putBoolean("fullLocked", v).apply()
    /** Dodatkowe aplikacje dozwolone przy blokadzie calego telefonu (lista todo ..._dozwolone). */
    var allowed: Set<String>
        get() = p.getStringSet("allowed", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("allowed", v).apply()
    /** Identyfikator webhooka w HA (raporty aplikacji/statusu bez tokenu administratora). Puste = stary tryb przez REST (wymaga admina). */
    var webhook: String
        get() = p.getString("webhook", "") ?: ""
        set(v) = p.edit().putString("webhook", v.trim()).apply()
    /** Czujnik z regulami (atrybuty blocked/allowed) - czytelny takze dla zwyklego uzytkownika. */
    /** Wysylanie polozenia do HA (wlaczane w aplikacji, widoczne w powiadomieniu). */
    var locEnabled: Boolean
        get() = p.getBoolean("locEnabled", false)
        set(v) = p.edit().putBoolean("locEnabled", v).apply()
    var lastLocReport: Long
        get() = p.getLong("lastLocReport", 0L)
        set(v) = p.edit().putLong("lastLocReport", v).apply()
    /** Ustawienia sterowane z HA (jesli odpowiednie encje istnieja): czestotliwosc lokalizacji poza domem / w domu [min]. */
    var locAwayMin: Int
        get() = p.getInt("locAwayMin", 15)
        set(v) = p.edit().putInt("locAwayMin", v.coerceIn(5, 720)).apply()
    var locHomeMin: Int
        get() = p.getInt("locHomeMin", 60)
        set(v) = p.edit().putInt("locHomeMin", v.coerceIn(5, 1440)).apply()
    val setLocEntity: String get() = "input_boolean.guard_lokalizacja_$deviceId"
    val setAwayEntity: String get() = "input_number.guard_lok_poza_$deviceId"
    val setHomeEntity: String get() = "input_number.guard_lok_dom_$deviceId"
    val setMsgEntity: String get() = "input_text.guard_komunikat_$deviceId"
    val setFullMsgEntity: String get() = "input_text.guard_komunikat_caly_$deviceId"
    /** Przycisk w HA (input_button) - jego nacisniecie w Pulse prosi telefon o aktualna pozycje. */
    val locateEntity: String get() = "input_button.lokalizacja_telefon_$deviceId"

    var rulesEntity: String
        get() = p.getString("rulesEntity", "")?.ifBlank { null } ?: "sensor.aurora_guard_${deviceId}_rules"
        set(v) = p.edit().putString("rulesEntity", v.trim()).apply()
    /** Repozytorium z wydaniami (aktualizacje) i opcjonalny token GitHub (dla repozytorium prywatnego). */
    var repo: String
        get() = p.getString("repo", "")?.ifBlank { null } ?: "jaque66/aurora-guard"
        set(v) = p.edit().putString("repo", v.trim()).apply()
    var ghToken: String
        get() = p.getString("ghToken", "") ?: ""
        set(v) = p.edit().putString("ghToken", v.trim()).apply()
    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()
    var lastUpdateCheck: Long
        get() = p.getLong("lastUpdateCheck", 0L)
        set(v) = p.edit().putLong("lastUpdateCheck", v).apply()
    var pin: String
        get() = p.getString("pin", "") ?: ""
        set(v) = p.edit().putString("pin", v.trim()).apply()
    var message: String
        get() = p.getString("message", "") ?: "Ta aplikacja jest teraz zablokowana. Wykonaj zadania w domu, a rodzic ją odblokuje."
        set(v) = p.edit().putString("message", v).apply()

    /** Czy blokada jest teraz wlaczona (input_boolean w HA = on). */
    var locked: Boolean
        get() = p.getBoolean("locked", false)
        set(v) = p.edit().putBoolean("locked", v).apply()
    /** Pakiety z listy "do blokowania" (pozycje todo o statusie needs_action). */
    var blocked: Set<String>
        get() = p.getStringSet("blocked", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("blocked", v).apply()
    var suspended: Set<String>
        get() = p.getStringSet("suspended", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("suspended", v).apply()
    var connected: Boolean
        get() = p.getBoolean("connected", false)
        set(v) = p.edit().putBoolean("connected", v).apply()
    var lastSync: Long
        get() = p.getLong("lastSync", 0L)
        set(v) = p.edit().putLong("lastSync", v).apply()

    val configured: Boolean get() = url.isNotBlank() && token.isNotBlank()

    companion object {
        fun slug(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "telefon" }
    }
}
