package com.yalazas.app.model

import java.util.Locale

enum class OrderStatus(
    val key: String,
    val label: String,
    val actionText: String,
    val emoji: String
) {
    PENDIENTE(
        key = "PENDIENTE",
        label = "Pendiente de Confirmación",
        actionText = "Pasar a Cocina / Preparación",
        emoji = "🟡"
    ),
    EN_PROCESO(
        key = "EN_PROCESO",
        label = "En Cocina (Preparación)",
        actionText = "Despachar a Repartidor / Delivery",
        emoji = "👨‍🍳"
    ),
    EN_CAMINO(
        key = "EN_CAMINO",
        label = "En Camino (Delivery)",
        actionText = "Marcar como Entregado al Cliente",
        emoji = "🛵"
    ),
    ENTREGADO(
        key = "ENTREGADO",
        label = "Entregado (Completado)",
        actionText = "Pedido Finalizado",
        emoji = "✅"
    ),
    CANCELADO(
        key = "CANCELADO",
        label = "Cancelado (Anulado)",
        actionText = "Cancelar Pedido",
        emoji = "❌"
    );

    companion object {
        fun fromString(status: String?): OrderStatus {
            val upper = status?.uppercase(Locale.getDefault()) ?: ""
            return when {
                upper.contains("PENDIENTE") -> PENDIENTE
                upper.contains("PROCESO") || upper.contains("COCIN") || upper.contains("PREPAR") -> EN_PROCESO
                upper.contains("CAMINO") || upper.contains("REPART") || upper.contains("DELIVERY") -> EN_CAMINO
                upper.contains("ENTREG") -> ENTREGADO
                upper.contains("CANCEL") -> CANCELADO
                else -> PENDIENTE
            }
        }

        /**
         * Retorna las transiciones estrictamente válidas y secuenciales para el Administrador.
         * Evita que se salten etapas del proceso operativo de pedidos.
         */
        fun getAllowedTransitions(currentStatusStr: String?): List<OrderStatus> {
            val current = fromString(currentStatusStr)
            return when (current) {
                PENDIENTE -> listOf(EN_PROCESO, CANCELADO)
                EN_PROCESO -> listOf(EN_CAMINO, CANCELADO)
                EN_CAMINO -> listOf(ENTREGADO, CANCELADO)
                ENTREGADO -> emptyList() // Pedido completado (Terminal)
                CANCELADO -> emptyList() // Pedido cancelado (Terminal)
            }
        }

        /**
         * REGLA DE NEGOCIO: El cliente solo puede cancelar el pedido mientras esté PENDIENTE.
         * En el momento que pasa a EN_PROCESO (en cocina/preparación) o posteriores,
         * el usuario YA NO PUEDE cancelar el pedido.
         */
        fun canCustomerCancel(statusStr: String?): Boolean {
            val status = fromString(statusStr)
            return status == PENDIENTE
        }
    }
}
