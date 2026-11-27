package pl.aurora.guard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** Jednorazowe pobranie polozenia bez uslug Google. Automatyczne raporty: najpierw siec (tanio), GPS tylko gdy dokladnosc jest slaba; na zadanie rodzica (precise): GPS, potem siec. */
object Loc {
    @Suppress("DEPRECATION")
    fun get(ctx: Context, precise: Boolean, cb: (Location?) -> Unit) {
        val app = ctx.applicationContext
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { cb(null); return }
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val main = Handler(Looper.getMainLooper())
        fun on(p: String) = try { lm.isProviderEnabled(p) } catch (_: Exception) { false }
        val gps = on(LocationManager.GPS_PROVIDER); val net = on(LocationManager.NETWORK_PROVIDER)
        fun last(): Location? = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: Exception) { null } }.maxByOrNull { it.time }
        fun one(prov: String, timeoutMs: Long, done: (Location?) -> Unit) {
            var fin = false
            val ls = object : LocationListener {
                override fun onLocationChanged(l: Location) { if (!fin) { fin = true; done(l) } }
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) { }
                override fun onProviderEnabled(p: String) { }
                override fun onProviderDisabled(p: String) { }
            }
            try { lm.requestSingleUpdate(prov, ls, Looper.getMainLooper()) } catch (_: Exception) { fin = true; done(null); return }
            main.postDelayed({ if (!fin) { fin = true; try { lm.removeUpdates(ls) } catch (_: Exception) { }; done(null) } }, timeoutMs)
        }
        if (!gps && !net) { cb(last()); return }
        if (precise) {
            if (gps) one(LocationManager.GPS_PROVIDER, 30_000L) { g -> if (g != null) cb(g) else if (net) one(LocationManager.NETWORK_PROVIDER, 8_000L) { n -> cb(n ?: last()) } else cb(last()) }
            else one(LocationManager.NETWORK_PROVIDER, 8_000L) { n -> cb(n ?: last()) }
        } else {
            if (net) one(LocationManager.NETWORK_PROVIDER, 8_000L) { n ->
                if (n != null && n.accuracy <= 500f) cb(n)
                else if (gps) one(LocationManager.GPS_PROVIDER, 25_000L) { g -> cb(g ?: n ?: last()) } else cb(n ?: last())
            } else one(LocationManager.GPS_PROVIDER, 25_000L) { g -> cb(g ?: last()) }
        }
    }
}
