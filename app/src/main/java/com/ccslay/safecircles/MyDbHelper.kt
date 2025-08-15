package com.ccslay.safecircles

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.ccslay.safecircles.zone.LocationCircle
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore

class MyDbHelper(private val context: Context) {
    private companion object {
        private const val TAG = "LOG" // Or any descriptive name
    }
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore

    fun addUser(email: String, name: String, phoneNumber: String, plainPassword: String) {
        val emailTask = db.collection("users").whereEqualTo("email", email).get()
        val phoneTask = db.collection("users").whereEqualTo("phoneNumber", phoneNumber).get()

        Tasks.whenAllSuccess<QuerySnapshot>(emailTask, phoneTask)
            .addOnSuccessListener { results ->
                val emailDocs = results[0]
                val phoneDocs = results[1]

                if (!emailDocs.isEmpty) {
                    Toast.makeText(context, "Email is already registered.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                if (!phoneDocs.isEmpty) {
                    Toast.makeText(context, "Phone number is already registered.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // Create Firebase Auth account
                auth.createUserWithEmailAndPassword(email, plainPassword)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                            // Empty initial data
                            val emptyHomeCircle = null  // or a default LocationCircle object if you want
                            val emptySavedCircles = arrayListOf<LocationCircle>()
                            val emptyFriends = arrayListOf<User>()

                            val user = hashMapOf(
                                "email" to email,
                                "phoneNumber" to phoneNumber,
                                "name" to name,
                                "homeCircle" to emptyHomeCircle,
                                "savedCircles" to emptySavedCircles,
                                "friends" to emptyFriends
                            )

                            // Save to Firestore
                            db.collection("users").document(uid).set(user)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Failed to store user data", e)
                                    Toast.makeText(context, "Failed to save user data: ${e.message}", Toast.LENGTH_LONG).show()
                                }

                            // Move to the map activity
                            val i = Intent(context, MainActivity::class.java)
                            i.putExtra("USER_NAME_KEY", name)
                            context.startActivity(i)
                            if (context is android.app.Activity) {
                                context.finish()
                            }

                        } else {
                            Toast.makeText(context, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error checking credentials: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    fun loginUser(email: String, password: String){
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        // Fetch the username from Firestore using UID
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val username = document.getString("username") ?: "Unknown"
                                    Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()

                                    val i = Intent(context, MainActivity::class.java)
                                    i.putExtra(IntentKeys.USER_NAME_KEY.name, username) // Pass the username
                                    context.startActivity(i)
                                    if (context is android.app.Activity) {
                                        context.finish()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "User data not found.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Failed to load user data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                } else {
                    val message = task.exception?.message ?: "Login failed"
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
    }

    fun logout(){
        auth.signOut()
    }
}
