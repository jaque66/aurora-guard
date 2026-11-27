package pl.aurora.guard

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Polaczenie z Home Assistantem: WebSocket (reguly na zywo) + REST (raport aplikacji i statusu). */
class HaClient(private val ctx: Context, private val onRules: () -> Unit) {
    private val prefs = Prefs(ctx)
    private val http = OkHttpClient.Builder().pingInterval(90, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var ws: WebSocket? = null
    @Volatile private var running = false
    private var backoff = 3000L

    fun start() {
        if (running) return
        running = true
        connect()
    }

    /** Zmiana sieci: rozlacz i polacz ponownie (wybierze adres wewnetrzny lub zewnetrzny). */
    fun reconnect() {
        if (!running) return
        backoff = 3000L
        prefs.probeInternal(true)
        ws?.cancel()
    }

    fun stop() {
        running = false
        ws?.close(1000, null)
        ws = null
    }

    private fun connect() {
        if (!running || !prefs.configured) return
        val url = prefs.activeUrl().replaceFirst("http", "ws") + "/api/websocket"
        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try { handle(webSocket, JSONObject(text)) } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { lost() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { lost() }
        })
    }

    private fun lost() {
        prefs.connected = false
        if (!running) return
        main.postDelayed({ connect() }, backoff)
        backoff = minOf(backoff * 2, 120_000L)
    }

    private fun send(w: WebSocket, o: JSONObject) { w.send(o.toString()) }

    private fun handle(w: WebSocket, m: JSONObject) {
        when (m.optString("type")) {
            "auth_required" -> send(w, JSONObject().put("type", "auth").put("access_token", prefs.token))
            "auth_ok" -> {
                backoff = 3000L; prefs.connected = true
                ents.clear()
                send(w, JSONObject().put("id", 2).put("type", "subscribe_entities").put("entity_ids", JSONArray(watched())))   /* tylko nasze encje - bez lawiny zdarzen z calego HA */
                requestTodo(w)
                send(w, JSONObject().put("id", 4).put("type", "todo/item/list").put("entity_id", prefs.allowTodo))
                Thread { reportApps(); reportStatus() }.start()
            }
            "auth_invalid" -> { prefs.connected = false; running = false }
            "result" -> {
                if (!m.optBoolean("success")) return
                when (m.optInt("id")) {
                    4 -> {
                        val items = m.optJSONObject("result")?.optJSONArray("items") ?: return
                        val set = HashSet<String>()
                        for (i in 0 until items.length()) {
                            val it = items.getJSONObject(i)
                            if (it.optString("status") == "needs_action") set.add(it.optString("summary").substringBefore("|").trim())
                        }
                        prefs.allowed = set; onRules()
                    }
                    3 -> {
                        val items = m.optJSONObject("result")?.optJSONArray("items") ?: return
                        val set = HashSet<String>(); val lim = HashSet<String>()
                        for (i in 0 until items.length()) {
                            val it = items.getJSONObject(i)
                            if (it.optString("status") != "needs_action") continue
                            val q = it.optString("summary").split("|")
                            val mins = if (q.size >= 3) q[2].trim().toIntOrNull() else null
                            if (mins != null && mins > 0) lim.add(q[0].trim() + "|" + mins) else set.add(q[0].trim())
                        }
                        prefs.limits = lim
                        prefs.blocked = set; prefs.lastSync = System.currentTimeMillis(); onRules()
                    }
                }
            }
            "event" -> {
                val ev = m.optJSONObject("event") ?: return
                ev.optJSONObject("a")?.let { a -> a.keys().forEach { id -> val o = a.getJSONObject(id); onEnt(w, id, o.optString("s"), o.optJSONObject("a") ?: JSONObject(), true) } }
                ev.optJSONObject("c")?.let { c -> c.keys().forEach { id ->
                    val o = c.getJSONObject(id); val prev = ents[id]; var st = prev?.first ?: ""; val at = prev?.second ?: JSONObject()
                    o.optJSONObject("+")?.let { p -> if (p.has("s")) st = p.optString("s"); p.optJSONObject("a")?.let { pa -> pa.keys().forEach { k -> at.put(k, pa.get(k)) } } }
                    o.optJSONObject("-")?.optJSONArray("a")?.let { rm -> for (i in 0 until rm.length()) at.remove(rm.optString(i)) }
                    onEnt(w, id, st, at, false)
                } }
            }
        }
    }

    private val ents = HashMap<String, Pair<String, JSONObject>>()
    private fun watched(): List<String> = listOf(prefs.lockEntity, prefs.fullEntity, prefs.rulesEntity, prefs.todoEntity, prefs.allowTodo, prefs.locateEntity, prefs.ringEntity,
        prefs.setLocEntity, prefs.setAwayEntity, prefs.setHomeEntity, prefs.setMsgEntity, prefs.setFullMsgEntity, prefs.bonusEntity, prefs.kidMsgEntity)

    /** Zmiana (lub stan poczatkowy) obserwowanej encji. Przyciski (znajdz telefon / lokalizacja) reaguja tylko na nowe nacisniecia. */
    private fun onEnt(w: WebSocket, id: String, st: String, at: JSONObject, initial: Boolean) {
        ents[id] = st to at
        applySetting(id, st)
        when (id) {
            prefs.lockEntity -> setLock(st == "on")
            prefs.fullEntity -> setFull(st == "on")
            prefs.rulesEntity -> rules(at)
            prefs.locateEntity -> if (!initial) reportLocation("zadanie")
            prefs.ringEntity -> if (!initial) Alarm.toggle(ctx)
            prefs.todoEntity -> if (!initial) requestTodo(w)
            prefs.allowTodo -> if (!initial) send(w, JSONObject().put("id", 4).put("type", "todo/item/list").put("entity_id", prefs.allowTodo))
        }
    }

    private fun requestTodo(w: WebSocket) = send(w, JSONObject().put("id", 3).put("type", "todo/item/list").put("entity_id", prefs.todoEntity))

    /** Reguly z czujnika (atrybuty: blocked - pakiety do blokowania, allowed - dozwolone przy blokadzie telefonu). */
    private fun rules(a: JSONObject?) {
        if (a == null) return
        val b = a.optJSONArray("blocked"); val al = a.optJSONArray("allowed")
        if (b != null) { val s = HashSet<String>(); for (i in 0 until b.length()) s.add(b.optString(i).trim()); prefs.blocked = s }
        if (al != null) { val s = HashSet<String>(); for (i in 0 until al.length()) s.add(al.optString(i).trim()); prefs.allowed = s }
        val lm = a.optJSONArray("limits"); if (lm != null) { val s = HashSet<String>(); for (i in 0 until lm.length()) s.add(lm.optString(i).trim()); prefs.limits = s }
        prefs.lastSync = System.currentTimeMillis(); onRules()
    }

    /** Ustawienia aplikacji sterowane z HA (helpery guard_*); brak encji = ustawienia lokalne bez zmian. */
    private fun applySetting(id: String, st: String) {
        if (st.isBlank() || st == "unavailable" || st == "unknown") return
        when (id) {
            prefs.setLocEntity -> { val on = st == "on"; if (on != prefs.locEnabled) { prefs.locEnabled = on; GuardService.start(ctx) } }
            prefs.setAwayEntity -> st.toDoubleOrNull()?.let { prefs.locAwayMin = it.toInt() }
            prefs.setHomeEntity -> st.toDoubleOrNull()?.let { prefs.locHomeMin = it.toInt() }
            prefs.bonusEntity -> st.toDoubleOrNull()?.let { prefs.bonusMin = it.toInt() }
            prefs.kidMsgEntity -> {
                val ts = st.substringBefore("|"); val txt = st.substringAfter("|", "").trim()
                val age = System.currentTimeMillis() / 1000 - (ts.toLongOrNull() ?: 0L)
                if (txt.isNotBlank() && ts != prefs.lastMsgStamp) { prefs.lastMsgStamp = ts; if (age in 0..900) Notifier.parentMessage(ctx, txt) }
            }
            prefs.setMsgEntity -> prefs.message = st
            prefs.setFullMsgEntity -> prefs.fullMessage = st
        }
    }

    private fun setLock(on: Boolean) { prefs.locked = on; onRules() }
    private fun setFull(on: Boolean) { prefs.fullLocked = on; onRules() }

    private fun post(path: String, body: JSONObject): Boolean = try {
        val req = Request.Builder().url(prefs.activeUrl() + path).header("Authorization", "Bearer " + prefs.token)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) { false }

    private fun hook(body: JSONObject): Boolean = try {
        val req = Request.Builder().url(prefs.activeUrl() + "/api/webhook/" + prefs.webhook).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) { false }

    /** Wysyla polozenie przez webhook (HA zapisuje je jako device_tracker). */
    fun reportLocation(reason: String) {
        if (!prefs.locEnabled || prefs.webhook.isBlank()) return
        Loc.get(ctx, reason == "zadanie") { l ->
            if (l == null) return@get
            Thread {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                if (hook(JSONObject().put("type", "location").put("lat", l.latitude).put("lon", l.longitude).put("acc", l.accuracy.toInt()).put("time", l.time)
                        .put("reason", reason).put("battery", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)))) prefs.lastLocReport = System.currentTimeMillis()
            }.start()
        }
    }

    /** Raport czasu w aplikacjach (co ~15 min) + wykrywanie cofniecia uprawnien (ostrzezenie dla rodzica). */
    fun maybeUsage() {
        val acc = Usage.hasAccess(ctx)
        if (prefs.permUsageOk != acc) { if (!acc) Hook.tamper(ctx, "Cofnięto dostęp do danych o użyciu aplikacji (limity czasu przestaną działać)"); prefs.permUsageOk = acc }
        if (prefs.locEnabled) {
            val loc = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (prefs.permLocOk != loc) { if (!loc) Hook.tamper(ctx, "Cofnięto uprawnienie lokalizacji"); prefs.permLocOk = loc }
        }
        if (!acc || prefs.webhook.isBlank() || System.currentTimeMillis() - prefs.lastUsageReport < 27 * 60_000L) return
        try {
            val (apps, total, _) = Usage.report(ctx)
            val days = Usage.days(ctx)
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val week = (6 downTo 0).map { b -> val d = fmt.format(Date(System.currentTimeMillis() - b * 86_400_000L)); days.firstOrNull { it.first == d }?.second ?: 0 }
            val arr = JSONArray(); apps.forEach { arr.put(JSONObject().put("p", it.first).put("n", it.second).put("m", it.third)) }
            val wk = JSONArray(); week.forEach { wk.put(it) }
            val dl = JSONArray()
            days.forEach { d -> val da = JSONArray(); d.third.forEach { da.put(JSONObject().put("n", it.second).put("m", it.third)) }; dl.put(JSONObject().put("d", d.first).put("t", d.second).put("a", da)) }
            if (hook(JSONObject().put("type", "usage").put("apps", arr).put("total", total).put("week", wk).put("days", dl))) prefs.lastUsageReport = System.currentTimeMillis()
        } catch (_: Exception) { }
    }

    /** Okresowo: poza domem co 15 min, w domowym Wi-Fi co godzine. */
    fun maybeLocate() {
        if (!prefs.locEnabled) return
        val home = prefs.urlInternal.isNotBlank() && prefs.urlMode == "wewnętrzne"
        val every = (if (home) prefs.locHomeMin else prefs.locAwayMin) * 60_000L
        if (System.currentTimeMillis() - prefs.lastLocReport >= every) reportLocation("auto")
    }

    /** Lista aplikacji z ikona w launcherze - w Pulse zaznaczasz z niej, co ma byc blokowane. */
    fun reportApps() {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(q, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString().take(40) }
            .distinctBy { it.first }.filter { it.first != ctx.packageName }.sortedBy { it.second.lowercase() }.take(250)
        val arr = JSONArray()
        apps.forEach { arr.put(JSONObject().put("p", it.first).put("n", it.second)) }
        if (prefs.webhook.isNotBlank()) { hook(JSONObject().put("type", "apps").put("apps", arr)); return }
        post("/api/states/sensor.aurora_guard_${prefs.deviceId}_apps", JSONObject().put("state", apps.size.toString())
            .put("attributes", JSONObject().put("apps", arr).put("friendly_name", "Aurora Guard – aplikacje (${prefs.deviceId})").put("icon", "mdi:apps")))
    }

    private var lastStatus = 0L
    /** Raport statusu (heartbeat) co ~10 min zamiast co 5 - mniej wybudzen radia. */
    fun maybeStatus() { if (System.currentTimeMillis() - lastStatus < 9 * 60_000L) return; reportStatus() }

    fun reportStatus() {
        lastStatus = System.currentTimeMillis()
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val acc = GuardAccessibilityService.instance != null
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date())
        if (prefs.webhook.isNotBlank()) {
            hook(JSONObject().put("type", "status").put("state", if (acc) "ok" else "brak_dostepnosci").put("accessibility", acc).put("locked", prefs.locked).put("full_locked", prefs.fullLocked)
                .put("blocked_count", prefs.blocked.size).put("device_owner", Policy.isDeviceOwner(ctx)).put("battery", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                .put("last_seen", iso).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("version", "2.4"))
            return
        }
        post("/api/states/sensor.aurora_guard_${prefs.deviceId}_status", JSONObject().put("state", if (acc) "ok" else "brak_dostepnosci")
            .put("attributes", JSONObject().put("accessibility", acc).put("locked", prefs.locked).put("full_locked", prefs.fullLocked).put("blocked_count", prefs.blocked.size)
                .put("device_owner", Policy.isDeviceOwner(ctx)).put("battery", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                .put("last_seen", iso).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("version", "2.4")
                .put("friendly_name", "Aurora Guard – status (${prefs.deviceId})").put("icon", "mdi:shield-lock-outline")))
    }
}
