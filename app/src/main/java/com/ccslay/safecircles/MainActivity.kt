package com.ccslay.safecircles

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.osmdroid.api.IGeoPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.app.AlertDialog
import android.widget.EditText
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import com.ccslay.safecircles.zone.LocationCircle


class MainActivity : AppCompatActivity() {
    private val myZones = mutableListOf<LocationCircle>()
    private lateinit var map: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null

    // Ask for location at runtime
    private val requestLocPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (ok) enableMyLocation()
        else Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)

        // If permission already granted, enable immediately; otherwise request it
        if (hasLocationPermission()) enableMyLocation()
        else requestLocPerms.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        addRecenterButton()
        //This is for adding circles
        //
        //
        //
        //
        //
        //
        //
        enableTapToCreateZone()
    }

    override fun onResume() { super.onResume(); map.onResume(); myLocationOverlay?.enableMyLocation() }
    override fun onPause()  { myLocationOverlay?.disableMyLocation(); map.onPause(); super.onPause() }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun enableMyLocation() {
        val provider = GpsMyLocationProvider(this)
        myLocationOverlay = MyLocationNewOverlay(provider, map).apply {
            enableMyLocation()
            // Not enabling follow here to avoid snap to emulator's default
        }
        map.overlays.add(myLocationOverlay)

        // Coordinates for DLSU
        val dlsu = GeoPoint(14.5646, 120.9930)
        map.controller.setZoom(16.0)
        map.controller.setCenter(dlsu)

        // Add a marker at DLSU
        val marker = org.osmdroid.views.overlay.Marker(map).apply {
            position = dlsu
            title = "De La Salle University"
            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
            icon = resources.getDrawable(R.drawable.ic_launcher_foreground, theme)
        }
        map.overlays.add(marker)

        map.invalidate()
    }




    private fun addRecenterButton() {
        val root = findViewById<FrameLayout>(android.R.id.content)
        val btn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_mylocation)
            background = null
            contentDescription = "My Location"
            setOnClickListener {
                val myLoc: IGeoPoint? = myLocationOverlay?.myLocation
                if (myLoc != null) {
                    map.controller.animateTo(GeoPoint(myLoc.latitude, myLoc.longitude))
                    myLocationOverlay?.enableFollowLocation()
                } else {
                    Toast.makeText(this@MainActivity, "Locating…", Toast.LENGTH_SHORT).show()
                    myLocationOverlay?.enableMyLocation()
                }
            }
        }
        // simple layout params to place it bottom-right
        val size = resources.displayMetrics.density * 56
        val lp = FrameLayout.LayoutParams(size.toInt(), size.toInt())
        lp.marginEnd = (16 * resources.displayMetrics.density).toInt()
        lp.bottomMargin = (24 * resources.displayMetrics.density).toInt()
        lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
        root.addView(btn, lp)
    }

    private fun enableTapToCreateZone() {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p ?: return false
                showRadiusDialog(defaultMeters = 600.0) { radius ->
                    val zone = LocationCircle(
                        id = System.currentTimeMillis().toString(),
                        center = p,
                        radiusMeters = radius, // <-- use the chosen radius
                        fillColor = android.graphics.Color.argb(120, 57, 161, 255),
                        strokeColor = android.graphics.Color.parseColor("#39A1FF"),
                        strokeWidthPx = 4f
                    )
                    zone.attach(map)
                    myZones.add(zone)
                    Toast.makeText(this@MainActivity, "Watch area added (${radius.toInt()} m)", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        map.overlays.add(MapEventsOverlay(receiver))
        map.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

    }

    private fun showRadiusDialog(defaultMeters: Double = 600.0, onOk: (Double) -> Unit) {
        val input = EditText(this).apply {
            hint = "Radius in meters"
            setText(defaultMeters.toInt().toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Set watch radius")
            .setView(input)
            .setPositiveButton("OK") { d, _ ->
                val v = input.text.toString().toDoubleOrNull()
                if (v == null || v <= 0) {
                    Toast.makeText(this, "Enter a positive number", Toast.LENGTH_SHORT).show()
                } else onOk(v)
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }


}
