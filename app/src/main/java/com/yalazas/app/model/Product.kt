package com.yalazas.app.model

import android.os.Parcelable
import com.google.firebase.firestore.Exclude
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt

@Parcelize
data class Product(
    var id: String = "",
    var name: String = "",
    var tagline: String = "",
    var description: String = "",
    var price: Double = 0.0,
    var originalPrice: Double = 0.0,
    var isOffer: Boolean = false,
    var offerTag: String = "",
    var imageUrl: String = "",
    var category: String = "",
    var isAvailable: Boolean = true,
    var allowsSauces: Boolean = false,
    var maxSauces: Int = 0,
    var availableSauces: List<String> = emptyList()
) : Parcelable {

    @get:Exclude
    val hasDiscount: Boolean
        get() = isOffer && originalPrice > price && originalPrice > 0.0

    @get:Exclude
    val discountPercent: Int
        get() = if (hasDiscount) {
            (((originalPrice - price) / originalPrice) * 100).roundToInt()
        } else {
            0
        }

    @get:Exclude
    val savedAmountPerUnit: Double
        get() = if (hasDiscount) originalPrice - price else 0.0

    companion object {
        val DEFAULT_YALAZA_SAUCES = listOf(
            "Acevichada",
            "BBQ Clásica",
            "Búfalo Picante",
            "Maracuyá Dulce",
            "Honey Mustard",
            "Mango Habanero",
            "Ajo Parmesano",
            "Tártara Especial"
        )
    }
}
