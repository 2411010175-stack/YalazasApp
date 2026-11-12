package com.yalazas.app.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class EstadisticasVentasAdminTest {
    @Test
    fun `un delivery digital entregado sin validar no cuenta como venta`() {
        val hoy = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 11, 12, 0, 0) }.time
        val pedidos = listOf(
            PedidoParaResumen("ENTREGADO", hoy, "PENDIENTE"),
            PedidoParaResumen("ENTREGADO", hoy, "PAGADO")
        )
        assertEquals(IndicadoresVentasAdmin(1, 1, 0), EstadisticasVentasAdmin.calcular(pedidos, hoy))
    }

    @Test
    fun `solo los pedidos entregados cuentan como ventas`() {
        val hoy = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 3, 12, 0, 0) }.time
        val ayer = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 2, 12, 0, 0) }.time
        val pedidos = listOf(
            PedidoParaResumen("PENDIENTE", hoy),
            PedidoParaResumen("EN_PROCESO", hoy),
            PedidoParaResumen("LISTO_PARA_ENTREGA", hoy),
            PedidoParaResumen("EN_CAMINO", hoy),
            PedidoParaResumen("CANCELADO", hoy),
            PedidoParaResumen("ENTREGANDO", hoy),
            PedidoParaResumen("ENTREGADO", hoy),
            PedidoParaResumen("ENTREGADO", ayer)
        )

        assertEquals(IndicadoresVentasAdmin(2, 1, 4), EstadisticasVentasAdmin.calcular(pedidos, hoy))
    }
}
