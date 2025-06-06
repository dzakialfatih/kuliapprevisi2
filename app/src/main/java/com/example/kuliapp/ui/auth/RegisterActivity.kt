package com.example.kuliapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kuliapp.R
import com.example.kuliapp.databinding.ActivityRegisterBinding
import com.example.kuliapp.ui.customer.CustomerDashboardActivity
import com.example.kuliapp.ui.worker.WorkerDashboardActivity
import com.example.kuliapp.utils.PreferenceManager
import com.example.kuliapp.utils.UserType
import com.example.kuliapp.utils.ValidationUtils
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var selectedUserType = UserType.CUSTOMER
    private lateinit var prefManager: PreferenceManager
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PreferenceManager(this)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupUserTypeTabs()
        setupRegisterButton()
        setupLoginLink()
    }

    private fun setupUserTypeTabs() {
        binding.tabsUserType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedUserType = if (tab?.position == 0) UserType.CUSTOMER else UserType.WORKER
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRegisterButton() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val phoneNumber = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (validateInputs(name, phoneNumber, password, confirmPassword)) {
                attemptRegister(name, phoneNumber, password)
            }
        }
    }

    private fun validateInputs(name: String, phoneNumber: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        // Validate name
        if (name.isEmpty()) {
            binding.tilName.error = getString(R.string.error_empty_field)
            isValid = false
        } else {
            binding.tilName.error = null
        }

        // Validate phone number
        if (phoneNumber.isEmpty()) {
            binding.tilPhone.error = getString(R.string.error_empty_field)
            isValid = false
        } else if (!ValidationUtils.isValidPhoneNumber(phoneNumber)) {
            binding.tilPhone.error = getString(R.string.error_invalid_phone)
            isValid = false
        } else {
            binding.tilPhone.error = null
        }

        // Validate password
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_empty_field)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_too_short)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = getString(R.string.error_empty_field)
            isValid = false
        } else if (confirmPassword != password) {
            binding.tilConfirmPassword.error = getString(R.string.error_passwords_dont_match)
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun attemptRegister(name: String, phoneNumber: String, password: String) {
        showLoading(true)

        // Buat email temporary dari nomor telepon untuk Firebase Auth
        val email = "${phoneNumber}@kuliapp.com"

        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    user?.let { firebaseUser ->
                        // Simpan data user ke Firestore
                        saveUserToFirestore(firebaseUser.uid, name, phoneNumber, email)
                    }
                } else {
                    showLoading(false)
                    val errorMessage = when {
                        task.exception?.message?.contains("email address is already in use") == true ->
                            "Nomor telepon sudah terdaftar"
                        task.exception?.message?.contains("weak password") == true ->
                            "Password terlalu lemah"
                        else -> "Registrasi gagal: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserToFirestore(customerId: String, customerName: String, phoneNumber: String, email: String) {
        val userData = hashMapOf<String, Any>(
            "customerId" to customerId,
            "customerName" to customerName,
            "phoneNumber" to phoneNumber,
            "userType" to selectedUserType.name,
            "createdAt" to com.google.firebase.Timestamp.now(),
            "updatedAt" to com.google.firebase.Timestamp.now(),
            "isActive" to true
        )

        // Tambahkan field khusus berdasarkan tipe user
        if (selectedUserType == UserType.WORKER) {
            // GENERATE UNIQUE WORKER ID
            val workerId = generateWorkerUniqueId(phoneNumber)

            userData["workerId"] = workerId // Tambahkan field id untuk Worker model
            userData["email"] = email // Tambahkan email untuk Worker model
            userData["phone"] = phoneNumber // Tambahkan phone untuk Worker model
            userData["location"] = "" // Sesuai dengan Worker model
            userData["experience"] = "" // Sesuai dengan Worker model
            userData["skills"] = arrayListOf<String>()
            userData["rating"] = 0.0
            userData["ratingCount"] = 0 // Tambahkan ratingCount
            userData["totalJobs"] = 0
            userData["completedJobs"] = 0
            userData["isAvailable"] = true
            userData["hourlyRate"] = 0.0
            userData["price"] = 0L // Sesuai dengan Worker model (Long)
            userData["profileImageUrl"] = ""
            userData["photo"] = "" // Sesuai dengan Worker model
            userData["description"] = ""
        } else {
            userData["totalOrders"] = 0
            userData["completedOrders"] = 0
            userData["profileImageUrl"] = ""
            userData["address"] = ""
        }

        val collectionName = if (selectedUserType == UserType.CUSTOMER) "customers" else "workers"

        firestore.collection(collectionName)
            .document(customerId)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                showLoading(false)

                // Simpan informasi pengguna ke preferensi lokal
                prefManager.setUserLoggedIn(true)
                prefManager.setUserType(selectedUserType.name)
                prefManager.setString("user_id", customerId)
                prefManager.setString("user_name", customerName)
                prefManager.setString("user_phone", phoneNumber)

                // SIMPAN WORKER ID JIKA USER ADALAH WORKER
                if (selectedUserType == UserType.WORKER) {
                    val workerId = generateWorkerUniqueId(phoneNumber)
                    prefManager.setString("worker_id", workerId)
                }

                Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()

                // Navigasi ke dashboard sesuai dengan tipe pengguna
                val intent = if (selectedUserType == UserType.CUSTOMER) {
                    Intent(this, CustomerDashboardActivity::class.java)
                } else {
                    Intent(this, WorkerDashboardActivity::class.java)
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Gagal menyimpan data: ${e.message}", Toast.LENGTH_LONG).show()

                // Hapus user dari Firebase Auth jika gagal menyimpan ke Firestore
                firebaseAuth.currentUser?.delete()
            }
    }

    // METODE 1: Generate ID berdasarkan timestamp + phone number
    private fun generateWorkerUniqueId(phoneNumber: String): String {
        val timestamp = System.currentTimeMillis()
        val phoneLastFour = phoneNumber.takeLast(4)
        return "WKR${phoneLastFour}${timestamp}"
    }

    private fun showLoading(show: Boolean) {
        binding.btnRegister.isEnabled = !show
        binding.btnRegister.text = if (show) "Mendaftar..." else "Daftar"

        // Jika Anda memiliki progress bar, tampilkan/sembunyikan di sini
        // binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupLoginLink() {
        binding.tvLogin.setOnClickListener {
            finish() // Go back to login activity
        }
    }
}