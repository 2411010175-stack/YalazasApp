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

    @Test
    fun laVersionPublicadaCambiaElImporteSinCambiarLaEstrategia() {
        val configuracion = ConfiguracionTarifaEntrega(
            version = 2, baseCents = 450, includedMeters = 2000, extraKmCents = 125
        )
        assertEquals(4.50, CalculadoraTarifaEntrega.calcularTarifaEntrega(2.0, true, configuracion), 0.001)
        assertEquals(5.75, CalculadoraTarifaEntrega.calcularTarifaEntrega(2.001, true, configuracion), 0.001)
        assertEquals(0.0, CalculadoraTarifaEntrega.calcularTarifaEntrega(8.0, false, configuracion), 0.001)
    }
}
