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
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.firestore

class MyDbHelper(private val context: Context) {
    private companion object {
        private const val TAG = "LOG" // Or any descriptive name
    }
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore

    fun saveLocationCircle(circle: LocationCircle) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "You must be logged in to save circles.", Toast.LENGTH_SHORT).show()
            return
        }

        // Convert to map
        val circleMap = circle.toMap()

        // Save to user's savedCircles array
        db.collection("users").document(uid)
            .update("savedCircles", com.google.firebase.firestore.FieldValue.arrayUnion(circleMap))
            .addOnSuccessListener {
                Toast.makeText(context, "Circle saved successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save circle", e)
                Toast.makeText(context, "Failed to save circle: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    fun observeSavedCircles(
        onChange: (List<LocationCircle>) -> Unit,
        onError: (Exception) -> Unit = { e -> android.util.Log.e("LOG","observeSavedCircles", e) }
    ): ListenerRegistration {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in")
        return db.collection("users").document(uid)
            .addSnapshotListener { snap, e ->
                if (e != null) { onError(e); return@addSnapshotListener }
                val raw = snap?.get("savedCircles") as? List<*>
                val all = raw?.mapNotNull { it as? Map<String, Any> }?.map { LocationCircle.fromMap(it) } ?: emptyList()
                val safeOnly = all.filter { !it.isDisaster }
                onChange(safeOnly)
            }
    }

    fun observeDisasterCircles(
        onChange: (List<LocationCircle>) -> Unit,
        onError: (Exception) -> Unit = { e -> android.util.Log.e("LOG","observeDisasterCircles", e) }
    ): ListenerRegistration {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in")
        return db.collection("users").document(uid)
            .addSnapshotListener { snap, e ->
                if (e != null) { onError(e); return@addSnapshotListener }
                val raw = snap?.get("savedCircles") as? List<*>
                val all = raw?.mapNotNull { it as? Map<String, Any> }?.map { LocationCircle.fromMap(it) } ?: emptyList()
                val disastersOnly = all.filter { it.isDisaster }
                onChange(disastersOnly)
            }
    }

    fun loadSavedCircles(
        onSuccess: (List<LocationCircle>) -> Unit,
        onFailure: (Exception) -> Unit = { e -> Log.e(TAG, "loadSavedCircles error", e) }
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure(IllegalStateException("User must be logged in"))
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                try {
                    val raw = doc.get("savedCircles") as? List<*>
                    val circleMaps = raw?.mapNotNull { it as? Map<String, Any> } ?: emptyList()

                    val circles = circleMaps.mapNotNull { map ->
                        try {
                            LocationCircle.fromMap(map)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse LocationCircle from map: $map", e)
                            null
                        }
                    }

                    onSuccess(circles)
                } catch (e: Exception) {
                    onFailure(e)
                }
            }
            .addOnFailureListener(onFailure)
    }




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
                            val i = Intent(context, LoginActivity::class.java)
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

                                    val i = Intent(context, HomeActivity::class.java)
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


    /** Adjust path if your disasters are stored elsewhere */


    fun deleteLocationCircle(id: String) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val circles = document.get("savedCircles") as? List<Map<String, Any>> ?: return@addOnSuccessListener
                val circleToRemove = circles.find { it["id"] == id }

                if (circleToRemove != null) {
                    db.collection("users").document(uid)
                        .update("savedCircles", com.google.firebase.firestore.FieldValue.arrayRemove(circleToRemove))
                        .addOnSuccessListener {
                            Toast.makeText(context, "Circle deleted successfully!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to delete circle", e)
                            Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(context, "Circle not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading circles for deletion", e)
                Toast.makeText(context, "Failed to load circles: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }




    fun logout(){
        auth.signOut()
    }
}
