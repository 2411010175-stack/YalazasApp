package com.yalazas.app.utilidades

import org.junit.Assert.assertEquals
import org.junit.Test

class EstrategiaTarifaEntregaTest {
    @Test
    fun tarifaPorDistanciaRespetaElLimiteYElKilometroAdicionalIniciado() {
        val estrategia = TarifaPorDistancia()

        assertEquals(3.0, estrategia.calcular(0.0), 0.001)
        assertEquals(3.0, estrategia.calcular(1.5), 0.001)
        assertEquals(4.0, estrategia.calcular(1.5001), 0.001)
        assertEquals(4.0, estrategia.calcular(2.5), 0.001)
        assertEquals(5.0, estrategia.calcular(2.5001), 0.001)
    }

    @Test
    fun retiroEnLocalNoCobraEnvio() {
        assertEquals(0.0, CalculadoraTarifaEntrega.calcularTarifaEntrega(4.0, false), 0.001)
    }

    @Test
    fun calculadoraPuedeUsarOtraEstrategiaSinCambiarSuCodigo() {
        val tarifaPromocional = EstrategiaTarifaEntrega { 2.0 }

        assertEquals(2.0, CalculadoraTarifaEntrega.calcularTarifaEntrega(8.0, tarifaPromocional), 0.001)
    }
}
