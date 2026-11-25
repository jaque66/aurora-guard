package pl.aurora.guard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Wspolny wyglad "Aurora": ciemny granat, turkus -> fiolet -> roz, zaokraglone karty. */
object Ui {
    val CYAN = Color.parseColor("#67E8F9")
    val PURPLE = Color.parseColor("#A78BFA")
    val PINK = Color.parseColor("#F472B6")
    val INK = Color.parseColor("#EEF2FF")
    val INK2 = Color.parseColor("#94A3C4")
    val OK = Color.parseColor("#34D399")
    val BAD = Color.parseColor("#FB7185")
    val WARN = Color.parseColor("#FBBF24")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun background(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#060A18"), Color.parseColor("#0A1230"), Color.parseColor("#170C30")))

    private fun round(ctx: Context, fill: Int, radius: Int, stroke: Int = 0, strokeW: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill); cornerRadius = dp(ctx, radius).toFloat()
            if (stroke != 0) setStroke(dp(ctx, strokeW), stroke)
        }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        background = round(ctx, Color.parseColor("#14FFFFFF"), 20, Color.parseColor("#22FFFFFF"))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, 12) }
    }

    fun section(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text.uppercase(); textSize = 12f; setTextColor(INK2); letterSpacing = 0.14f; typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(ctx, 4), dp(ctx, 18), 0, dp(ctx, 8))
    }

    fun label(ctx: Context, text: String): TextView = TextView(ctx).apply {
        this.text = text; textSize = 12f; setTextColor(INK2); setPadding(dp(ctx, 2), dp(ctx, 12), 0, dp(ctx, 4))
    }

    fun input(ctx: Context, value: String, secret: Boolean = false, number: Boolean = false): EditText = EditText(ctx).apply {
        setText(value); setSingleLine(); setTextColor(INK); setHintTextColor(INK2); textSize = 15f
        background = round(ctx, Color.parseColor("#1AFFFFFF"), 12, Color.parseColor("#26FFFFFF"))
        setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        if (secret) inputType = (if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT) or (if (number) InputType.TYPE_NUMBER_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
    }

    fun primary(ctx: Context, text: String, onClick: () -> Unit): Button = Button(ctx).apply {
        this.text = text; isAllCaps = false; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#07131F")); stateListAnimator = null
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, PURPLE)).apply { cornerRadius = dp(ctx, 14).toFloat() }
        setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 18); bottomMargin = dp(ctx, 4) }
        setOnClickListener { onClick() }
    }

    fun secondary(ctx: Context, text: String, onClick: () -> Unit): Button = Button(ctx).apply {
        this.text = text; isAllCaps = false; textSize = 14f; setTextColor(CYAN); stateListAnimator = null
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(ctx, 14).toFloat(); setStroke(dp(ctx, 1), CYAN) }
        setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 8) }
        setOnClickListener { onClick() }
    }

    /** Wiersz z kropka statusu; klikniecie (opcjonalne) otwiera odpowiednie ustawienie. */
    fun statusRow(ctx: Context, title: String, detail: String, state: Boolean?, onClick: (() -> Unit)? = null): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(ctx, 8), 0, dp(ctx, 8))
        val col = when (state) { true -> OK; false -> BAD; null -> WARN }
        addView(TextView(ctx).apply {
            text = when (state) { true -> "✓"; false -> "✕"; null -> "•" }; textSize = 16f; setTextColor(Color.parseColor("#07131F")); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col) }
        }, LinearLayout.LayoutParams(dp(ctx, 26), dp(ctx, 26)))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 12), 0, 0, 0)
            addView(TextView(ctx).apply { text = title; textSize = 15f; setTextColor(INK) })
            if (detail.isNotBlank()) addView(TextView(ctx).apply { text = detail; textSize = 12f; setTextColor(INK2) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (onClick != null) {
            addView(TextView(ctx).apply { text = "›"; textSize = 22f; setTextColor(INK2) })
            isClickable = true; setOnClickListener { onClick() }
        }
    }
}
