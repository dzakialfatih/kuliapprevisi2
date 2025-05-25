package com.example.kuliapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kuliapp.R
import com.example.kuliapp.databinding.ActivityLoginBinding
import com.example.kuliapp.ui.customer.CustomerDashboardActivity
import com.example.kuliapp.ui.worker.WorkerDashboardActivity
import com.example.kuliapp.utils.PreferenceManager
import com.example.kuliapp.utils.UserType
import com.example.kuliapp.utils.ValidationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var selectedUserType = UserType.CUSTOMER
    private lateinit var prefManager: PreferenceManager
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PreferenceManager(this)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Cek apakah user sudah login
        checkIfUserLoggedIn()

        setupUserTypeTabs()
        setupLoginButton()
        setupRegisterLink()
    }

    private fun checkIfUserLoggedIn() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && prefManager.isUserLoggedIn()) {
            // User sudah login, arahkan ke dashboard
            val userType = prefManager.getString("user_type") ?: UserType.CUSTOMER.name
            val intent = if (userType == UserType.CUSTOMER.name) {
                Intent(this, CustomerDashboardActivity::class.java)
            } else {
                Intent(this, WorkerDashboardActivity::class.java)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupUserTypeTabs() {
        binding.tabsUserType.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                selectedUserType = if (tab?.position == 0) UserType.CUSTOMER else UserType.WORKER
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}

            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun setupLoginButton() {
        binding.btnLogin.setOnClickListener {
            val phoneNumber = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validateInputs(phoneNumber, password)) {
                attemptLogin(phoneNumber, password)
            }
        }
    }

    private fun validateInputs(phoneNumber: String, password: String): Boolean {
        var isValid = true

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

        return isValid
    }

    private fun attemptLogin(phoneNumber: String, password: String) {
        showLoading(true)

        // Buat email dari nomor telepon untuk Firebase Auth
        val email = "${phoneNumber}@kuliapp.com"

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    user?.let { firebaseUser ->
                        // Ambil data user dari Firestore untuk verifikasi tipe user
                        getUserDataFromFirestore(firebaseUser.uid, phoneNumber)
                    }
                } else {
                    showLoading(false)
                    val errorMessage = when {
                        task.exception?.message?.contains("user not found") == true ||
                                task.exception?.message?.contains("invalid-email") == true ||
                                task.exception?.message?.contains("user-not-found") == true ->
                            "Nomor telepon tidak terdaftar"
                        task.exception?.message?.contains("wrong-password") == true ||
                                task.exception?.message?.contains("invalid-credential") == true ->
                            "Password salah"
                        task.exception?.message?.contains("too-many-requests") == true ->
                            "Terlalu banyak percobaan login. Coba lagi nanti"
                        else -> "Login gagal: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun getUserDataFromFirestore(userId: String, phoneNumber: String) {
        // Coba cari di collection customers terlebih dahulu
        firestore.collection("customers")
            .document(userId)
            .get()
            .addOnSuccessListener { customerDoc ->
                if (customerDoc.exists()) {
                    // User adalah customer
                    if (selectedUserType == UserType.CUSTOMER) {
                        saveUserDataAndNavigate(customerDoc.data!!, UserType.CUSTOMER)
                    } else {
                        showLoading(false)
                        Toast.makeText(this, "Akun ini terdaftar sebagai Customer. Silakan pilih tab Customer untuk login.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Coba cari di collection workers
                    firestore.collection("workers")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { workerDoc ->
                            if (workerDoc.exists()) {
                                // User adalah worker
                                if (selectedUserType == UserType.WORKER) {
                                    saveUserDataAndNavigate(workerDoc.data!!, UserType.WORKER)
                                } else {
                                    showLoading(false)
                                    Toast.makeText(this, "Akun ini terdaftar sebagai Worker. Silakan pilih tab Worker untuk login.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                showLoading(false)
                                Toast.makeText(this, "Data user tidak ditemukan. Silakan daftar terlebih dahulu.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            Toast.makeText(this, "Gagal mengambil data: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Gagal mengambil data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveUserDataAndNavigate(userData: Map<String, Any>, userType: UserType) {
        showLoading(false)

        // Simpan informasi pengguna ke preferensi lokal
        prefManager.setUserLoggedIn(true)
        prefManager.setUserType(userType.name)
        prefManager.setString("user_id", userData["userId"] as String)
        prefManager.setString("user_name", userData["name"] as String)
        prefManager.setString("user_phone", userData["phoneNumber"] as String)
//        prefManager.setString("user_email", userData["email"] as String)

        Toast.makeText(this, "Login berhasil!", Toast.LENGTH_SHORT).show()

        // Navigasi ke activity berdasarkan tipe pengguna
        val intent = if (userType == UserType.CUSTOMER) {
            Intent(this, CustomerDashboardActivity::class.java)
        } else {
            Intent(this, WorkerDashboardActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        binding.btnLogin.isEnabled = !show
        binding.btnLogin.text = if (show) "Masuk..." else "Masuk"

        // Jika Anda memiliki progress bar, tampilkan/sembunyikan di sini
        // binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupRegisterLink() {
        binding.tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}