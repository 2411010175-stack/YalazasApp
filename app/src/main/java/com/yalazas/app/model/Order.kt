package com.yalazas.app.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

data class Order(
    var id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val phone: String = "",
    val address: String = "",
    val reference: String = "",
    val comment: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val total: Double = 0.0,
    var status: String = "PENDIENTE",
    val items: List<OrderItem> = emptyList(),
    val timestamp: Timestamp? = null,
    val paymentMethod: String = "EFECTIVO",
    val paymentAmount: Double = 0.0,
    val paymentChange: Double = 0.0,
    val paymentStatus: String = "PENDIENTE",
    val deliveryDriverId: String = "",
    val deliveryDriverName: String = "",
    val deliveryDriverPhone: String = "",
    val isDelivery: Boolean = true,
    val deliveryDistanceKm: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val routeOrderIndex: Int = 0
)

@Parcelize
data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val subtotal: Double = 0.0,
    val notes: String = "",
    val selectedSauces: List<String> = emptyList()
) : Parcelable
