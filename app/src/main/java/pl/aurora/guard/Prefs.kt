package pl.aurora.guard

import android.content.Context
import android.os.Build

/** Ustawienia aplikacji i ostatnio znane reguly z Home Assistanta (dzialaja tez offline). */
class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("guard", Context.MODE_PRIVATE)

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
