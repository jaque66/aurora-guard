package pl.aurora.guard

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Aktualizator: sprawdza najnowsze wydanie na GitHubie, pobiera APK i uruchamia instalator systemu (potwierdzenie przez uzytkownika). */
object Updater {
    data class Info(val version: String, val browserUrl: String, val apiUrl: String, val notes: String)

    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private fun parts(v: String): List<Int> = v.trim().trimStart('v', 'V').split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) { val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }; if (x != y) return x > y }
        return false
    }

    /** Najnowsze wydanie albo wyjatek z czytelnym komunikatem. */
    fun latest(prefs: Prefs): Info {
        val rb = Request.Builder().url("https://api.github.com/repos/${prefs.repo}/releases/latest").header("Accept", "application/vnd.github+json")
        if (prefs.ghToken.isNotBlank()) rb.header("Authorization", "Bearer " + prefs.ghToken)
        http.newCall(rb.build()).execute().use { r ->
            if (r.code == 404) throw IOException(if (prefs.ghToken.isBlank()) "Brak dostępu do wydań (repozytorium prywatne?) – podaj token GitHub w ustawieniach" else "Nie znaleziono wydań w ${prefs.repo}")
            if (!r.isSuccessful) throw IOException("GitHub: HTTP ${r.code}")
            val j = JSONObject(r.body!!.string()); val assets = j.optJSONArray("assets")
            var a: JSONObject? = null
            if (assets != null) for (i in 0 until assets.length()) { val o = assets.getJSONObject(i); if (o.optString("name").endsWith(".apk")) { a = o; break } }
            if (a == null) throw IOException("Wydanie ${j.optString("tag_name")} nie zawiera pliku APK")
            return Info(j.optString("tag_name").trimStart('v', 'V'), a.optString("browser_download_url"), a.optString("url"), j.optString("body"))
        }
    }

    /** Pobiera APK do katalogu aplikacji; onProgress(0..100). */
    fun download(ctx: Context, prefs: Prefs, info: Info, onProgress: (Int) -> Unit): File {
        val useApi = prefs.ghToken.isNotBlank()
        val rb = Request.Builder().url(if (useApi) info.apiUrl else info.browserUrl)
        if (useApi) rb.header("Authorization", "Bearer " + prefs.ghToken).header("Accept", "application/octet-stream")
        val dir = ctx.getExternalFilesDir(null) ?: ctx.cacheDir
        val f = File(dir, "update.apk"); if (f.exists()) f.delete()
        http.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Pobieranie nieudane: HTTP ${r.code}")
            val body = r.body!!; val total = body.contentLength(); var got = 0L
            body.byteStream().use { inp -> f.outputStream().use { out -> val buf = ByteArray(32768); while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); got += n; if (total > 0) onProgress((got * 100 / total).toInt()) } } }
        }
        return f
    }

    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
