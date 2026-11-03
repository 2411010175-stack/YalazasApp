package com.yalazas.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoPedidoTest {
    @Test fun cocinaSoloPuedeAvanzarSusDosEtapas() {
        assertEquals(listOf(OrderStatus.EN_PROCESO), EstadoPedido.Pendiente.siguientes(Role.COCINERO))
        assertEquals(listOf(OrderStatus.LISTO_PARA_ENTREGA), EstadoPedido.EnProceso.siguientes(Role.COCINERO))
        assertFalse(EstadoPedido.permite("EN_PROCESO", OrderStatus.EN_CAMINO, Role.COCINERO))
        assertTrue(EstadoPedido.permite("LISTO_PARA_ENTREGA", OrderStatus.EN_PROCESO, Role.COCINERO))
    }

    @Test fun clienteSoloCancelaPendienteYRepartidorEsperaListo() {
        assertTrue(EstadoPedido.permite("PENDIENTE", OrderStatus.CANCELADO, Role.CLIENTE))
        assertFalse(EstadoPedido.permite("EN_PROCESO", OrderStatus.CANCELADO, Role.CLIENTE))
        assertFalse(EstadoPedido.permite("EN_PROCESO", OrderStatus.EN_CAMINO, Role.REPARTIDOR))
        assertTrue(EstadoPedido.permite("LISTO_PARA_ENTREGA", OrderStatus.EN_CAMINO, Role.REPARTIDOR))
        assertEquals(OrderStatus.LISTO_PARA_ENTREGA, OrderStatus.desdeTexto("LISTO_PARA_ENTREGA"))
    }

    @Test fun administradorSoloAvanzaUnaEtapaYLosEstadosDesconocidosNoAvanzan() {
        assertEquals(
            listOf(OrderStatus.EN_PROCESO, OrderStatus.CANCELADO),
            EstadoPedido.opciones("PENDIENTE", Role.ADMIN)
        )
        assertFalse(EstadoPedido.permite("PENDIENTE", OrderStatus.EN_CAMINO, Role.ADMIN))
        assertFalse(EstadoPedido.permite("estado_desconocido", OrderStatus.EN_PROCESO, Role.ADMIN))
        assertFalse(OrderStatus.puedeCancelarCliente("estado_desconocido"))
    }

    @Test fun soloCocinaReabreYLosEstadosFinalesSonTerminales() {
        assertTrue(EstadoPedido.permite("LISTO_PARA_ENTREGA", OrderStatus.EN_PROCESO, Role.COCINERO))
        assertFalse(EstadoPedido.permite("LISTO_PARA_ENTREGA", OrderStatus.EN_PROCESO, Role.ADMIN))
        assertEquals(emptyList<OrderStatus>(), EstadoPedido.opciones("ENTREGADO", Role.ADMIN))
        assertEquals(emptyList<OrderStatus>(), EstadoPedido.opciones("CANCELADO", Role.REPARTIDOR))
    }
}
