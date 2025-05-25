package com.example.kuliapp.utils

import android.util.Log
import com.example.kuliapp.models.Job
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class JobStatusManager {

    companion object {
        private const val TAG = "JobStatusManager"
        private const val COLLECTION_JOBS = "jobs"

        // Job Status Constants
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_CANCELLED = "cancelled"

        fun updateJobStatus(
            firestore: FirebaseFirestore,
            jobId: String,
            newStatus: String,
            callback: (Boolean) -> Unit
        ) {
            firestore.collection(COLLECTION_JOBS)
                .document(jobId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .addOnSuccessListener {
                    Log.d(TAG, "Job status updated successfully: $jobId -> $newStatus")
                    callback(true)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error updating job status: $jobId", exception)
                    callback(false)
                }
        }

        fun acceptJob(
            firestore: FirebaseFirestore,
            jobId: String,
            callback: (Boolean) -> Unit
        ) {
            updateJobStatus(firestore, jobId, STATUS_ACCEPTED, callback)
        }

        fun startJob(
            firestore: FirebaseFirestore,
            jobId: String,
            callback: (Boolean) -> Unit
        ) {
            updateJobStatus(firestore, jobId, STATUS_IN_PROGRESS, callback)
        }

        fun completeJob(
            firestore: FirebaseFirestore,
            jobId: String,
            callback: (Boolean) -> Unit
        ) {
            updateJobStatus(firestore, jobId, STATUS_COMPLETED, callback)
        }

        fun cancelJob(
            firestore: FirebaseFirestore,
            jobId: String,
            callback: (Boolean) -> Unit
        ) {
            updateJobStatus(firestore, jobId, STATUS_CANCELLED, callback)
        }

        // Helper function untuk mendapatkan jobs berdasarkan status
        fun getJobsByStatus(
            firestore: FirebaseFirestore,
            userId: String,
            status: String,
            userType: String, // "customer" atau "worker"
            callback: (List<Job>) -> Unit
        ) {
            val field = if (userType == "customer") "customerId" else "workerId"

            firestore.collection(COLLECTION_JOBS)
                .whereEqualTo(field, userId)
                .whereEqualTo("status", status)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    val jobs = mutableListOf<Job>()
                    for (document in documents) {
                        val job = document.toObject(Job::class.java).copy(id = document.id)
                        jobs.add(job)
                    }
                    callback(jobs)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error getting jobs by status", exception)
                    callback(emptyList())
                }
        }

        // Helper function untuk mendapatkan completed jobs yang belum di-rating
        fun getUnratedCompletedJobs(
            firestore: FirebaseFirestore,
            customerId: String,
            callback: (List<Job>) -> Unit
        ) {
            firestore.collection(COLLECTION_JOBS)
                .whereEqualTo("customerId", customerId)
                .whereEqualTo("status", STATUS_COMPLETED)
                .whereEqualTo("rating", 0f)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { documents ->
                    val jobs = mutableListOf<Job>()
                    for (document in documents) {
                        val job = document.toObject(Job::class.java).copy(id = document.id)
                        jobs.add(job)

                        // LOG UNTUK DEBUG
                        Log.d(TAG, "Unrated job found: ${job.id}, workerId: ${job.workerId}, description: ${job.description}")
                    }
                    Log.d(TAG, "Total unrated completed jobs: ${jobs.size}")
                    callback(jobs)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error getting unrated completed jobs", exception)
                    callback(emptyList())
                }
        }
    }
}

