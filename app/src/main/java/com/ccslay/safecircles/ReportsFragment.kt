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

class ReportsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.reports_sample, container, false)

        return root
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}