package com.ccslay.safecircles

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import com.ccslay.safecircles.zone.LocationCircle
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay

class ZoneManager(
    private val context: Context,
    private val map: MapView
) {

    private val zones = mutableListOf<LocationCircle>()
    private var eventsOverlay: MapEventsOverlay? = null

    /** Start listening for single taps -> prompt radius -> create zone */
    fun enableTapToCreateZone(
        defaultMeters: Double = 600.0,
        fillColor: Int = Color.argb(120, 57, 161, 255),  // translucent blue
        strokeColor: Int = Color.parseColor("#39A1FF"),
        strokeWidthPx: Float = 4f,
        onZoneAdded: ((LocationCircle) -> Unit)? = null
    ) {
        // Remove previous listener if any
        eventsOverlay?.let { map.overlays.remove(it) }

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p ?: return false
                showRadiusDialog(defaultMeters) { radius ->
                    val zone = addZone(
                        center = p,
                        radiusMeters = radius,
                        fillColor = fillColor,
                        strokeColor = strokeColor,
                        strokeWidthPx = strokeWidthPx
                    )
                    Toast.makeText(
                        context,
                        "Watch area added (${radius.toInt()} m)",
                        Toast.LENGTH_SHORT
                    ).show()
                    onZoneAdded?.invoke(zone)
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }

        eventsOverlay = MapEventsOverlay(receiver).also {
            map.overlays.add(it)
            map.invalidate()
        }
    }

    /** Programmatically add a zone (no dialog) */
    fun addZone(
        center: GeoPoint,
        radiusMeters: Double,
        fillColor: Int = Color.argb(120, 57, 161, 255),
        strokeColor: Int = Color.parseColor("#39A1FF"),
        strokeWidthPx: Float = 4f
    ): LocationCircle {
        val zone = LocationCircle(
            id = System.currentTimeMillis().toString(),
            center = center,
            radiusMeters = radiusMeters,
            fillColor = fillColor,
            strokeColor = strokeColor,
            strokeWidthPx = strokeWidthPx
        )
        zone.attach(map)                  // uses fillPaint/outlinePaint (non-deprecated)
        zones.add(zone)
        return zone
    }

    fun getZones(): List<LocationCircle> = zones

    fun clearZones() {
        zones.forEach { it.detach() }
        zones.clear()
        map.invalidate()
    }

    fun detach() {
        clearZones()
        eventsOverlay?.let { map.overlays.remove(it) }
        eventsOverlay = null
    }

    // --- private ---

    private fun showRadiusDialog(defaultMeters: Double, onOk: (Double) -> Unit) {
        val input = EditText(context).apply {
            hint = "Radius in meters"
            setText(defaultMeters.toInt().toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(context)
            .setTitle("Set watch radius")
            .setView(input)
            .setPositiveButton("OK") { d, _ ->
                val v = input.text.toString().toDoubleOrNull()
                if (v == null || v <= 0) {
                    Toast.makeText(context, "Enter a positive number", Toast.LENGTH_SHORT).show()
                } else onOk(v)
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }
}
