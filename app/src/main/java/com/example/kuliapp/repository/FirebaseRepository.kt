package com.example.kuliapp.repository

import android.util.Log
import com.example.kuliapp.models.Job
import com.example.kuliapp.models.Rating
import com.example.kuliapp.models.Worker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    companion object {
        private const val TAG = "FirebaseRepository"
        private const val COLLECTION_WORKERS = "workers"
        private const val COLLECTION_RATINGS = "ratings"
        private const val COLLECTION_JOBS = "jobs"
        private const val COLLECTION_CUSTOMERS = "customers"

        @Volatile
        private var INSTANCE: FirebaseRepository? = null

        fun getInstance(): FirebaseRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseRepository().also { INSTANCE = it }
            }
        }
    }

    // Worker Operations
    suspend fun createOrUpdateWorker(worker: Worker): Result<Worker> {
        return try {
            firestore.collection(COLLECTION_WORKERS)
                .document(worker.workerId)
                .set(worker)
                .await()
            Result.success(worker)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating worker", e)
            Result.failure(e)
        }
    }

    suspend fun getWorker(workerId: String): Result<Worker?> {
        return try {
            val document = firestore.collection(COLLECTION_WORKERS)
                .document(workerId)
                .get()
                .await()

            val worker = if (document.exists()) {
                document.toObject(Worker::class.java)
            } else {
                null
            }
            Result.success(worker)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting worker", e)
            Result.failure(e)
        }
    }

    suspend fun getAllAvailableWorkers(): Result<List<Worker>> {
        return try {
            val documents = firestore.collection(COLLECTION_WORKERS)
                .whereEqualTo("isAvailable", true)
                .get()
                .await()

            val workers = documents.map { it.toObject(Worker::class.java) }
                .filter { it.name.isNotEmpty() }

            Result.success(workers)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available workers", e)
            Result.failure(e)
        }
    }

    suspend fun updateWorkerAvailability(workerId: String, isAvailable: Boolean): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_WORKERS)
                .document(workerId)
                .update(
                    mapOf(
                        "isAvailable" to isAvailable,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating worker availability", e)
            Result.failure(e)
        }
    }

    // Rating Operations
    suspend fun submitRating(rating: Rating): Result<Rating> {
        return try {
            val ratingId = if (rating.workerId.isEmpty()) {
                firestore.collection(COLLECTION_RATINGS).document().id
            } else {
                rating.workerId
            }

            val ratingWithId = rating.copy(
                workerId = ratingId,
                _createdAt = Timestamp.now()
            )

            firestore.collection(COLLECTION_RATINGS)
                .document(ratingId)
                .set(ratingWithId)
                .await()

            // Update worker's rating
            updateWorkerRating(rating.workerId)

            Result.success(ratingWithId)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting rating", e)
            Result.failure(e)
        }
    }

    suspend fun getWorkerRatings(workerId: String): Result<List<Rating>> {
        return try {
            val documents = firestore.collection(COLLECTION_RATINGS)
                .whereEqualTo("workerId", workerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val ratings = documents.map { it.toObject(Rating::class.java) }
            Result.success(ratings)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting worker ratings", e)
            Result.failure(e)
        }
    }

    private suspend fun updateWorkerRating(workerId: String) {
        try {
            val ratingsResult = getWorkerRatings(workerId)
            if (ratingsResult.isSuccess) {
                val ratings = ratingsResult.getOrNull() ?: emptyList()

                val averageRating = if (ratings.isNotEmpty()) {
                    ratings.map { it.rating }.average().toFloat()  // rating diasumsikan Float
                } else {
                    0f
                }

                firestore.collection(COLLECTION_WORKERS)
                    .document(workerId)
                    .update(
                        mapOf(
                            "rating" to averageRating,
                            "ratingCount" to ratings.size,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating worker rating", e)
        }
    }


    // Job Operations
    suspend fun createJob(job: Job): Result<Job> {
        return try {
            val jobId = if (job.workerId.isEmpty()) {
                firestore.collection(COLLECTION_JOBS).document().id
            } else {
                job.workerId
            }

            val jobWithId = job.copy(
                workerId = jobId,
                _createdAt = Timestamp.now(),
                _updatedAt = Timestamp.now()
            )

            firestore.collection(COLLECTION_JOBS)
                .document(jobId)
                .set(jobWithId)
                .await()

            Result.success(jobWithId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating job", e)
            Result.failure(e)
        }
    }

    suspend fun updateJobStatus(jobId: String, status: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_JOBS)
                .document(jobId)
                .update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating job status", e)
            Result.failure(e)
        }
    }

    suspend fun updateJobRating(jobId: String, rating: Float): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_JOBS)
                .document(jobId)
                .update(
                    mapOf(
                        "rating" to rating,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating job rating", e)
            Result.failure(e)
        }
    }

    suspend fun getCustomerJobs(customerId: String, status: String? = null): Result<List<Job>> {
        return try {
            var query = firestore.collection(COLLECTION_JOBS)
                .whereEqualTo("customerId", customerId)

            if (status != null) {
                query = query.whereEqualTo("status", status)
            }

            val documents = query
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val jobs = documents.map {
                it.toObject(Job::class.java).copy(workerId = it.id)
            }

            Result.success(jobs)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting customer jobs", e)
            Result.failure(e)
        }
    }

    suspend fun getWorkerJobs(workerId: String, status: String? = null): Result<List<Job>> {
        return try {
            var query = firestore.collection(COLLECTION_JOBS)
                .whereEqualTo("workerId", workerId)

            if (status != null) {
                query = query.whereEqualTo("status", status)
            }

            val documents = query
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val jobs = documents.map {
                it.toObject(Job::class.java).copy(workerId = it.id)
            }

            Result.success(jobs)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting worker jobs", e)
            Result.failure(e)
        }
    }

    suspend fun getUnratedJobs(customerId: String): Result<List<Job>> {
        return try {
            val documents = firestore.collection(COLLECTION_JOBS)
                .whereEqualTo("customerId", customerId)
                .whereEqualTo("status", "completed")
                .whereEqualTo("rating", 0f)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            val jobs = documents.map {
                it.toObject(Job::class.java).copy(workerId = it.id)
            }

            Result.success(jobs)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unrated jobs", e)
            Result.failure(e)
        }
    }

    // Search Operations
    suspend fun searchWorkersByLocation(location: String): Result<List<Worker>> {
        return try {
            val documents = firestore.collection(COLLECTION_WORKERS)
                .whereEqualTo("isAvailable", true)
                .orderBy("location")
                .startAt(location)
                .endAt(location + "\uf8ff")
                .get()
                .await()

            val workers = documents.map { it.toObject(Worker::class.java) }
                .filter { it.name.isNotEmpty() }

            Result.success(workers)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching workers by location", e)
            Result.failure(e)
        }
    }

    suspend fun getTopRatedWorkers(limit: Int = 10): Result<List<Worker>> {
        return try {
            val documents = firestore.collection(COLLECTION_WORKERS)
                .whereEqualTo("isAvailable", true)
                .whereGreaterThan("ratingCount", 0)
                .orderBy("ratingCount")
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val workers = documents.map { it.toObject(Worker::class.java) }
            Result.success(workers)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top rated workers", e)
            Result.failure(e)
        }
    }

    // Utility functions
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    fun isUserLoggedIn(): Boolean = auth.currentUser != null
}