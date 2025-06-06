package com.example.kuliapp.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import java.io.Serializable
import java.util.Date

data class User(

    @PropertyName("customerId")
    val customerId: String = "",

    @PropertyName("customerName")
    val customerName: String = "",

    @PropertyName("phoneNumber")
    val phoneNumber: String = "",

    @PropertyName("photo")
    val photo: String = "",

    @PropertyName("userType")
    val userType: String = "", // "CUSTOMER" atau "WORKER"

    @PropertyName("address")
    val address: String = "",

    @PropertyName("createdAt")
    val _createdAt: Any? = null

) : Serializable {

    // Getter untuk createdAt sebagai Timestamp
    val createdAt: Timestamp?
        get() = convertToTimestamp(_createdAt)

    private fun convertToTimestamp(value: Any?): Timestamp? {
        return when (value) {
            is Timestamp -> value
            is Long -> {
                if (value > 1000000000000L) {
                    Timestamp(Date(value))
                } else {
                    Timestamp(value, 0)
                }
            }
            is Date -> Timestamp(value)
            is Map<*, *> -> {
                val seconds = value["seconds"] as? Long
                val nanoseconds = value["nanoseconds"] as? Int ?: 0
                if (seconds != null) {
                    Timestamp(seconds, nanoseconds)
                } else null
            }
            else -> null
        }
    }

    // Helper untuk dapatkan timestamp dalam bentuk Long
    fun getCreatedAtLong(): Long {
        return createdAt?.seconds ?: 0
    }

    // Helper tambahan jika dibutuhkan
    fun getUserTypeDisplay(): String {
        return when (userType.uppercase()) {
            "CUSTOMER" -> "Pencari Jasa"
            "WORKER" -> "Penyedia Jasa"
            else -> "Tidak Diketahui"
        }
    }
}
