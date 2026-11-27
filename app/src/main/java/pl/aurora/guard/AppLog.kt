package pl.aurora.guard

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dziennik aplikacji (plik guard.log w pamieci aplikacji, ok. 200 KB): zdarzenia uslug, polaczenia z HA i awarie. Do podgladu w Ustawienia -> Logi. */
object AppLog {
    private const val MAX = 200_000L
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized private fun write(ctx: Context, line: String) {
        try {
            val f = File(ctx.applicationContext.filesDir, "guard.log")
            if (f.length() > MAX) { val t = f.readText(); f.writeText(t.substring(t.length / 2).substringAfter('\n')) }
            f.appendText(fmt.format(Date()) + " " + line + "\n")
        } catch (_: Throwable) { }
    }

    fun i(ctx: Context, tag: String, msg: String) = write(ctx, "I/$tag $msg")
    fun w(ctx: Context, tag: String, msg: String) = write(ctx, "W/$tag $msg")
    fun e(ctx: Context, tag: String, msg: String, t: Throwable? = null) =
        write(ctx, "E/$tag $msg" + (if (t != null) "\n" + android.util.Log.getStackTraceString(t).trim().lines().take(14).joinToString("\n") { "    $it" } else ""))

    @Synchronized fun read(ctx: Context): String = try { File(ctx.filesDir, "guard.log").takeIf { it.exists() }?.readText() ?: "" } catch (_: Throwable) { "" }
    @Synchronized fun clear(ctx: Context) { try { File(ctx.filesDir, "guard.log").delete() } catch (_: Throwable) { } }

    /** Nieobsluzone wyjatki trafiaja do dziennika przed zamknieciem procesu. */
    fun installCrashHandler(app: Application) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            e(app, "CRASH", "watek ${th.name}: ${ex.javaClass.simpleName}: ${ex.message}", ex)
            prev?.uncaughtException(th, ex)
        }
    }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.installCrashHandler(this)
        AppLog.i(this, "app", "proces ${android.os.Process.myPid()} start, v${BuildConfigVersion.NAME}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MODEL}")
    }
}
