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

    // For real-time preview
    private var previewCircle: LocationCircle? = null

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
        val dlsu = GeoPoint(14.5663, 120.9931)
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
            icon = resources.getDrawable(R.drawable.baseline_account_circle_24, requireContext().theme)
        }
        map.overlays.add(marker)
        map.invalidate()

        val myDbHelper = MyDbHelper(requireContext())

        myDbHelper.loadSavedCircles(
            onSuccess = { savedCircles ->
                if (savedCircles.isEmpty()) {
                    // ✅ No saved zones — add a new one
                    val zone = LocationCircle(
                        id = System.currentTimeMillis().toString(),
                        center = dlsu,
                        radiusMeters = 50.0,
                        isDisaster = false,
                        nameType = "You"
                    )
                    zone.attach(map) // Draw on map
                    myDbHelper.saveLocationCircle(zone)
                } else {
                    // ✅ Circles exist — draw them on map
                    for (zone in savedCircles) {
                        zone.attach(map)
                    }
                }
            },
            onFailure = { e ->
                Toast.makeText(requireContext(), "Error loading zones: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
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
                showRadiusDialog(p, defaultMeters = 500.0) { radius, isDisaster, nameType ->
                    val zone = LocationCircle(
                        id = System.currentTimeMillis().toString(),
                        center = p,
                        radiusMeters = radius,
                        isDisaster = isDisaster,
                        nameType = nameType
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
        tapPoint: GeoPoint,
        defaultMeters: Double = 500.0,
        onOk: (Double, Boolean, String) -> Unit
    ) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16) // Reduced padding for more compact dialog
        }

        val disasterSwitch = SwitchCompat(requireContext()).apply {
            text = "Mark as Disaster Area"
            isChecked = false
        }

        // Name input for non-disaster
        val nameInput = EditText(requireContext()).apply {
            hint = "Enter location name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        // Disaster type dropdown
        val disasterSpinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Fire", "Flood", "Earthquake")
            )
        }

        val attachedImageView = ImageView(requireContext()).apply {
            setImageResource(R.drawable.disaster)
            adjustViewBounds = true
            maxHeight = (100 * resources.displayMetrics.density).toInt()
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        // Attach Image button
        val attachImageButton = Button(requireContext()).apply {
            text = "Attach Image"
            setOnClickListener {
                attachedImageView.visibility = View.VISIBLE
            }
        }

        // Radius label and value display
        val radiusLabel = TextView(requireContext()).apply {
            text = "Radius: ${defaultMeters.toInt()}m"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }

        // Slider for radius (50m to 2000m range) - Enhanced for better UX
        val radiusSlider = SeekBar(requireContext()).apply {
            max = 1950 // 2000 - 50 = 1950
            progress = (defaultMeters - 50).toInt() // Convert to slider range
            setPadding(0, 8, 0, 16)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val radiusValue = (progress + 50).toDouble() // Convert back to actual radius (min 50m)
                        radiusLabel.text = "Radius: ${radiusValue.toInt()}m"

                        // Update preview circle in real-time
                        updatePreviewCircle(tapPoint, radiusValue, disasterSwitch.isChecked)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val dynamicContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }
        dynamicContainer.removeAllViews()
        dynamicContainer.addView(nameInput)

        // Switch behavior with real-time updates
        disasterSwitch.setOnCheckedChangeListener { _, isChecked ->
            dynamicContainer.removeAllViews()
            if (isChecked) {
                dynamicContainer.addView(disasterSpinner)
                dynamicContainer.addView(attachImageButton)
                dynamicContainer.addView(attachedImageView)
            } else {
                dynamicContainer.addView(nameInput)
            }

            // Update preview circle when switch changes
            val radiusValue = (radiusSlider.progress + 50).toDouble()
            updatePreviewCircle(tapPoint, radiusValue, isChecked)
        }

        // Create initial preview circle
        updatePreviewCircle(tapPoint, defaultMeters, false)

        // Add views to layout
        layout.addView(radiusLabel)
        layout.addView(radiusSlider)
        layout.addView(disasterSwitch)
        layout.addView(dynamicContainer)

        // Create dialog positioned at bottom with transparency
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Set Circle Details")
            .setView(layout)
            .setPositiveButton("OK") { d, _ ->
                val radiusValue = (radiusSlider.progress + 50).toDouble()
                val extraValue = if (disasterSwitch.isChecked) {
                    disasterSpinner.selectedItem.toString()
                } else {
                    nameInput.text.toString()
                }

                // Remove preview circle before creating final one
                removePreviewCircle()

                onOk(radiusValue, disasterSwitch.isChecked, extraValue)
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ ->
                // Remove preview circle on cancel
                removePreviewCircle()
                d.dismiss()
            }
            .setOnDismissListener {
                // Clean up preview circle when dialog is dismissed
                removePreviewCircle()
            }
            .create()

        // Position dialog at bottom with custom styling
        dialog.show()
        val window = dialog.window
        window?.let {
            val params = it.attributes
            params.gravity = Gravity.BOTTOM
            params.y = (50 * resources.displayMetrics.density).toInt() // Margin from bottom
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            it.attributes = params

            // Add semi-transparent background to make map visible behind
            it.setDimAmount(0.3f) // Reduce dim amount to see map better
        }
    }

    private fun updatePreviewCircle(center: GeoPoint, radiusMeters: Double, isDisaster: Boolean) {
        // Remove existing preview circle
        removePreviewCircle()

        // Create new preview circle with temporary ID and different name
        previewCircle = LocationCircle(
            id = "preview_${System.currentTimeMillis()}",
            center = center,
            radiusMeters = radiusMeters,
            isDisaster = isDisaster,
            nameType = "Preview (${radiusMeters.toInt()}m)"
        ).apply {
            // Attach normally - the preview nature is handled by our cleanup logic
            attach(map)
        }

        map.invalidate()
    }

    private fun removePreviewCircle() {
        previewCircle?.let { circle ->
            circle.detach()
            previewCircle = null
            map.invalidate()
        }
    }

    private val triggeredOverlaps = mutableSetOf<Pair<String, String>>()

    private fun checkOverlapsAndNotify(
        safe: List<LocationCircle>,
        hazards: List<LocationCircle>
    ) {
        for (s in safe) {
            for (h in hazards) {
                val pairKey = s.id to h.id
                if (pairKey in triggeredOverlaps) continue // Already notified

                if (circlesOverlap(s, h)) {
                    triggeredOverlaps.add(pairKey)

                    notifier.showNotification(
                        title = "⚠️ Hazard near your zone",
                        message = "A disaster area overlaps your '${s.nameType}' zone."
                    )

                    if (isAdded && isVisible) {
                        showHelpResourcesDialog()
                    }
                }
            }
        }
    }

    private fun showHelpResourcesDialog() {
        val resourcesList = listOf(
            "Philippine Fire Department" to "tel:911",
            "Local Government Unit" to "tel:1234567890"
        )

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        resourcesList.forEach { (label, phone) ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val textView = TextView(requireContext()).apply {
                    text = label
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val callButton = Button(requireContext()).apply {
                    text = "Call"
                    setOnClickListener {
                        Toast.makeText(requireContext(), "Calling $label…", Toast.LENGTH_SHORT).show()
                    }
                }
                addView(textView)
                addView(callButton)
            }
            layout.addView(row)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Emergency Contacts")
            .setView(layout)
            .setPositiveButton("Close", null)
            .show()
    }
}