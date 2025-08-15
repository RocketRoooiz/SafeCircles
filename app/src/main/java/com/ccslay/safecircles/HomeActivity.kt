package com.ccslay.safecircles

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ccslay.safecircles.databinding.ActivityHomeBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the toolbar as action bar
        setSupportActionBar(binding.toolbar)

        // Set up bottom navigation
        setupBottomNavigation()

        // Load MapFragment as the default fragment
        if (savedInstanceState == null) {
            loadFragment(MapFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.map -> {
                    loadFragment(MapFragment())
                    supportActionBar?.title = "Safe Circles"
                    true
                }
                R.id.saved_loc -> {
                    loadFragment(SavedLocationsFragment())
                    supportActionBar?.title = "Saved Locations"
                    true
                }
                R.id.reports -> {
                    loadFragment(ReportsFragment())
                    supportActionBar?.title = "Recent Reports"
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }
}