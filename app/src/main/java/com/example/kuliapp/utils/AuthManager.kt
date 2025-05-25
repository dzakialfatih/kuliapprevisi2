//package com.example.kuliapp.utils
//
//import android.content.Context
//import android.content.Intent
//import com.example.kuliapp.ui.auth.LoginActivity
//import com.google.firebase.auth.FirebaseAuth
//
//class AuthManager(private val context: Context) {
//
//    private val prefManager = PreferenceManager(context)
//    private val firebaseAuth = FirebaseAuth.getInstance()
//
//    /**
//     * Logout user dari Firebase dan clear local preferences
//     */
//    fun logout() {
//        // Sign out dari Firebase
//        firebaseAuth.signOut()
//
//        // Clear semua data dari SharedPreferences
//        prefManager.clearAll()
//
//        // Redirect ke LoginActivity
//        val intent = Intent(context, LoginActivity::class.java)
//        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//        context.startActivity(intent)
//    }
//
//    /**
//     * Cek apakah user masih terautentikasi
//     */
//    fun isUserAuthenticated(): Boolean {
//        return firebaseAuth.currentUser != null && prefManager.isUserLoggedIn()
//    }
//
//    /**
//     * Get current user info
//     */
//    fun getCurrentUserInfo(): UserInfo? {
//        if (!isUserAuthenticated()) return null
//
//        return UserInfo(
//            userId = prefManager.getString("user_id", ""),
//            name = prefManager.getString("user_name", ""),
//            phoneNumber = prefManager.getString("user_phone", ""),
//            email = prefManager.getString("user_email", ""),
//            userType = UserType.valueOf(prefManager.getString("user_type", UserType.CUSTOMER.name))
//        )
//    }
//
//    data class UserInfo(
//        val userId: String,
//        val name: String,
//        val phoneNumber: String,
//        val email: String,
//        val userType: UserType
//    )
//}