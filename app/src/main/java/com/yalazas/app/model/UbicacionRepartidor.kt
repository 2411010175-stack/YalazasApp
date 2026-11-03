package com.yalazas.app.model

import com.google.firebase.Timestamp
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Última posición compartida para un pedido en camino. Los nombres son campos de Firestore. */
data class UbicacionRepartidor(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val updatedAt: Timestamp? = null
) {
    fun esValida(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        (latitude != 0.0 || longitude != 0.0)

    fun esReciente(ahora: Long = System.currentTimeMillis()): Boolean {
        val fecha = updatedAt?.toDate()?.time ?: return false
        return fecha <= ahora + 30_000L && ahora - fecha <= 120_000L
    }
}

/** Distancia geográfica directa entre el repartidor y la dirección del pedido. */
object DistanciaSeguimiento {
    fun metros(latitudOrigen: Double, longitudOrigen: Double, latitudDestino: Double, longitudDestino: Double): Double {
        val radioTierra = 6_371_000.0
        val lat1 = Math.toRadians(latitudOrigen)
        val lat2 = Math.toRadians(latitudDestino)
        val diferenciaLat = Math.toRadians(latitudDestino - latitudOrigen)
        val diferenciaLon = Math.toRadians(longitudDestino - longitudOrigen)
        val a = sin(diferenciaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(diferenciaLon / 2).pow(2)
        return 2 * radioTierra * asin(min(1.0, sqrt(a)))
    }

    fun formatear(metros: Double): String = if (metros < 1_000) {
        "${metros.roundToInt()} m"
    } else {
        "${String.format(java.util.Locale.US, "%.1f", metros / 1_000)} km"
    }
}
