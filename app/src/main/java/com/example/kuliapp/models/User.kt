package com.example.kuliapp.models

//import com.google.firebase.Timestamp

//data class User(
//    val id: String,
//    val name: String,
//    val phoneNumber: String,
//    val photo: String = "",
//    val userType: String, // "customer" or "worker"
//    val address: String = ""
//)

data class User(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val photo: String = "",
    val userType: String = "", // "CUSTOMER" or "WORKER"
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    // Constructor kosong untuk Firebase
    constructor() : this("", "", "", "", "", "", 0L)
}