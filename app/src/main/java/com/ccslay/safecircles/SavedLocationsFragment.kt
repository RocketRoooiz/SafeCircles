package com.ccslay.safecircles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ccslay.safecircles.zone.LocationCircle
import com.google.firebase.firestore.ListenerRegistration

class SavedLocationsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SavedLocationsAdapter
    private lateinit var dbHelper: MyDbHelper
    private var circlesListener: ListenerRegistration? = null
    private val savedCircles = mutableListOf<LocationCircle>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_saved_locations, container, false)

        recyclerView = root.findViewById(R.id.recyclerView_savedLocations)
        dbHelper = MyDbHelper(requireContext())

        setupRecyclerView()
        loadSavedCircles()

        return root
    }

    private fun setupRecyclerView() {
        adapter = SavedLocationsAdapter(
            circles = savedCircles,
            onNotificationToggle = { circle, isEnabled ->
                // Handle notification toggle
                handleNotificationToggle(circle, isEnabled)
            },
            onDelete = { circle ->
                // Handle delete
                handleDelete(circle)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun loadSavedCircles() {
        circlesListener = dbHelper.observeSavedCircles(
            onChange = { circles ->
                savedCircles.clear()
                savedCircles.addAll(circles)
                adapter.notifyDataSetChanged()
            },
            onError = { e ->
                Toast.makeText(requireContext(), "Failed to load saved locations: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun handleNotificationToggle(circle: LocationCircle, isEnabled: Boolean) {
        // Update the circle's notification status in database
        // You'll need to implement this in your LocationCircle class and database helper
        Toast.makeText(requireContext(),
            "Notifications ${if (isEnabled) "enabled" else "disabled"} for ${circle.id}",
            Toast.LENGTH_SHORT).show()
    }

    private fun handleDelete(circle: LocationCircle) {
        dbHelper.deleteLocationCircle(circle.id)
        Toast.makeText(requireContext(), "Location deleted", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        circlesListener?.remove()
        circlesListener = null
        super.onDestroy()
    }
}