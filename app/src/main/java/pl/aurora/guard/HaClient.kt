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
    private val http = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
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
                send(w, JSONObject().put("id", 1).put("type", "get_states"))
                send(w, JSONObject().put("id", 2).put("type", "subscribe_events").put("event_type", "state_changed"))
                requestTodo(w)
                send(w, JSONObject().put("id", 4).put("type", "todo/item/list").put("entity_id", prefs.allowTodo))
                Thread { reportApps(); reportStatus() }.start()
            }
            "auth_invalid" -> { prefs.connected = false; running = false }
            "result" -> {
                if (!m.optBoolean("success")) return
                when (m.optInt("id")) {
                    1 -> {
                        val arr = m.optJSONArray("result") ?: return
                        for (i in 0 until arr.length()) {
                            val s = arr.getJSONObject(i)
                            if (s.optString("entity_id") == prefs.lockEntity) setLock(s.optString("state") == "on")
                            if (s.optString("entity_id") == prefs.fullEntity) setFull(s.optString("state") == "on")
                            if (s.optString("entity_id") == prefs.rulesEntity) rules(s.optJSONObject("attributes"))
                        }
                    }
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
                        val set = HashSet<String>()
                        for (i in 0 until items.length()) {
                            val it = items.getJSONObject(i)
                            if (it.optString("status") == "needs_action") set.add(it.optString("summary").substringBefore("|").trim())
                        }
                        prefs.blocked = set; prefs.lastSync = System.currentTimeMillis(); onRules()
                    }
                }
            }
            "event" -> {
                val d = m.optJSONObject("event")?.optJSONObject("data") ?: return
                val id = d.optString("entity_id")
                if (id == prefs.lockEntity) setLock(d.optJSONObject("new_state")?.optString("state") == "on")
                else if (id == prefs.fullEntity) setFull(d.optJSONObject("new_state")?.optString("state") == "on")
                else if (id == prefs.rulesEntity) rules(d.optJSONObject("new_state")?.optJSONObject("attributes"))
                else if (id == prefs.todoEntity) requestTodo(w)
                else if (id == prefs.allowTodo) send(w, JSONObject().put("id", 4).put("type", "todo/item/list").put("entity_id", prefs.allowTodo))
            }
        }
    }

    private fun requestTodo(w: WebSocket) = send(w, JSONObject().put("id", 3).put("type", "todo/item/list").put("entity_id", prefs.todoEntity))

    /** Reguly z czujnika (atrybuty: blocked - pakiety do blokowania, allowed - dozwolone przy blokadzie telefonu). */
    private fun rules(a: JSONObject?) {
        if (a == null) return
        val b = a.optJSONArray("blocked"); val al = a.optJSONArray("allowed")
        if (b != null) { val s = HashSet<String>(); for (i in 0 until b.length()) s.add(b.optString(i).trim()); prefs.blocked = s }
        if (al != null) { val s = HashSet<String>(); for (i in 0 until al.length()) s.add(al.optString(i).trim()); prefs.allowed = s }
        prefs.lastSync = System.currentTimeMillis(); onRules()
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

    fun reportStatus() {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val acc = GuardAccessibilityService.instance != null
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date())
        if (prefs.webhook.isNotBlank()) {
            hook(JSONObject().put("type", "status").put("state", if (acc) "ok" else "brak_dostepnosci").put("accessibility", acc).put("locked", prefs.locked).put("full_locked", prefs.fullLocked)
                .put("blocked_count", prefs.blocked.size).put("device_owner", Policy.isDeviceOwner(ctx)).put("battery", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                .put("last_seen", iso).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("version", "1.9"))
            return
        }
        post("/api/states/sensor.aurora_guard_${prefs.deviceId}_status", JSONObject().put("state", if (acc) "ok" else "brak_dostepnosci")
            .put("attributes", JSONObject().put("accessibility", acc).put("locked", prefs.locked).put("full_locked", prefs.fullLocked).put("blocked_count", prefs.blocked.size)
                .put("device_owner", Policy.isDeviceOwner(ctx)).put("battery", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                .put("last_seen", iso).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE).put("version", "1.9")
                .put("friendly_name", "Aurora Guard – status (${prefs.deviceId})").put("icon", "mdi:shield-lock-outline")))
    }
}
