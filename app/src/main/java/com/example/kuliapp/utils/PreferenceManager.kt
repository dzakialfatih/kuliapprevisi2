package com.example.kuliapp.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility class to manage SharedPreferences operations
 */
class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCE_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFERENCE_NAME = "KuliAppPrefs"

        // Preference keys
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_ADDRESS = "user_address"
        private const val KEY_USER_TYPE = "user_type" // "customer" or "worker"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    // String preferences
    fun setString(key: String, value: String) {
        val editor = sharedPreferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun getString(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    // Boolean preferences
    fun setBoolean(key: String, value: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    // Integer preferences
    fun setInt(key: String, value: Int) {
        val editor = sharedPreferences.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    // Common user preferences
    fun setUserLoggedIn(isLoggedIn: Boolean) {
        setBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
    }

    fun isUserLoggedIn(): Boolean {
        return getBoolean(KEY_IS_LOGGED_IN)
    }

    fun setUserId(userId: String) {
        setString(KEY_USER_ID, userId)
    }

    fun getUserId(): String? {
        return getString(KEY_USER_ID)
    }

    fun setUserType(userType: String) {
        setString(KEY_USER_TYPE, userType)
    }

    fun getUserType(): String? {
        return getString(KEY_USER_TYPE)
    }

    fun clearAllPreferences() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }


}