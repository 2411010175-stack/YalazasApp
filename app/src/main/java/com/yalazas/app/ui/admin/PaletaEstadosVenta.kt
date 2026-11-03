package com.yalazas.app.ui.admin

import androidx.annotation.ColorRes
import com.yalazas.app.R
import com.yalazas.app.model.OrderStatus

/** Colores de presentación compartidos por ventas recientes y control de pedidos. */
data class EstiloEstadoVenta(
    @ColorRes val tarjeta: Int,
    @ColorRes val borde: Int,
    @ColorRes val boton: Int,
    @ColorRes val textoEstado: Int,
    @ColorRes val fondoEstado: Int
)

object PaletaEstadosVenta {
    fun para(estado: OrderStatus): EstiloEstadoVenta = when (estado) {
        OrderStatus.PENDIENTE -> EstiloEstadoVenta(
            R.color.admin_sale_pending_card, R.color.admin_sale_pending_border,
            R.color.admin_sale_pending_button, R.color.admin_status_pending, R.color.admin_status_pending_bg
        )
        OrderStatus.EN_PROCESO -> EstiloEstadoVenta(
            R.color.admin_sale_cooking_card, R.color.admin_sale_cooking_border,
            R.color.admin_sale_cooking_button, R.color.admin_status_cooking, R.color.admin_status_cooking_bg
        )
        OrderStatus.LISTO_PARA_ENTREGA -> EstiloEstadoVenta(
            R.color.admin_sale_ready_card, R.color.admin_sale_ready_border,
            R.color.admin_sale_ready_button, R.color.admin_status_ready, R.color.admin_status_ready_bg
        )
        OrderStatus.EN_CAMINO -> EstiloEstadoVenta(
            R.color.admin_sale_road_card, R.color.admin_sale_road_border,
            R.color.admin_sale_road_button, R.color.admin_status_road, R.color.admin_status_road_bg
        )
        OrderStatus.ENTREGADO -> EstiloEstadoVenta(
            R.color.admin_sale_delivered_card, R.color.admin_sale_delivered_border,
            R.color.admin_sale_delivered_button, R.color.admin_status_delivered, R.color.admin_status_delivered_bg
        )
        OrderStatus.CANCELADO -> EstiloEstadoVenta(
            R.color.admin_sale_cancelled_card, R.color.admin_sale_cancelled_border,
            R.color.admin_sale_cancelled_button, R.color.admin_status_cancelled, R.color.admin_status_cancelled_bg
        )
    }
}
