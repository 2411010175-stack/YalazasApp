package com.yalazas.app.utilidades

import kotlin.math.ceil

/** Contrato para calcular una tarifa según la modalidad de entrega. */
fun interface EstrategiaTarifaEntrega {
    fun calcular(distanciaKm: Double): Double
}

/** S/ 3 hasta 1,5 km y S/ 1 por cada kilómetro adicional iniciado. */
class TarifaPorDistancia(
    private val tarifaBase: Double = 3.0,
    private val distanciaIncluidaKm: Double = 1.5,
    private val costoKmAdicional: Double = 1.0
) : EstrategiaTarifaEntrega {
    override fun calcular(distanciaKm: Double): Double {
        if (distanciaKm <= distanciaIncluidaKm) return tarifaBase
        val kilometrosAdicionales = ceil(distanciaKm - distanciaIncluidaKm)
        return tarifaBase + kilometrosAdicionales * costoKmAdicional
    }
}

/** El cliente recoge su pedido en el local, por lo que no hay costo de envío. */
object TarifaRetiroEnLocal : EstrategiaTarifaEntrega {
    override fun calcular(distanciaKm: Double): Double = 0.0
}
