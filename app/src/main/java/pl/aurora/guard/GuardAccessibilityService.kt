package pl.aurora.guard

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Wykrywa aplikacje na pierwszym planie; zablokowana dostaje pelnoekranowa nakladke z komunikatem i powrot na ekran glowny. */
class GuardAccessibilityService : AccessibilityService() {
    private var lastKick = 0L
    private var overlay: View? = null
    private var lastShown = 0L

    override fun onServiceConnected() {
        instance = this
        if (Prefs(this).configured) { GuardService.start(this); Watchdog.schedule(this) }
    }

    private var fullView: View? = null
    private var current = ""

    /** Wywolywane po zmianie regul z HA (np. wlaczenie/wylaczenie blokady calego telefonu). */
    fun rulesChanged() {
        val prefs = Prefs(this)
        android.os.Handler(mainLooper).post {
            if (prefs.fullLocked) { if (current !in Allowed.all(this, prefs)) showFull(prefs) } else hideFull()
        }
    }

    private fun hideFull() {
        val v = fullView ?: return
        fullView = null
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) { }
    }

    private fun showFull(prefs: Prefs) {
        if (fullView != null) return
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding((28 * d).toInt(), (40 * d).toInt(), (28 * d).toInt(), (40 * d).toInt())
            background = Ui.background()
        }
        col.addView(TextView(this).apply {
            text = "🌙"; textSize = 44f; gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.PURPLE, Ui.PINK, Ui.CYAN)).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams((110 * d).toInt(), (110 * d).toInt()))
        col.addView(TextView(this).apply { text = "Telefon zablokowany"; textSize = 26f; setTextColor(Ui.INK); gravity = Gravity.CENTER; setPadding(0, (24 * d).toInt(), 0, 0) })
        col.addView(TextView(this).apply { text = prefs.fullMessage; textSize = 16f; setTextColor(Ui.INK2); gravity = Gravity.CENTER; setPadding(0, (16 * d).toInt(), 0, (28 * d).toInt()) })
        Allowed.launchable(this, prefs).forEach { (label, pkg) ->
            col.addView(Ui.primary(this, label) {
                val li = packageManager.getLaunchIntentForPackage(pkg)
                if (li != null) { hideFull(); li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li) }
            }.apply { textSize = 19f; setPadding((16 * d).toInt(), (20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt()) },
                LinearLayout.LayoutParams((280 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (30 * d).toInt() })
        }
        addRequest(col, d, "Telefon", "", "full")
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT)
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(col, lp); fullView = col } catch (_: Exception) { }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val prefs = Prefs(this)
        if (prefs.configured && !GuardService.running && System.currentTimeMillis() - lastKick > 30000) { lastKick = System.currentTimeMillis(); GuardService.start(this) }
        current = pkg
        if (prefs.fullLocked) {
            if (pkg == "com.android.systemui") return
            if (pkg in Allowed.core(this) || pkg in prefs.allowed) { hideFull(); return }
            if (pkg !in Allowed.exempt(this)) performGlobalAction(GLOBAL_ACTION_HOME)
            showFull(prefs)
            return
        }
        hideFull()
        if (Policy.shouldBlock(prefs, pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            showOverlay(prefs.message, pkg, "blocked")
            return
        }
        if (prefs.limitFor(pkg) != null) checkLimit(pkg)
    }

    private val limitTick = object : Runnable { override fun run() { checkLimit(current) } }

    /** Aplikacja z limitem czasu: po wyczerpaniu (limit + bonus od rodzica) blokada; w trakcie sprawdzanie co 20 s. */
    private fun checkLimit(pkg: String) {
        val handler = android.os.Handler(mainLooper); handler.removeCallbacks(limitTick)
        val prefs = Prefs(this)
        if (pkg != current || prefs.limitFor(pkg) == null) return
        val left = Policy.limitLeft(this, prefs, pkg) ?: return
        if (left <= 0) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            showOverlay("Dzienny limit czasu w tej aplikacji się skończył. Możesz poprosić rodzica o więcej czasu.", pkg, "limit")
            return
        }
        if (left in 1..5 && System.currentTimeMillis() - lastWarn > 120_000L) {
            lastWarn = System.currentTimeMillis()
            android.widget.Toast.makeText(this, "Zostało $left min w tej aplikacji", android.widget.Toast.LENGTH_LONG).show()
        }
        handler.postDelayed(limitTick, 20_000L)
    }
    private var lastWarn = 0L

    /** Okno z wiadomoscia (np. od rodzica) - zamykane przyciskiem OK. */
    fun showNote(emoji: String, title: String, msg: String) {
        hideOverlay()
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding((32 * d).toInt(), (32 * d).toInt(), (32 * d).toInt(), (32 * d).toInt()); background = Ui.background() }
        col.addView(TextView(this).apply { text = emoji; textSize = 44f; gravity = Gravity.CENTER })
        col.addView(TextView(this).apply { text = title; textSize = 22f; setTextColor(Ui.CYAN); gravity = Gravity.CENTER; setPadding(0, (16 * d).toInt(), 0, 0) })
        col.addView(TextView(this).apply { text = msg; textSize = 24f; setTextColor(Ui.INK); gravity = Gravity.CENTER; setPadding(0, (20 * d).toInt(), 0, (32 * d).toInt()) })
        col.addView(Ui.primary(this, "OK") { hideOverlay() }, LinearLayout.LayoutParams((220 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT)
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(col, lp); overlay = col; col.postDelayed({ hideOverlay() }, 60_000L) } catch (_: Exception) { }
    }

    private fun addRequest(col: LinearLayout, d: Float, name: String, pkg: String, reason: String) {
        val prefs = Prefs(this)
        if (prefs.webhook.isBlank()) return
        var btn: Button? = null
        btn = Ui.secondary(this, "Poproś o więcej czasu") {
            val b = btn ?: return@secondary
            if (System.currentTimeMillis() - prefs.lastReq < 180_000L) { b.text = "Prośba już wysłana"; return@secondary }
            prefs.lastReq = System.currentTimeMillis(); b.text = "Wysyłam…"
            Hook.request(this, name, pkg, reason) { ok -> b.post { b.text = if (ok) "Wysłano prośbę ✔" else "Nie udało się wysłać" } }
        }
        col.addView(btn, LinearLayout.LayoutParams((220 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (14 * d).toInt() })
    }

    private fun showOverlay(msg: String, pkg: String, reason: String = "blocked") {
        val now = System.currentTimeMillis()
        if (overlay != null || now - lastShown < 1500) return
        lastShown = now
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding((32 * d).toInt(), (32 * d).toInt(), (32 * d).toInt(), (32 * d).toInt())
            background = Ui.background()
        }
        col.addView(TextView(this).apply {
            text = "🔒"; textSize = 44f; gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.CYAN, Ui.PURPLE, Ui.PINK)).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams((110 * d).toInt(), (110 * d).toInt()))
        col.addView(TextView(this).apply { text = label; textSize = 26f; setTextColor(Ui.INK); gravity = Gravity.CENTER; setPadding(0, (24 * d).toInt(), 0, 0) })
        col.addView(TextView(this).apply { text = "zablokowana"; textSize = 13f; setTextColor(Ui.CYAN); letterSpacing = 0.2f; gravity = Gravity.CENTER; setPadding(0, (4 * d).toInt(), 0, 0) })
        col.addView(TextView(this).apply { text = msg; textSize = 16f; setTextColor(Ui.INK2); gravity = Gravity.CENTER; setPadding(0, (20 * d).toInt(), 0, (32 * d).toInt()) })
        col.addView(Ui.primary(this, "OK") { hideOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }, LinearLayout.LayoutParams((220 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        addRequest(col, d, label, pkg, reason)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT)
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(col, lp)
            overlay = col
            col.postDelayed({ hideOverlay() }, 12000)
        } catch (_: Exception) { }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) { }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (Prefs(this).configured) Hook.tamper(this, "Usługa dostępności Aurora Guard została wyłączona na telefonie")
        instance = null; hideOverlay(); hideFull()
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile var instance: GuardAccessibilityService? = null
    }
}
