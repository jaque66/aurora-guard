package pl.aurora.guard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Osobne okno aktualizacji: sprawdzanie i instalacja nowej wersji z GitHuba oraz ustawienia aktualizatora. */
class UpdateActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var card: LinearLayout
    private lateinit var repo: EditText
    private lateinit var token: EditText
    private lateinit var auto: Switch

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        window.statusBarColor = android.graphics.Color.parseColor("#060A18")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A1230")
        if (prefs.pin.isNotEmpty()) askPin() else build()
    }

    private fun askPin() {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "PIN rodzica" }
        AlertDialog.Builder(this).setTitle("Aurora Guard").setView(et).setCancelable(false)
            .setPositiveButton("OK") { _, _ -> if (et.text.toString() == prefs.pin) build() else { Toast.makeText(this, "Zły PIN", Toast.LENGTH_SHORT).show(); finish() } }
            .setNegativeButton("Anuluj") { _, _ -> finish() }.show()
    }

    private fun build() {
        val d = Ui.dp(this, 18)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d, Ui.dp(this@UpdateActivity, 28), d, Ui.dp(this@UpdateActivity, 40)) }
        root.addView(TextView(this).apply { text = "Aktualizacje"; textSize = 26f; setTextColor(Ui.INK); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        root.addView(TextView(this).apply { text = "Aurora Guard v${BuildConfigVersion.NAME}"; textSize = 13f; setTextColor(Ui.INK2) })
        root.addView(Ui.section(this, "Wersja"))
        card = Ui.card(this); root.addView(card)

        root.addView(Ui.section(this, "Ustawienia aktualizacji"))
        val form = Ui.card(this)
        form.addView(Ui.label(this, "Repozytorium GitHub z wydaniami (właściciel/nazwa)"))
        repo = Ui.input(this, prefs.repo); form.addView(repo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        form.addView(Ui.label(this, "Token GitHub (tylko dla repozytorium prywatnego; uprawnienie Contents: read)"))
        token = Ui.input(this, prefs.ghToken, secret = true); form.addView(token, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        auto = Switch(this).apply { text = "Sprawdzaj automatycznie raz na dobę i powiadamiaj o nowej wersji"; setTextColor(Ui.INK); textSize = 14f; isChecked = prefs.autoUpdate; setPadding(0, Ui.dp(this@UpdateActivity, 16), 0, 0) }
        form.addView(auto)
        form.addView(Ui.primary(this, "Zapisz ustawienia") { saveSettings(); Toast.makeText(this, "Zapisano", Toast.LENGTH_SHORT).show() })
        root.addView(form)
        root.addView(Ui.secondary(this, "Historia zmian") { showChangelog() })

        setContentView(ScrollView(this).apply { background = Ui.background(); isFillViewport = true; addView(root) })
        render(false)
        checkUpdate()
    }

    private fun saveSettings() { prefs.repo = repo.text.toString(); prefs.ghToken = token.text.toString(); prefs.autoUpdate = auto.isChecked }

    private fun render(busy: Boolean, info: Updater.Info? = null, msg: String? = null, progress: Int = -1) {
        card.removeAllViews()
        card.addView(Ui.statusRow(this, "Zainstalowana: ${BuildConfigVersion.NAME}", msg ?: if (info != null) "dostępna nowa wersja ${info.version}" else "sprawdź, czy jest nowsza", if (info != null) null else true))
        if (info != null && info.notes.isNotBlank()) card.addView(TextView(this).apply { text = info.notes.replace("**", "").take(700); textSize = 12f; setTextColor(Ui.INK2); setPadding(0, Ui.dp(this@UpdateActivity, 8), 0, 0) })
        if (progress in 0..100) card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; this.progress = progress })
        if (info != null) card.addView(Ui.primary(this, "Pobierz i zainstaluj ${info.version}") { startUpdate(info) })
        else card.addView(Ui.secondary(this, if (busy) "Sprawdzanie…" else "Sprawdź aktualizację") { saveSettings(); checkUpdate() })
    }

    private fun checkUpdate() {
        render(true)
        Thread {
            try {
                val info = Updater.latest(prefs); prefs.lastUpdateCheck = System.currentTimeMillis()
                runOnUiThread { if (Updater.isNewer(info.version, BuildConfigVersion.NAME)) render(false, info) else render(false, null, "masz najnowszą wersję") }
            } catch (e: Exception) { runOnUiThread { render(false, null, e.message ?: "błąd sprawdzania") } }
        }.start()
    }

    private fun startUpdate(info: Updater.Info) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "Zezwól Aurora Guard na instalowanie aplikacji i wróć", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))); return
        }
        render(false, null, "pobieranie ${info.version}…", 0)
        Thread {
            try {
                val f = Updater.download(this, prefs, info) { p -> runOnUiThread { render(false, null, "pobieranie ${info.version}… $p%", p) } }
                runOnUiThread { render(false, null, "pobrano – potwierdź instalację"); Updater.install(this, f) }
            } catch (e: Exception) { runOnUiThread { render(false, info, "błąd: ${e.message}") } }
        }.start()
    }

    private fun showChangelog() {
        val txt = try { assets.open("changelog.md").bufferedReader(Charsets.UTF_8).readText() } catch (_: Exception) { "Brak pliku historii zmian." }
        val tv = TextView(this).apply { text = txt.replace("**", "").replace("### ", "").replace("## ", "").replace("# ", ""); textSize = 13f; setTextColor(Ui.INK); setPadding(Ui.dp(this@UpdateActivity, 20), Ui.dp(this@UpdateActivity, 12), Ui.dp(this@UpdateActivity, 20), Ui.dp(this@UpdateActivity, 12)) }
        AlertDialog.Builder(this).setTitle("Historia zmian").setView(ScrollView(this).apply { addView(tv) }).setPositiveButton("Zamknij", null).show()
    }
}
