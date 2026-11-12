package com.yalazas.app.utilidades

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Calcula distancias y tiempos; delega la tarifa a la estrategia de entrega elegida. */
object CalculadoraTarifaEntrega {

    // Ubicación Central de Yalazas Fast Food (Imperial, Cañete, Perú)
    const val LATITUD_LOCAL = -13.061851
    const val LONGITUD_LOCAL = -76.352075
    const val NOMBRE_LOCAL = "Yalazas Fast Food - Local Central"
    const val DIRECCION_LOCAL = "Av. Ramos 318, Imperial, Cañete"

    private val tarifaRecojo: EstrategiaTarifaEntrega = TarifaRetiroEnLocal

    /**
     * Calcula la distancia en kilómetros usando la fórmula de Haversine
     */
    fun calcularDistanciaKm(
        lat1: Double = LATITUD_LOCAL,
        lon1: Double = LONGITUD_LOCAL,
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

    /** Selecciona la estrategia según la modalidad sin cambiar a quienes llaman este método. */
    fun calcularTarifaEntrega(
        distanceKm: Double, isDelivery: Boolean,
        configuracion: ConfiguracionTarifaEntrega = ConfiguracionTarifaEntrega()
    ): Double {
        val estrategia = if (isDelivery) TarifaPorDistancia(configuracion) else tarifaRecojo
        return calcularTarifaEntrega(distanceKm, estrategia)
    }

    /** Permite usar otra política de tarifas sin modificar el cálculo de distancia ni la UI. */
    fun calcularTarifaEntrega(distanceKm: Double, estrategia: EstrategiaTarifaEntrega): Double =
        estrategia.calcular(distanceKm)

    /**
     * Formatea la distancia de manera legible (ej. "850 m" o "2.4 km")
     */
    fun formatearDistancia(distanceKm: Double): String {
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
    fun estimarTiempoEntrega(distanceKm: Double, isDelivery: Boolean): String {
        if (!isDelivery) return "10 - 15 min (Recojo en local)"

        val baseMinutes = 20
        val extraMinutes = (distanceKm * 4).toInt()
        val minTime = baseMinutes + extraMinutes
        val maxTime = minTime + 10
        return "$minTime - $maxTime min"
    }
}
