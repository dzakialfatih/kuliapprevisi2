package com.example.kuliapp.utils

import android.util.Patterns
import java.util.regex.Pattern

class ValidationUtils {
    companion object {
        /**
         * Validasi nomor telepon Indonesia
         * - Harus dimulai dengan angka 0
         * - Panjang total minimal 10 dan maksimal 13 digit
         */
        fun isValidPhoneNumber(phoneNumber: String): Boolean {
            val regex = Regex("^(0)[0-9]{9,12}$")
            return regex.matches(phoneNumber)
        }
    }

//    /**
//     * Validates if the email is in correct format
//     * @param email The email to validate
//     * @return True if valid, false otherwise
//     */
//    fun isValidEmail(email: String): Boolean {
//        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
//    }
//
//    /**
//     * Validates if the password meets minimum requirements
//     * @param password The password to validate
//     * @return True if valid, false otherwise
//     */
//    fun isValidPassword(password: String): Boolean {
//        return password.length >= 6
//    }
}