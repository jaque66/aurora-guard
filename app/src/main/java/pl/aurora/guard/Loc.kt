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

/** Jednorazowe pobranie polozenia (bez uslug Google): najpierw GPS, po 25 s siec komorkowa/Wi-Fi, na koncu ostatnia znana pozycja. */
object Loc {
    @Suppress("DEPRECATION")
    fun get(ctx: Context, cb: (Location?) -> Unit) {
        val app = ctx.applicationContext
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { cb(null); return }
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val main = Handler(Looper.getMainLooper())
        val order = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { try { lm.isProviderEnabled(it) } catch (_: Exception) { false } }
        fun last(): Location? = order.mapNotNull { try { lm.getLastKnownLocation(it) } catch (_: Exception) { null } }.maxByOrNull { it.time }
        if (order.isEmpty()) { cb(last()); return }
        var done = false
        val listeners = ArrayList<LocationListener>()
        fun finish(l: Location?) {
            if (done) return; done = true
            listeners.forEach { try { lm.removeUpdates(it) } catch (_: Exception) { } }
            cb(l ?: last())
        }
        order.forEach { prov ->
            val ls = object : LocationListener {
                override fun onLocationChanged(l: Location) { finish(l) }
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) { }
                override fun onProviderEnabled(p: String) { }
                override fun onProviderDisabled(p: String) { }
            }
            listeners.add(ls)
            try { lm.requestSingleUpdate(prov, ls, Looper.getMainLooper()) } catch (_: Exception) { }
        }
        main.postDelayed({ finish(null) }, 40_000L)
    }
}
