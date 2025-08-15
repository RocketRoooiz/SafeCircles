package com.ccslay.safecircles

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.ccslay.safecircles.notifications.NotificationHelper
import com.ccslay.safecircles.zone.LocationCircle
import com.ccslay.safecircles.zone.LocationCircle.Companion.circlesOverlap
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.api.IGeoPoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapFragment : Fragment() {

    private lateinit var dbHelper: MyDbHelper
    private val myZones = mutableListOf<LocationCircle>()
    private val disasterZones = mutableListOf<LocationCircle>()

    private lateinit var map: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null

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
        else Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    // Request notification permission for Android 13+
    private val requestNotificationPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(requireContext(), "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_map, container, false)

        map = root.findViewById(R.id.map)
        dbHelper = MyDbHelper(requireContext())
        notifier = NotificationHelper(requireContext())

        // Request permissions
        if (hasLocationPermission()) enableMyLocation()
        else requestLocPerms.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        addRecenterButton(root as FrameLayout)
        enableTapToCreateZone()

        // Observe safe circles
        safeListener = dbHelper.observeSavedCircles(
            onChange = { safeCircles ->
                // Redraw safe circles
                myZones.forEach { it.detach() }
                myZones.clear()
                safeCircles.forEach {
                    it.attach(map)
                    myZones.add(it)
                }
                map.invalidate()
                checkOverlapsAndNotify(myZones, disasterZones)
            },
            onError = { e ->
                Toast.makeText(requireContext(), "Failed to load circles: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )

        // Observe disaster circles
        disasterListener = dbHelper.observeDisasterCircles(
            onChange = { hazardCircles ->
                // Redraw disaster circles
                disasterZones.forEach { it.detach() }
                disasterZones.clear()
                hazardCircles.forEach {
                    it.attach(map)
                    disasterZones.add(it)
                }
                map.invalidate()
                checkOverlapsAndNotify(myZones, disasterZones)
            },
            onError = { e ->
                Toast.makeText(requireContext(), "Failed to load disasters: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )

        return root
    }

    override fun onDestroy() {
        safeListener?.remove()
        disasterListener?.remove()
        safeListener = null
        disasterListener = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        myLocationOverlay?.disableMyLocation()
        map.onPause()
        super.onPause()
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun enableMyLocation() {
        val provider = GpsMyLocationProvider(requireContext())
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
            setAnchor(
                org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
            )
            icon = resources.getDrawable(R.drawable.ic_launcher_foreground, requireContext().theme)
        }
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun addRecenterButton(root: FrameLayout) {
        val btn = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_mylocation)
            background = null
            contentDescription = "My Location"
            setOnClickListener {
                val myLoc: IGeoPoint? = myLocationOverlay?.myLocation
                if (myLoc != null) {
                    map.controller.animateTo(GeoPoint(myLoc.latitude, myLoc.longitude))
                    myLocationOverlay?.enableFollowLocation()
                } else {
                    Toast.makeText(requireContext(), "Locating…", Toast.LENGTH_SHORT).show()
                    myLocationOverlay?.enableMyLocation()
                }
            }
        }

        // Simple layout params to place it bottom-right
        val size = resources.displayMetrics.density * 56
        val lp = FrameLayout.LayoutParams(size.toInt(), size.toInt())
        lp.marginEnd = (16 * resources.displayMetrics.density).toInt()
        lp.bottomMargin = (24 * resources.displayMetrics.density).toInt()
        lp.gravity = Gravity.BOTTOM or Gravity.END
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

                    // Add to appropriate list
                    if (isDisaster) {
                        disasterZones.add(zone)
                    } else {
                        myZones.add(zone)
                    }

                    MyDbHelper(requireContext()).saveLocationCircle(zone)
                    Toast.makeText(
                        requireContext(),
                        if (isDisaster) "Disaster area added (${radius.toInt()} m)" else "Watch area added (${radius.toInt()} m)",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Check for overlaps after adding new zone
                    checkOverlapsAndNotify(myZones, disasterZones)
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }
        map.overlays.add(MapEventsOverlay(receiver))
    }

    private fun showRadiusDialog(
        defaultMeters: Double = 500.0,
        onOk: (Double, Boolean) -> Unit // Returns radius + disaster flag
    ) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val radiusInput = EditText(requireContext()).apply {
            hint = "Radius in meters"
            setText(defaultMeters.toInt().toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val disasterSwitch = SwitchCompat(requireContext()).apply {
            text = "Mark as Disaster Area"
            isChecked = false
        }

        layout.addView(radiusInput)
        layout.addView(disasterSwitch)

        AlertDialog.Builder(requireContext())
            .setTitle("Set Circle Details")
            .setView(layout)
            .setPositiveButton("OK") { d, _ ->
                val v = radiusInput.text.toString().toDoubleOrNull()
                if (v == null || v <= 0) {
                    Toast.makeText(requireContext(), "Enter a positive number", Toast.LENGTH_SHORT).show()
                } else {
                    onOk(v, disasterSwitch.isChecked) // Pass both values
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