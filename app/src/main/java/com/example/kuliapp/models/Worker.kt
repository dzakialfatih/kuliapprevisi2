package com.example.kuliapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import java.io.Serializable
import java.util.Date

data class Worker(
    @PropertyName("id")
    val id: String = "",

    @PropertyName("name")
    val name: String = "",

    @PropertyName("email")
    val email: String = "",

    @PropertyName("phone")
    val phone: String = "",

    @PropertyName("location")
    val location: String = "",

    @PropertyName("experience")
    val experience: String = "",

    @PropertyName("price")
    val price: Long = 0,

    @PropertyName("photo")
    val photo: String = "",

    @PropertyName("rating")
    val rating: Double = 0.0,

    @PropertyName("ratingCount")
    val ratingCount: Int = 0,

    @PropertyName("isAvailable")
    val isAvailable: Boolean = true,

    // Gunakan Any untuk menangani berbagai format timestamp
    @PropertyName("createdAt")
    val _createdAt: Any? = null,

    @PropertyName("updatedAt")
    val _updatedAt: Any? = null,

    // Additional fields for specific contexts
    @PropertyName("jobDate")
    val jobDate: String = "",

    @PropertyName("jobDescription")
    val jobDescription: String = "",

    @PropertyName("phoneNumber")
    val phoneNumber: String = phone // Alias for compatibility
) : Serializable {

    // HAPUS konstruktor secondary yang bermasalah - ini penyebab utama masalah!
    // Konstruktor secondary Anda me-reset semua nilai ke default

    // Public getters untuk timestamp
    val createdAt: Timestamp?
        get() = convertToTimestamp(_createdAt)

    val updatedAt: Timestamp?
        get() = convertToTimestamp(_updatedAt)

    // Helper function untuk konversi berbagai format timestamp
    private fun convertToTimestamp(value: Any?): Timestamp? {
        return when (value) {
            is Timestamp -> value
            is Long -> {
                // Jika Long adalah milliseconds
                if (value > 1000000000000L) { // > year 2001 in milliseconds
                    Timestamp(Date(value))
                } else {
                    // Jika Long adalah seconds
                    Timestamp(value, 0)
                }
            }
            is Date -> Timestamp(value)
            is Map<*, *> -> {
                // Jika data berupa Map (dari Firestore)
                val seconds = value["seconds"] as? Long
                val nanoseconds = value["nanoseconds"] as? Int ?: 0
                if (seconds != null) {
                    Timestamp(seconds, nanoseconds)
                } else null
            }
            else -> null
        }
    }

    fun getFormattedPrice(): String {
        return if (price > 0) {
            "Rp ${String.format("%,d", price)}/hari"
        } else {
            "Harga belum ditetapkan"
        }
    }

    fun getFormattedRating(): String {
        return if (rating > 0) {
            String.format("%.1f", rating)
        } else {
            "Belum dirating"
        }
    }

    fun isNewWorker(): Boolean {
        return ratingCount == 0
    }

    // Helper functions untuk mendapatkan Long timestamp
    fun getCreatedAtLong(): Long {
        return createdAt?.seconds ?: 0
    }

    fun getUpdatedAtLong(): Long {
        return updatedAt?.seconds ?: 0
    }
}

// Rating model untuk Firebase
data class Rating(
    @PropertyName("id")
    val id: String = "",

    @PropertyName("workerId")
    val workerId: String = "",

    @PropertyName("customerId")
    val customerId: String = "",

    @PropertyName("customerName")
    val customerName: String = "",

    @PropertyName("rating")
    val rating: Float = 0f,

    @PropertyName("review")
    val review: String = "",

    @PropertyName("date")
    val date: String = "",

    @PropertyName("createdAt")
    val createdAt: Timestamp? = null
) : Serializable {

    // Helper function untuk mendapatkan Long timestamp jika diperlukan
    fun getCreatedAtLong(): Long {
        return createdAt?.seconds ?: 0
    }
}

// Job model untuk Firebase
// Job model untuk Firebase - Disinkronkan dengan Worker model
data class Job(
    @PropertyName("id")
    var id: String = "",

    @PropertyName("customerId")
    var customerId: String = "",

    @PropertyName("customerName")
    var customerName: String = "",

    @PropertyName("workerId")
    var workerId: String = "",

    @PropertyName("workerName")
    var workerName: String = "",

    // Sinkronkan dengan Worker.photo
    @PropertyName("workerPhoto")
    var workerPhoto: String = "",

    // Sinkronkan dengan Worker.phone
    @PropertyName("workerPhone")
    var workerPhone: String = "",

    @PropertyName("date")
    var date: String = "",

    @PropertyName("description")
    var description: String = "",

    @PropertyName("status")
    var status: String = "", // "pending", "accepted", "in_progress", "completed", "cancelled"

    @PropertyName("rating")
    var rating: Float = 0f,

    @PropertyName("price")
    var price: Long = 0,

    @PropertyName("location")
    var location: String = "",

    // Gunakan format timestamp yang sama dengan Worker
    @PropertyName("createdAt")
    val _createdAt: Any? = null,

    @PropertyName("updatedAt")
    val _updatedAt: Any? = null
) : Serializable {

    // Public getters untuk timestamp - sama dengan Worker
    val createdAt: Timestamp?
        get() = convertToTimestamp(_createdAt)

    val updatedAt: Timestamp?
        get() = convertToTimestamp(_updatedAt)

    // Helper function untuk konversi berbagai format timestamp - sama dengan Worker
    private fun convertToTimestamp(value: Any?): Timestamp? {
        return when (value) {
            is Timestamp -> value
            is Long -> {
                // Jika Long adalah milliseconds
                if (value > 1000000000000L) { // > year 2001 in milliseconds
                    Timestamp(Date(value))
                } else {
                    // Jika Long adalah seconds
                    Timestamp(value, 0)
                }
            }
            is Date -> Timestamp(value)
            is Map<*, *> -> {
                // Jika data berupa Map (dari Firestore)
                val seconds = value["seconds"] as? Long
                val nanoseconds = value["nanoseconds"] as? Int ?: 0
                if (seconds != null) {
                    Timestamp(seconds, nanoseconds)
                } else null
            }
            else -> null
        }
    }

    // Helper function untuk format rating - sama dengan Worker
    fun getFormattedRating(): String {
        return if (rating > 0) {
            String.format("%.1f", rating)
        } else {
            "Belum dirating"
        }
    }

    // Helper function untuk format price - konsisten dengan Worker
    fun getFormattedPrice(): String {
        return if (price > 0) {
            "Rp ${String.format("%,d", price)}/hari"
        } else {
            "Harga belum ditetapkan"
        }
    }

    // Helper function untuk format status
    fun getStatusText(): String {
        return when (status) {
            "pending" -> "Menunggu Konfirmasi"
            "accepted" -> "Diterima"
            "in_progress" -> "Sedang Dikerjakan"
            "completed" -> "Selesai"
            "cancelled" -> "Dibatalkan"
            else -> status
        }
    }

    // Helper functions untuk mendapatkan Long timestamp - sama dengan Worker
    fun getCreatedAtLong(): Long {
        return createdAt?.seconds ?: 0
    }

    fun getUpdatedAtLong(): Long {
        return updatedAt?.seconds ?: 0
    }

    // Helper function untuk membuat Job dari Worker
    companion object {
        fun fromWorker(
            worker: Worker,
            customerId: String,
            customerName: String,
            date: String,
            description: String,
            status: String = "pending"
        ): Job {
            return Job(
                workerId = worker.id,
                workerName = worker.name,
                workerPhoto = worker.photo,
                workerPhone = worker.phone,
                price = worker.price,
                location = worker.location,
                customerId = customerId,
                customerName = customerName,
                date = date,
                description = description,
                status = status,
                _createdAt = Timestamp.now(),
                _updatedAt = Timestamp.now()
            )
        }
    }
}