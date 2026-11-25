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
            }, LinearLayout.LayoutParams((260 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
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
            showOverlay(prefs.message, pkg)
        }
    }

    private fun showOverlay(msg: String, pkg: String) {
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
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT)
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(col, lp)
            overlay = col
            col.postDelayed({ hideOverlay() }, 6000)
        } catch (_: Exception) { }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) { }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null; hideOverlay(); hideFull()
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile var instance: GuardAccessibilityService? = null
    }
}
