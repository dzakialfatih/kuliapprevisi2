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

    // Binding untuk layout
    private lateinit var binding: ActivityProfileBinding

    // Manager untuk shared preferences
    private lateinit var preferenceManager: PreferenceManager

    // Variable state untuk mode edit
    private var isEditMode = false

    // Fungsi onCreate - Inisialisasi Activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi preference manager
        preferenceManager = PreferenceManager(this)

        // Setup semua listener
        setupListeners()

        // Load data profile dari preferences
        loadProfileData()

        // Set mode awal tidak bisa edit
        setEditingEnabled(false)
    }

    // Fungsi setup semua listener tombol
    private fun setupListeners() {

        // Tombol Edit/Cancel - Toggle mode edit
        binding.btnEdit.setOnClickListener {
            isEditMode = !isEditMode
            setEditingEnabled(isEditMode)
            binding.btnEdit.text = if (isEditMode) getString(R.string.cancel) else getString(R.string.edit)
        }

        // Tombol Save - Simpan perubahan profile
        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                saveProfileData()
                setEditingEnabled(false)
                isEditMode = false
                binding.btnEdit.text = getString(R.string.edit)
                Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
            }
        }

        // Tombol Back - Kembali ke halaman sebelumnya
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        // Tombol Logout - Keluar dari aplikasi
        binding.buttonLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    // Fungsi untuk mengaktifkan/menonaktifkan mode edit
    private fun setEditingEnabled(enabled: Boolean) {
        // Enable/disable field input
        binding.editName.isEnabled = enabled
        binding.editPhone.isEnabled = enabled
        binding.editAddress.isEnabled = enabled

        // Show/hide tombol save
        binding.btnSave.isEnabled = enabled
        binding.btnSave.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    // Fungsi validasi input data profile
    private fun validateInputs(): Boolean {
        var isValid = true

        // Validasi nama - tidak boleh kosong
        if (binding.editName.text.toString().trim().isEmpty()) {
            binding.nameInputLayout.error = getString(R.string.error_name_required)
            isValid = false
        } else {
            binding.nameInputLayout.error = null
        }

        // Validasi nomor telepon - tidak boleh kosong dan format valid
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

        // Validasi alamat - tidak boleh kosong
        if (binding.editAddress.text.toString().trim().isEmpty()) {
            binding.addressInputLayout.error = getString(R.string.error_address_required)
            isValid = false
        } else {
            binding.addressInputLayout.error = null
        }

        return isValid
    }

    // Fungsi load data profile dari preferences
    private fun loadProfileData() {
        // Ambil data dari PreferenceManager dengan default value
        val name = preferenceManager.getString("user_name") ?: "User Name"
        val phone = preferenceManager.getString("user_phone") ?: "08123456789"
        val address = preferenceManager.getString("user_address") ?: "Bekasi, Jawa Barat"

        // Set data ke field input
        binding.editName.setText(name)
        binding.editPhone.setText(phone)
        binding.editAddress.setText(address)
    }

    // Fungsi simpan data profile ke preferences
    private fun saveProfileData() {
        // Ambil data dari field input
        val name = binding.editName.text.toString().trim()
        val phone = binding.editPhone.text.toString().trim()
        val address = binding.editAddress.text.toString().trim()

        // Simpan ke preference manager
        preferenceManager.setString("user_name", name)
        preferenceManager.setString("user_phone", phone)
        preferenceManager.setString("user_address", address)
    }

    // Fungsi dialog konfirmasi logout
    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Keluar") { _, _ ->
                // Bersihkan SEMUA data login dari preferences
                preferenceManager.clearAllPreferences()

                // Buat intent ke LoginActivity dengan flag clear task
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                // Tambahkan extra untuk memastikan tidak auto-login
                intent.putExtra("force_logout", true)

                // Pindah ke LoginActivity dan tutup ProfileActivity
                startActivity(intent)
                finish()
            }
            .show()
    }
}