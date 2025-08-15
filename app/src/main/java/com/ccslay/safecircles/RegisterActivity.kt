package com.ccslay.safecircles

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.ccslay.safecircles.databinding.ActivityRegisterBinding
class RegisterActivity: ComponentActivity()  {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewBinding: ActivityRegisterBinding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        val mydbHelper = MyDbHelper(this)


        viewBinding.registerButton.setOnClickListener {
            val name = viewBinding.nameEditText.text.toString().trim()
            val email = viewBinding.emailEditText.text.toString().trim()
            val phoneNumber = viewBinding.phoneNumberEditText.text.toString().trim()
            val plainPassword = viewBinding.passwordEditText.text.toString().trim()
            val confirmPassword = viewBinding.confirmPassEditText.text.toString().trim()

            val specialCharRegex = Regex(".*[!@#\$%^&*()_+=\\[\\]{};':\"\\\\|,.<>/?].*")
            if (plainPassword.length < 5 || !specialCharRegex.containsMatchIn(plainPassword)) {
                Toast.makeText(this, "Password must be at least 5 characters and contain a special character.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            } else if (plainPassword != confirmPassword) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            mydbHelper.addUser(email, name, phoneNumber, plainPassword)

            // Check if username or email already exists in Firestore
        }


        viewBinding.loginLink.setOnClickListener ({
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        })
    }


}