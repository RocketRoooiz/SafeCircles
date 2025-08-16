package com.ccslay.safecircles

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

class ProfileFragment : Fragment() {

    private lateinit var nameDisp: TextView
    private lateinit var emailDisp: TextView
    private lateinit var phoneDisp: TextView
    private lateinit var phoneRow: LinearLayout
    private lateinit var emailRow: LinearLayout
    private lateinit var logOut: LinearLayout
    private lateinit var profileImage: ImageView
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Bind views
        nameDisp = view.findViewById(R.id.nameDisp)
        phoneDisp = view.findViewById(R.id.phoneDisp)
        emailDisp = view.findViewById(R.id.emailDisp)
        phoneRow = view.findViewById(R.id.phoneRow)
        emailRow = view.findViewById(R.id.emailRow)
        logOut = view.findViewById(R.id.logOut)
        profileImage = view.findViewById(R.id.roundedProfile)

        // TODO: Replace with actual user data from your storage / API
        auth = Firebase.auth
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val db = Firebase.firestore
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val username = document.getString("name") ?: "Unknown"
                        nameDisp.text = username
                        phoneDisp.text = document.getString("phoneNumber") ?: "Unknown"
                        emailDisp.text = document.getString("email") ?: "Unknown"

                        // Set up actions
                    }
                }
        }


        val mydbHelper = MyDbHelper(requireContext())

        logOut.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { dialog, _ ->
                    mydbHelper.logout()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }


        return view
    }
}
