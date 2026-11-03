package com.yalazas.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanciaSeguimientoTest {
    @Test
    fun `la distancia es cero en el mismo punto`() {
        assertEquals(0.0, DistanciaSeguimiento.metros(-13.0618, -76.3520, -13.0618, -76.3520), 0.01)
    }

    @Test
    fun `un cambio de una centesima de latitud equivale aproximadamente a un kilometro`() {
        val metros = DistanciaSeguimiento.metros(-13.06, -76.35, -13.05, -76.35)
        assertTrue(metros in 1_100.0..1_120.0)
        assertEquals("1.1 km", DistanciaSeguimiento.formatear(metros))
    }
}
