package com.example.kuliapp.models

data class Order(
    val id: String,
    val customerName: String,
    val customerPhone: String,
    val address: String,
    val jobType: String,
    val date: String,
    val status: String
)