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
import android.os.Build
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.material3.Switch
import com.ccslay.safecircles.notifications.NotificationHelper
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import com.ccslay.safecircles.zone.LocationCircle
import com.google.firebase.firestore.ListenerRegistration
import com.ccslay.safecircles.zone.LocationCircle.Companion.circlesOverlap



class MainActivity : AppCompatActivity() {
    private lateinit var dbHelper: MyDbHelper
    private val myZones = mutableListOf<LocationCircle>()
    private val disasterZones = mutableListOf<LocationCircle>()

    private lateinit var map: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    //private var circlesListener: ListenerRegistration? = null

    private var safeListener: ListenerRegistration? = null
    private var disasterListener: ListenerRegistration? = null

    private lateinit var notifier: NotificationHelper


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
        dbHelper = MyDbHelper(this)
        notifier = NotificationHelper(this)


        // If permission already granted, enable immediately; otherwise request it
        if (hasLocationPermission()) enableMyLocation()
        else requestLocPerms.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
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

        safeListener = dbHelper.observeSavedCircles(
            onChange = { safeCircles ->
                // redraw safe circles
                myZones.forEach { it.detach() }
                myZones.clear()
                safeCircles.forEach { it.attach(map); myZones.add(it) }

                checkOverlapsAndNotify(myZones, disasterZones)
            },
            onError = { e ->
                Toast.makeText(this, "Failed to load circles: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )

        disasterListener = dbHelper.observeDisasterCircles(
            onChange = { hazardCircles ->
                // redraw disaster circles
                disasterZones.forEach { it.detach() }
                disasterZones.clear()
                hazardCircles.forEach { it.attach(map); disasterZones.add(it) }

                checkOverlapsAndNotify(myZones, disasterZones)
            },
            onError = { e ->
                Toast.makeText(this, "Failed to load disasters: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )


    }

    override fun onDestroy() {
        safeListener?.remove()
        disasterListener?.remove()
        safeListener = null
        disasterListener = null
        super.onDestroy()
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
                showRadiusDialog(defaultMeters = 500.0) { radius, isDisaster ->
                    val zone = LocationCircle(
                        id = System.currentTimeMillis().toString(),
                        center = p,
                        radiusMeters = radius,
                        isDisaster = isDisaster
                    )
                    zone.attach(map)
                    myZones.add(zone)

                    MyDbHelper(this@MainActivity).saveLocationCircle(zone)
                    Toast.makeText(
                        this@MainActivity,
                        if (isDisaster) "Disaster area added (${radius.toInt()} m)" else "Watch area added (${radius.toInt()} m)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        map.overlays.add(MapEventsOverlay(receiver))
    }

    private fun showRadiusDialog(
        defaultMeters: Double = 500.0,
        onOk: (Double, Boolean) -> Unit // <-- now returns radius + disaster flag
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val radiusInput = EditText(this).apply {
            hint = "Radius in meters"
            setText(defaultMeters.toInt().toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val disasterSwitch = SwitchCompat(this).apply {
            text = "Mark as Disaster Area"
            isChecked = false
        }


        layout.addView(radiusInput)
        layout.addView(disasterSwitch)

        AlertDialog.Builder(this)
            .setTitle("Set Circle Details")
            .setView(layout)
            .setPositiveButton("OK") { d, _ ->
                val v = radiusInput.text.toString().toDoubleOrNull()
                if (v == null || v <= 0) {
                    Toast.makeText(this, "Enter a positive number", Toast.LENGTH_SHORT).show()
                } else {
                    onOk(v, disasterSwitch.isChecked) // pass both values
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun checkOverlapsAndNotify(
        safe: List<LocationCircle>,
        hazards: List<LocationCircle>
    ) {
        for (s in safe) {
            for (h in hazards) {
                if (circlesOverlap(s, h)) {
                    notifier.showNotification(
                        title = "⚠️ Hazard near your zone",
                        message = "A disaster area overlaps your '${s.id}' zone."
                    )
                    return // Avoid multiple notifications at once
                }
            }
        }
    }



}
