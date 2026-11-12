package com.yalazas.app.utilidades

import kotlin.math.ceil

/** Contrato para calcular una tarifa según la modalidad de entrega. */
fun interface EstrategiaTarifaEntrega {
    fun calcular(distanciaKm: Double): Double
}

/** Montos en céntimos y distancia en metros evitan errores de redondeo al guardar. */
data class ConfiguracionTarifaEntrega(
    val version: Int = 0,
    val baseCents: Int = 300,
    val includedMeters: Int = 1500,
    val extraKmCents: Int = 100
) {
    fun valida(): Boolean = version >= 0 && baseCents in 0..10000 &&
        includedMeters in 1..50000 && extraKmCents in 0..10000
}

/** Política por distancia; la configuración llega de Firestore sin cambiar el algoritmo. */
class TarifaPorDistancia(
    private val configuracion: ConfiguracionTarifaEntrega = ConfiguracionTarifaEntrega()
) : EstrategiaTarifaEntrega {
    override fun calcular(distanciaKm: Double): Double {
        require(configuracion.valida() && distanciaKm.isFinite() && distanciaKm >= 0.0)
        val extra = ceil(distanciaKm - configuracion.includedMeters / 1000.0).toInt().coerceAtLeast(0)
        return (configuracion.baseCents + extra * configuracion.extraKmCents) / 100.0
    }
}

/** El cliente recoge su pedido en el local, por lo que no hay costo de envío. */
object TarifaRetiroEnLocal : EstrategiaTarifaEntrega {
    override fun calcular(distanciaKm: Double): Double = 0.0
}
