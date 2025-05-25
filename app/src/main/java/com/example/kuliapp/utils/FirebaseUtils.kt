//package com.example.kuliapp.utils
//
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.firestore.SetOptions
//
//object FirebaseUtils {
//
//    private val firebaseAuth = FirebaseAuth.getInstance()
//    private val firestore = FirebaseFirestore.getInstance()
//
//    // Collections
//    const val CUSTOMERS_COLLECTION = "customers"
//    const val WORKERS_COLLECTION = "workers"
//    const val ORDERS_COLLECTION = "orders"
//    const val REVIEWS_COLLECTION = "reviews"
//
//    /**
//     * Get current user ID
//     */
//    fun getCurrentUserId(): String? {
//        return firebaseAuth.currentUser?.uid
//    }
//
//    /**
//     * Check if user is logged in
//     */
//    fun isUserLoggedIn(): Boolean {
//        return firebaseAuth.currentUser != null
//    }
//
//    /**
//     * Sign out user
//     */
//    fun signOut() {
//        firebaseAuth.signOut()
//    }
//
//    /**
//     * Convert phone number to email format for Firebase Auth
//     */
//    fun phoneToEmail(phoneNumber: String): String {
//        return "${phoneNumber}@kuliapp.com"
//    }
//
//    /**
//     * Get user document reference based on user type
//     */
//    fun getUserDocument(userId: String, userType: UserType) =
//        firestore.collection(
//            if (userType == UserType.CUSTOMER) CUSTOMERS_COLLECTION else WORKERS_COLLECTION
//        ).document(userId)
//
//    /**
//     * Update user profile
//     */
//    fun updateUserProfile(userId: String, userType: UserType, updates: Map<String, Any>,
//                          onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
//        getUserDocument(userId, userType)
//            .set(updates, SetOptions.merge())
//            .addOnSuccessListener { onSuccess() }
//            .addOnFailureListener { onFailure(it) }
//    }
//
//    /**
//     * Get user data
//     */
//    fun getUserData(userId: String, userType: UserType,
//                    onSuccess: (Map<String, Any>) -> Unit,
//                    onFailure: (Exception) -> Unit) {
//        getUserDocument(userId, userType)
//            .get()
//            .addOnSuccessListener { document ->
//                if (document.exists()) {
//                    document.data?.let { onSuccess(it) }
//                } else {
//                    onFailure(Exception("User data not found"))
//                }
//            }
//            .addOnFailureListener { onFailure(it) }
//    }
//
//    /**
//     * Delete user account (from Auth and Firestore)
//     */
//    fun deleteUserAccount(userId: String, userType: UserType,
//                          onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
//        // Delete from Firestore first
//        getUserDocument(userId, userType)
//            .delete()
//            .addOnSuccessListener {
//                // Then delete from Firebase Auth
//                firebaseAuth.currentUser?.delete()
//                    ?.addOnSuccessListener { onSuccess() }
//                    ?.addOnFailureListener { onFailure(it) }
//            }
//            .addOnFailureListener { onFailure(it) }
//    }
//
//    /**
//     * Create user data structure for registration
//     */
//    fun createUserData(userId: String, name: String, phoneNumber: String,
//                       email: String, userType: UserType): HashMap<String, Any> {
//        val userData = hashMapOf(
//            "userId" to userId,
//            "name" to name,
//            "phoneNumber" to phoneNumber,
//            "email" to email,
//            "userType" to userType.name,
//            "createdAt" to com.google.firebase.Timestamp.now(),
//            "updatedAt" to com.google.firebase.Timestamp.now(),
//            "isActive" to true
//        )
//
//        // Add specific fields based on user type
//        if (userType == UserType.WORKER) {
//            userData["skills"] = emptyList<String>()
//            userData["rating"] = 0.0
//            userData["totalJobs"] = 0
//            userData["completedJobs"] = 0
//            userData["isAvailable"] = true
//            userData["hourlyRate"] = 0.0
//            userData["profileImageUrl"] = ""
//            userData["description"] = ""
//        } else {
//            userData["totalOrders"] = 0
//            userData["completedOrders"] = 0
//            userData["profileImageUrl"] = ""
//            userData["address"] = ""
//        }
//
//        return userData
//    }
//
//    /**
//     * Search workers by skills
//     */
//    fun searchWorkersBySkills(skills: List<String>,
//                              onSuccess: (List<Map<String, Any>>) -> Unit,
//                              onFailure: (Exception) -> Unit) {
//        firestore.collection(WORKERS_COLLECTION)
//            .whereArrayContainsAny("skills", skills)
//            .whereEqualTo("isAvailable", true)
//            .whereEqualTo("isActive", true)
//            .get()
//            .addOnSuccessListener { documents ->
//                val workers = documents.mapNotNull { it.data }
//                onSuccess(workers)
//            }
//            .addOnFailureListener { onFailure(it) }
//    }
//
//    /**
//     * Get all available workers
//     */
//    fun getAvailableWorkers(onSuccess: (List<Map<String, Any>>) -> Unit,
//                            onFailure: (Exception) -> Unit) {
//        firestore.collection(WORKERS_COLLECTION)
//            .whereEqualTo("isAvailable", true)
//            .whereEqualTo("isActive", true)
//            .orderBy("rating", com.google.firebase.firestore.Query.Direction.DESCENDING)
//            .get()
//            .addOnSuccessListener { documents ->
//                val workers = documents.mapNotNull { it.data }
//                onSuccess(workers)
//            }
//            .addOnFailureListener { onFailure(it) }
//    }
//}