package com.yalazas.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoradoresProductoTest {
    @Test
    fun ofertaYSalsaExtraSeComponenSinCambiarElProductoBase() {
        val base = Product(id = "alitas", name = "Alitas BBQ", price = 20.0, maxSauces = 2)
        val promocion = PromocionProducto(
            productId = base.id,
            offerPrice = 15.0,
            offerTag = "Oferta del día"
        )
        val productoVisible = promocion.aplicarA(base)
        val articulo = ArticuloCarrito(
            product = productoVisible,
            quantity = 2,
            selectedSauces = listOf("BBQ", "Acevichada", "Búfalo")
        )

        assertEquals(20.0, base.price, 0.001)
        assertFalse(base.isOffer)
        assertEquals(15.0, productoVisible.price, 0.001)
        assertEquals(1.0, articulo.recargoSalsasPorUnidad, 0.001)
        assertEquals(16.0, articulo.precioUnitarioFinal, 0.001)
        assertEquals(32.0, articulo.subtotal, 0.001)
        assertEquals(10.0, articulo.totalSavings, 0.001)
        assertTrue(articulo.descripcionFinal.contains("Oferta del día"))
        assertTrue(articulo.descripcionFinal.contains("Búfalo"))
    }

    @Test
    fun salsasIncluidasNoTienenRecargoYPromocionInvalidaNoSeAplica() {
        val base = Product(id = "alitas", name = "Alitas", price = 20.0, maxSauces = 2)
        val promocionInvalida = PromocionProducto(productId = base.id, offerPrice = 20.0)
        val articulo = ArticuloCarrito(base, 1, selectedSauces = listOf("BBQ", "Ajo"))

        assertEquals(base, promocionInvalida.aplicarA(base))
        assertEquals(0.0, articulo.recargoSalsasPorUnidad, 0.001)
        assertEquals(20.0, articulo.subtotal, 0.001)
    }
}
