package com.yalazas.app.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val role: String = "CLIENTE",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val reference: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
