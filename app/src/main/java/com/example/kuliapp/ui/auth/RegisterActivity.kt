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

    private fun saveUserToFirestore(userId: String, userName: String, phoneNumber: String, email: String) {
        // Base data yang sama untuk kedua user type
        val baseData = hashMapOf<String, Any>(
            "createdAt" to com.google.firebase.Timestamp.now(),
            "updatedAt" to com.google.firebase.Timestamp.now(),
            "isActive" to true
        )

        val userData: HashMap<String, Any>
        val collectionName: String

        if (selectedUserType == UserType.WORKER) {
            // STRUKTUR DATA UNTUK WORKER - SESUAI DENGAN Worker MODEL
            val workerId = generateWorkerUniqueId(phoneNumber)

            userData = hashMapOf(
                // Field sesuai dengan Worker model
                "workerId" to workerId,           // ✅ Konsisten dengan Worker model
                "name" to userName,               // ✅ Konsisten dengan Worker model
                "email" to email,                 // ✅ Konsisten dengan Worker model
                "phone" to phoneNumber,           // ✅ Konsisten dengan Worker model
                "phoneNumber" to phoneNumber,     // ✅ Untuk kompatibilitas
                "location" to "",                 // ✅ Sesuai Worker model
                "experience" to "",               // ✅ Sesuai Worker model
                "price" to 0L,                    // ✅ Sesuai Worker model (Long)
                "photo" to "",                    // ✅ Sesuai Worker model
                "rating" to 0.0,                  // ✅ Sesuai Worker model
                "ratingCount" to 0,               // ✅ Sesuai Worker model
                "isAvailable" to true,            // ✅ Sesuai Worker model
                "jobDate" to "",                  // ✅ Sesuai Worker model
                "jobDescription" to "",           // ✅ Sesuai Worker model
                "userType" to selectedUserType.name,
                "isAvailable" to true,

                // Field tambahan untuk worker
                "skills" to arrayListOf<String>(),
                "totalJobs" to 0,
                "completedJobs" to 0,
                "hourlyRate" to 0.0,
                "profileImageUrl" to "",
                "description" to ""
            )

            // Tambahkan base data
            userData.putAll(baseData)
            collectionName = "workers"

            // Simpan worker ID ke preferences
            prefManager.setString("worker_id", workerId)

        } else {
            // STRUKTUR DATA UNTUK CUSTOMER
            userData = hashMapOf(
                "customerId" to userId,           // ✅ Untuk customer
                "customerName" to userName,       // ✅ Untuk customer
                "phoneNumber" to phoneNumber,     // ✅ Untuk customer
                "userType" to selectedUserType.name,
                "totalOrders" to 0,
                "completedOrders" to 0,
                "profileImageUrl" to "",
                "address" to ""
            )

            // Tambahkan base data
            userData.putAll(baseData)
            collectionName = "customers"
        }

        // Simpan ke Firestore
        firestore.collection(collectionName)
            .document(userId)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                showLoading(false)

                // Simpan informasi pengguna ke preferensi lokal
                prefManager.setUserLoggedIn(true)
                prefManager.setUserType(selectedUserType.name)
                prefManager.setString("user_id", userId)
                prefManager.setString("user_name", userName)
                prefManager.setString("user_phone", phoneNumber)

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