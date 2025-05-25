package com.example.kuliapp.repository

import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.example.kuliapp.models.User
import com.example.kuliapp.utils.UserType
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Phone Authentication
    fun sendVerificationCode(
        phoneNumber: String,
        activity: android.app.Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // Verify OTP and Sign In
    suspend fun verifyOtpAndSignIn(verificationId: String, otp: String): AuthResult {
        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        return auth.signInWithCredential(credential).await()
    }

    // Save user to Firestore
    suspend fun saveUserToFirestore(user: User) {
        firestore.collection("users")
            .document(user.id)
            .set(user)
            .await()
    }

    // Get user from Firestore
    suspend fun getUserFromFirestore(userId: String): User? {
        return try {
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                document.toObject(User::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Check if user exists
    suspend fun checkUserExists(phoneNumber: String): User? {
        return try {
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                querySnapshot.documents[0].toObject(User::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Get current user
    fun getCurrentUser() = auth.currentUser

    // Sign out
    fun signOut() = auth.signOut()
}