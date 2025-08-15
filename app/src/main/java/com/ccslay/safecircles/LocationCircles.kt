// LocationCircle.kt
package com.ccslay.safecircles.zone

import android.graphics.Color
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import kotlin.math.PI
import kotlin.math.cos

class LocationCircle(
    val id: String,
    val center: GeoPoint,
    radiusMeters: Double,
    var isDisaster: Boolean = false,
    // Use ARGB for translucent fill (alpha 0x66 ≈ 40%)
    val fillColor: Int = Color.argb(0x66, 0x39, 0xA1, 0xFF),
    val strokeColor: Int = Color.parseColor("#39A1FF"),
    val strokeWidthPx: Float = 4f
) {
    private var map: MapView? = null
    private var polygon: Polygon? = null
    var radiusMeters: Double = radiusMeters
        private set

    companion object {
        fun fromMap(m: Map<String, Any>): LocationCircle {
            // Firestore gives Numbers (Long/Double). Convert safely.
            val id = (m["id"] as? String) ?: System.currentTimeMillis().toString()
            val lat = (m["centerLat"] as Number).toDouble()
            val lng = (m["centerLng"] as Number).toDouble()
            val radius = (m["radiusMeters"] as Number).toDouble()
            val isDisaster = (m["isDisaster"] as? Boolean) ?: false
            val fill = (m["fillColor"] as? Number)?.toInt() ?: android.graphics.Color.argb(120, 57,161,255)
            val stroke = (m["strokeColor"] as? Number)?.toInt() ?: android.graphics.Color.parseColor("#39A1FF")
            val strokeW = (m["strokeWidthPx"] as? Number)?.toFloat() ?: 4f

            return LocationCircle(
                id = id,
                center = org.osmdroid.util.GeoPoint(lat, lng),
                radiusMeters = radius,
                isDisaster = isDisaster,
                fillColor = fill,
                strokeColor = stroke,
                strokeWidthPx = strokeW
            )
        }

        fun circlesOverlap(a: LocationCircle, b: LocationCircle): Boolean {
            val dMeters = a.center.distanceToAsDouble(b.center) // osmdroid gives meters
            return dMeters <= (a.radiusMeters + b.radiusMeters)
        }
    }



    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "centerLat" to center.latitude,
            "centerLng" to center.longitude,
            "radiusMeters" to radiusMeters,
            "isDisaster" to isDisaster,
            "fillColor" to fillColor,
            "strokeColor" to strokeColor,
            "strokeWidthPx" to strokeWidthPx
        )
    }


    fun attach(mapView: MapView) {
        map = mapView

        // Build geometry and explicitly close the ring
        val pts = circleToPolygon(center, radiusMeters)
        if (pts.isEmpty() || pts.first() != pts.last()) pts.add(pts.first())

        val poly = Polygon(mapView).apply {
            points = pts

            // Outline (no deprecated APIs)
            outlinePaint.apply {
                this.color = this@LocationCircle.strokeColor
                this.strokeWidth = strokeWidthPx
                style = android.graphics.Paint.Style.STROKE
                isAntiAlias = true
            }

            // Fill (ARGB! alpha first)
            fillPaint.apply {
                this.color = this@LocationCircle.fillColor
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }

            // Turn red if this is a disaster
            if (isDisaster) {
                outlinePaint.color = android.graphics.Color.RED
                fillPaint.color = android.graphics.Color.argb(80, 255, 0, 0)
            } else {
                outlinePaint.color = this@LocationCircle.strokeColor
                fillPaint.color = this@LocationCircle.fillColor
            }

            infoWindow = null                 // no bubble on tap
            isEnabled = true
        }


        polygon = poly

        // Ensure it renders beneath markers/other overlays
        mapView.overlays.remove(poly)
        mapView.overlays.add(0, poly)

        // If your emulator/GPU still ignores fill, uncomment the next line:
        // mapView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

        mapView.invalidate()
    }



    fun updateRadius(newMeters: Double) {
        radiusMeters = newMeters
        polygon?.points = circleToPolygon(center, radiusMeters)
        map?.invalidate()
    }

    fun detach() {
        polygon?.let { map?.overlays?.remove(it) }
        polygon = null
        map?.invalidate()
        map = null
    }

    /** true if a point lies within this circle (meters) */
    fun contains(point: GeoPoint): Boolean =
        center.distanceToAsDouble(point) <= radiusMeters
}

/** --- helpers --- */
private fun circleToPolygon(center: GeoPoint, radiusM: Double, steps: Int = 64): ArrayList<GeoPoint> {
    val pts = ArrayList<GeoPoint>(steps + 1)
    val dLat = metersToLatDegrees(radiusM)
    val dLngBase = metersToLngDegrees(radiusM, center.latitude)
    for (i in 0..steps) {
        val t = 2.0 * PI * i / steps
        val lat = center.latitude + dLat * kotlin.math.sin(t)
        val lng = center.longitude + dLngBase * kotlin.math.cos(t)
        pts.add(GeoPoint(lat, lng))
    }
    return pts
}

private fun metersToLatDegrees(m: Double) = m / 111_320.0
private fun metersToLngDegrees(m: Double, atLat: Double) =
    m / (111_320.0 * cos(Math.toRadians(atLat)).coerceAtLeast(1e-6))
