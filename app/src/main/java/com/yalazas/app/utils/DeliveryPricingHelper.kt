package com.yalazas.app.utils

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DeliveryPricingHelper {

    // Ubicación Central de Yalazas Fast Food (Imperial, Cañete, Perú)
    const val STORE_LATITUDE = -13.061851
    const val STORE_LONGITUDE = -76.352075
    const val STORE_NAME = "Yalazas Fast Food - Local Central"
    const val STORE_ADDRESS = "Av. Ramos 309, Imperial, Cañete"

    const val BASE_DELIVERY_FEE = 3.00 // Tarifa base en Soles (hasta 1.5 km)
    const val BASE_DISTANCE_KM = 1.5 // Radio base incluido
    const val EXTRA_FEE_PER_KM = 1.00 // Recargo por kilómetro adicional

    /**
     * Calcula la distancia en kilómetros usando la fórmula de Haversine
     */
    fun calculateDistanceKm(
        lat1: Double = STORE_LATITUDE,
        lon1: Double = STORE_LONGITUDE,
        lat2: Double,
        lon2: Double
    ): Double {
        if (lat2 == 0.0 && lon2 == 0.0) return 0.0

        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusKm * c
    }

    /**
     * Calcula el costo de envío según la distancia y el tipo de entrega
     */
    fun calculateDeliveryFee(distanceKm: Double, isDelivery: Boolean): Double {
        if (!isDelivery) return 0.0
        if (distanceKm <= 0.0) return BASE_DELIVERY_FEE

        return if (distanceKm <= BASE_DISTANCE_KM) {
            BASE_DELIVERY_FEE
        } else {
            val extraKm = ceil(distanceKm - BASE_DISTANCE_KM)
            BASE_DELIVERY_FEE + (extraKm * EXTRA_FEE_PER_KM)
        }
    }

    /**
     * Formatea la distancia de manera legible (ej. "850 m" o "2.4 km")
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            val meters = (distanceKm * 1000).toInt()
            "$meters m"
        } else {
            String.format(Locale.getDefault(), "%.1f km", distanceKm)
        }
    }

    /**
     * Calcula un tiempo estimado de entrega basado en distancia y preparación
     */
    fun estimateDeliveryTime(distanceKm: Double, isDelivery: Boolean): String {
        if (!isDelivery) return "10 - 15 min (Recojo en local)"

        val baseMinutes = 20
        val extraMinutes = (distanceKm * 4).toInt()
        val minTime = baseMinutes + extraMinutes
        val maxTime = minTime + 10
        return "$minTime - $maxTime min"
    }
}
