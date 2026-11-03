package com.yalazas.app.ui.admin

import com.yalazas.app.model.OrderStatus
import java.util.Calendar
import java.util.Date

/** Datos mínimos para contar pedidos sin confundirlos con ventas completadas. */
data class PedidoParaResumen(val estado: String, val fechaPedido: Date)

data class IndicadoresVentasAdmin(
    val ventasRealizadas: Int,
    val ventasDelDia: Int,
    val pedidosEnCurso: Int
)

object EstadisticasVentasAdmin {
    fun esVentaRealizada(estado: String): Boolean = estado == OrderStatus.ENTREGADO.key

    fun calcular(pedidos: List<PedidoParaResumen>, hoy: Date = Date()): IndicadoresVentasAdmin {
        val diaActual = Calendar.getInstance().apply { time = hoy }
        val diaPedido = Calendar.getInstance()
        var ventasRealizadas = 0
        var ventasDelDia = 0
        var pedidosEnCurso = 0

        pedidos.forEach { pedido ->
            when (pedido.estado) {
                OrderStatus.ENTREGADO.key -> {
                    ventasRealizadas++
                    diaPedido.time = pedido.fechaPedido
                    if (diaPedido.get(Calendar.YEAR) == diaActual.get(Calendar.YEAR) &&
                        diaPedido.get(Calendar.DAY_OF_YEAR) == diaActual.get(Calendar.DAY_OF_YEAR)
                    ) ventasDelDia++
                }
                OrderStatus.PENDIENTE.key, OrderStatus.EN_PROCESO.key,
                OrderStatus.LISTO_PARA_ENTREGA.key, OrderStatus.EN_CAMINO.key -> pedidosEnCurso++
                else -> Unit
            }
        }
        return IndicadoresVentasAdmin(ventasRealizadas, ventasDelDia, pedidosEnCurso)
    }
}
