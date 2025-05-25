package com.example.kuliapp.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kuliapp.R
import com.example.kuliapp.databinding.ActivityProfileBinding
import com.example.kuliapp.ui.auth.LoginActivity
import com.example.kuliapp.utils.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var preferenceManager: PreferenceManager

    // State variables
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize preferences
        preferenceManager = PreferenceManager(this)

        // Set up click listeners
        setupListeners()

        // Set initial data
        loadProfileData()

        // Initially disable editing
        setEditingEnabled(false)
    }

    private fun setupListeners() {
        binding.btnEdit.setOnClickListener {
            // Toggle edit mode
            isEditMode = !isEditMode
            setEditingEnabled(isEditMode)
            binding.btnEdit.text = if (isEditMode) getString(R.string.cancel) else getString(R.string.edit)
        }

        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveProfileData()
                setEditingEnabled(false)
                isEditMode = false
                binding.btnEdit.text = getString(R.string.edit)
                Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
            }
        }

        // Add back button functionality if needed
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        // Tombol logout
        binding.buttonLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun setEditingEnabled(enabled: Boolean) {
        // Enable or disable editing for all editable fields
        binding.editName.isEnabled = enabled
        binding.editPhone.isEnabled = enabled
        binding.editAddress.isEnabled = enabled

        // Show or hide the save button
        binding.btnSave.isEnabled = enabled
        binding.btnSave.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Validate name
        if (binding.editName.text.toString().trim().isEmpty()) {
            binding.nameInputLayout.error = getString(R.string.error_name_required)
            isValid = false
        } else {
            binding.nameInputLayout.error = null
        }

        // Validate phone number (simple validation)
        val phoneText = binding.editPhone.text.toString().trim()
        if (phoneText.isEmpty()) {
            binding.phoneInputLayout.error = getString(R.string.error_phone_required)
            isValid = false
        } else if (!phoneText.matches(Regex("^[0-9+()-]{10,15}$"))) {
            binding.phoneInputLayout.error = getString(R.string.error_phone_invalid)
            isValid = false
        } else {
            binding.phoneInputLayout.error = null
        }

        // Address can be simple validation
        if (binding.editAddress.text.toString().trim().isEmpty()) {
            binding.addressInputLayout.error = getString(R.string.error_address_required)
            isValid = false
        } else {
            binding.addressInputLayout.error = null
        }

        return isValid
    }

    private fun loadProfileData() {
        // Get data from PreferenceManager
        val name = preferenceManager.getString("user_name") ?: "User Name"
        val phone = preferenceManager.getString("user_phone") ?: "08123456789"
        val address = preferenceManager.getString("user_address") ?: "Bekasi, Jawa Barat"

        // Set data to fields
        binding.editName.setText(name)
        binding.editPhone.setText(phone)
        binding.editAddress.setText(address)
    }

    private fun saveProfileData() {
        // Get data from fields
        val name = binding.editName.text.toString().trim()
        val phone = binding.editPhone.text.toString().trim()
        val address = binding.editAddress.text.toString().trim()

        // Save to preference manager
        preferenceManager.setString("user_name", name)
        preferenceManager.setString("user_phone", phone)
        preferenceManager.setString("user_address", address)
    }


    // LANGKAH 1: Update method logout di ProfileActivity
    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Keluar") { _, _ ->
                // LANGKAH 1A: Bersihkan SEMUA data login
                preferenceManager.clearAllPreferences() // atau gunakan method khusus di bawah

                // LANGKAH 1B: Buat intent ke LoginActivity
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                // LANGKAH 1C: Tambahkan extra untuk memastikan tidak auto-login
                intent.putExtra("force_logout", true)

                startActivity(intent)
                finish()
            }
            .show()
    }
}