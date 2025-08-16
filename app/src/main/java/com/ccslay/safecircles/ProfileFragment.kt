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

class ProfileFragment : Fragment() {

    private lateinit var nameDisp: TextView
    private lateinit var phoneRow: LinearLayout
    private lateinit var emailRow: LinearLayout
    private lateinit var logOut: LinearLayout
    private lateinit var profileImage: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Bind views
        nameDisp = view.findViewById(R.id.nameDisp)
        phoneRow = view.findViewById(R.id.phoneRow)
        emailRow = view.findViewById(R.id.emailRow)
        logOut = view.findViewById(R.id.logOut)
        profileImage = view.findViewById(R.id.roundedProfile)

        // TODO: Replace with actual user data from your storage / API
        nameDisp.text = "Juan Dela Cruz"

        // Set up actions
        phoneRow.setOnClickListener {
            Toast.makeText(requireContext(), "Phone: 09999999999", Toast.LENGTH_SHORT).show()
        }

        emailRow.setOnClickListener {
            Toast.makeText(requireContext(), "Email: juandelacruz@email.com", Toast.LENGTH_SHORT).show()
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
