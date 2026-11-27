package pl.aurora.guard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Podglad dziennika aplikacji: odswiez, kopiuj (do wyslania), wyczysc. */
class LogActivity : Activity() {
    private lateinit var tv: TextView
    private lateinit var sv: ScrollView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = android.graphics.Color.parseColor("#060A18")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A1230")
        val p = Ui.dp(this, 16)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, Ui.dp(this@LogActivity, 28), p, p); background = Ui.background() }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@LogActivity).apply { text = "‹"; textSize = 34f; setTextColor(Ui.INK); setPadding(0, 0, Ui.dp(this@LogActivity, 14), Ui.dp(this@LogActivity, 4)); setOnClickListener { finish() } })
            addView(TextView(this@LogActivity).apply { text = "Logi aplikacji"; textSize = 26f; setTextColor(Ui.INK); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        })
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(t: String, f: () -> Unit) = Ui.secondary(this, t) { f() }.also { bar.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = Ui.dp(this@LogActivity, 6) }) }
        btn("Odśwież") { load() }
        btn("Kopiuj") {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Aurora Guard – logi", header() + AppLog.read(this)))
            Toast.makeText(this, "Skopiowano do schowka", Toast.LENGTH_SHORT).show()
        }
        btn("Wyczyść") { AppLog.clear(this); AppLog.i(this, "app", "dziennik wyczyszczony"); load() }
        root.addView(bar)
        tv = TextView(this).apply { textSize = 11f; setTextColor(Ui.INK); typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(0, Ui.dp(this@LogActivity, 12), 0, 0) }
        sv = ScrollView(this).apply { addView(tv) }
        root.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        load()
    }

    private fun header() = "Aurora Guard v${BuildConfigVersion.NAME}, Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MODEL}\n"

    private fun load() {
        val t = AppLog.read(this)
        tv.text = if (t.isBlank()) "Dziennik jest pusty." else t
        sv.post { sv.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}
