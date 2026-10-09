package com.yalazas.app.utils

import com.yalazas.app.model.Order
import kotlin.math.max

object DeliveryRouteOptimizer {

    /**
     * Optimiza la secuencia de entrega para una lista de pedidos seleccionados.
     * Considera la distancia geográfica desde el local de Yalazas (y entre paradas)
     * combinada con la urgencia del tiempo de espera del cliente (minutos transcurridos desde que se hizo el pedido).
     *
     * @param orders Lista de pedidos a ordenar.
     * @param storeLat Latitud del local central (por defecto Yalazas Imperial).
     * @param storeLng Longitud del local central.
     * @return Lista de pedidos ordenados con su `routeOrderIndex` asignado secuencialmente (1, 2, 3...).
     */
    fun optimizeDeliverySequence(
        orders: List<Order>,
        storeLat: Double = DeliveryPricingHelper.STORE_LATITUDE,
        storeLng: Double = DeliveryPricingHelper.STORE_LONGITUDE
    ): List<Order> {
        if (orders.size <= 1) {
            return orders.mapIndexed { index, order ->
                order.copy(routeOrderIndex = index + 1)
            }
        }

        val now = System.currentTimeMillis()
        val remaining = orders.toMutableList()
        val optimizedList = mutableListOf<Order>()

        var currentLat = storeLat
        var currentLng = storeLng

        var stopIndex = 1

        while (remaining.isNotEmpty()) {
            // Evaluamos cada candidato con una función de costo combinada:
            // Costo = Distancia (km) - FactorUrgencia * MinutosDeEspera
            // Esto prioriza clientes cercanos, pero si un cliente lleva esperando mucho tiempo, se le da alta prioridad.
            var bestCandidate: Order? = null
            var bestScore = Double.MAX_VALUE

            for (candidate in remaining) {
                val candidateLat = if (candidate.latitude != 0.0) candidate.latitude else storeLat
                val candidateLng = if (candidate.longitude != 0.0) candidate.longitude else storeLng

                // 1. Distancia desde la posición actual hasta este pedido
                val distanceKm = DeliveryPricingHelper.calculateDistanceKm(
                    lat1 = currentLat,
                    lon1 = currentLng,
                    lat2 = candidateLat,
                    lon2 = candidateLng
                )

                // 2. Tiempo transcurrido en minutos
                val orderTime = candidate.timestamp?.toDate()?.time ?: now
                val elapsedMinutes = max(0L, (now - orderTime) / (1000 * 60)).toDouble()

                // Factor de urgencia: cada 10 minutos de espera equivalen a restarle ~1.5 km de distancia percibida
                val urgencyWeight = 0.15
                val score = distanceKm - (elapsedMinutes * urgencyWeight)

                if (score < bestScore) {
                    bestScore = score
                    bestCandidate = candidate
                }
            }

            val chosen = bestCandidate ?: remaining.first()
            remaining.remove(chosen)

            // Actualizamos la posición actual al destino elegido
            if (chosen.latitude != 0.0 && chosen.longitude != 0.0) {
                currentLat = chosen.latitude
                currentLng = chosen.longitude
            }

            optimizedList.add(chosen.copy(routeOrderIndex = stopIndex))
            stopIndex++
        }

        return optimizedList
    }

    /**
     * Retorna una medalla o identificador visual para el número de parada
     */
    fun getStopBadgeText(stopIndex: Int, totalStops: Int = 0): String {
        return when (stopIndex) {
            1 -> "Parada #1 🥇"
            2 -> "Parada #2 🥈"
            3 -> "Parada #3 🥉"
            else -> if (totalStops > 0) "Parada #$stopIndex de $totalStops" else "Parada #$stopIndex"
        }
    }
}
