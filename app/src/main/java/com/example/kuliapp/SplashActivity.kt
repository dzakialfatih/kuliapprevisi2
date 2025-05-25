package com.example.kuliapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.kuliapp.R
import com.example.kuliapp.ui.auth.LoginActivity
import com.example.kuliapp.ui.customer.CustomerDashboardActivity
import com.example.kuliapp.ui.worker.WorkerDashboardActivity
import com.example.kuliapp.utils.PreferenceManager
import com.example.kuliapp.utils.UserType

class SplashActivity : AppCompatActivity() {

    private lateinit var prefManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        prefManager = PreferenceManager(this)

        // Tunggu 2 detik untuk menampilkan splash screen
        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatus()
        }, 2000)
    }

    private fun checkLoginStatus() {
        if (prefManager.isUserLoggedIn()) {
            // Pengguna sudah login, arahkan ke dashboard yang sesuai
            val userType = prefManager.getUserType()
            val intent = if (userType == UserType.CUSTOMER.name) {
                Intent(this, CustomerDashboardActivity::class.java)
            } else {
                Intent(this, WorkerDashboardActivity::class.java)
            }
            startActivity(intent)
        } else {
            // Pengguna belum login, arahkan ke halaman login
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}