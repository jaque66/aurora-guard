package pl.aurora.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** "Znajdz telefon": glosny dzwonek alarmu (kanal alarmu, maksymalna glosnosc) do 90 s lub do wyciszenia. */
object Alarm {
    private var mp: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())

    fun toggle(ctx: Context) { if (mp != null) stop(ctx) else start(ctx) }

    fun start(ctx: Context) = h.post {
        if (mp != null) return@post
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val m = MediaPlayer()
            m.setDataSource(ctx, uri)
            m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            m.isLooping = true; m.prepare(); m.start(); mp = m
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("alarm", "Znajdź telefon", NotificationManager.IMPORTANCE_HIGH))
            val stop = PendingIntent.getBroadcast(ctx, 3, Intent(ctx, StopAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(31, Notification.Builder(ctx, "alarm").setSmallIcon(R.drawable.ic_stat_guard).setContentTitle("Rodzic szuka tego telefonu").setContentText("Dotknij, aby wyciszyć")
                .setContentIntent(stop).setAutoCancel(true).addAction(Notification.Action.Builder(null, "Wycisz", stop).build()).build())
            h.postDelayed({ stop(ctx) }, 90_000L)
        } catch (_: Exception) { mp = null }
    }

    fun stop(ctx: Context) = h.post {
        try { mp?.stop(); mp?.release() } catch (_: Exception) { }
        mp = null
        try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(31) } catch (_: Exception) { }
    }
}

class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { Alarm.stop(ctx) }
}
