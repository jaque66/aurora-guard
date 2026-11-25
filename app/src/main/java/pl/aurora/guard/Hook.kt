package pl.aurora.guard

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Jednorazowe wyslanie zdarzenia do HA przez webhook (prosby, ostrzezenia o ingerencji). */
object Hook {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    fun post(ctx: Context, body: JSONObject, cb: ((Boolean) -> Unit)? = null) {
        Thread {
            val p = Prefs(ctx)
            var ok = false
            if (p.webhook.isNotBlank() && p.url.isNotBlank()) try {
                val req = Request.Builder().url(p.activeUrl() + "/api/webhook/" + p.webhook).post(body.toString().toRequestBody("application/json".toMediaType())).build()
                http.newCall(req).execute().use { ok = it.isSuccessful }
            } catch (_: Exception) { }
            cb?.invoke(ok)
        }.start()
    }

    fun request(ctx: Context, name: String, pkg: String, reason: String, cb: ((Boolean) -> Unit)? = null) =
        post(ctx, JSONObject().put("type", "request").put("name", name).put("pkg", pkg).put("reason", reason), cb)

    fun tamper(ctx: Context, what: String) = post(ctx, JSONObject().put("type", "tamper").put("what", what))
}
