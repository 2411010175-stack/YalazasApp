package com.yalazas.app.repositorio

import com.yalazas.app.model.ArticuloCarrito
import com.yalazas.app.model.Product
import com.yalazas.app.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ConstructorPedidoTest {
    private val cliente = UserProfile(uid = "cliente-1", name = "Cliente")

    @Test
    fun pedidoConVariosProductosConservaTotalYDatosAunqueCambieElCarrito() {
        val salsas = mutableListOf("BBQ")
        val primerProducto = Product(id = "alitas", price = 20.0, maxSauces = 1)
        val primerArticulo = ArticuloCarrito(primerProducto, 2, selectedSauces = salsas)
        val segundoArticulo = ArticuloCarrito(Product(id = "bebida", price = 8.5), 1)

        val solicitud = ConstructorPedido.paraCliente(cliente)
            .agregarArticulo(primerArticulo)
            .agregarArticulo(segundoArticulo)
            .configurarEntrega("Calle 1", "Puerta azul", -12.0, -77.0, true, 1.0, 3.0)
            .agregarComentario("  Sin picante  ")
            .configurarPago("EFECTIVO", 60.0)
            .construir()

        primerArticulo.quantity = 5
        primerProducto.price = 100.0
        salsas.add("Acevichada")

        assertEquals(2, solicitud.items.size)
        assertEquals(51.5, solicitud.totalAmount, 0.001)
        assertEquals(60.0, solicitud.paymentAmount, 0.001)
        assertEquals(2, solicitud.items[0].quantity)
        assertEquals(listOf("BBQ"), solicitud.items[0].selectedSauces)
        assertEquals("Sin picante", solicitud.comment)
    }

    @Test
    fun pagoDigitalUsaElTotalCalculadoParaRetiroEnLocal() {
        val solicitud = ConstructorPedido.paraCliente(cliente)
            .agregarArticulo(ArticuloCarrito(Product(id = "combo", price = 18.0), 2))
            .configurarEntrega("Local Yalazas", "", -12.0, -77.0, false, 0.0, 0.0)
            .configurarPago("YAPE")
            .construir()

        assertEquals(36.0, solicitud.totalAmount, 0.001)
        assertEquals(36.0, solicitud.paymentAmount, 0.001)
        assertEquals(false, solicitud.isDelivery)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noConstruyeUnPedidoSinProductos() {
        ConstructorPedido.paraCliente(cliente)
            .configurarEntrega("Calle 1", "", -12.0, -77.0, true, 1.0, 3.0)
            .configurarPago("YAPE")
            .construir()
    }
}
