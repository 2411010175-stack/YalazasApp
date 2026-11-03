package com.yalazas.app.model

import java.util.Locale

/** Estados del pedido y reglas locales; las claves de cada estado se guardan en Firestore. */
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
        actionText = "Marcar listo para entrega",
        emoji = "👨‍🍳"
    ),
    LISTO_PARA_ENTREGA(
        key = "LISTO_PARA_ENTREGA",
        label = "Listo para Entrega",
        actionText = "Recoger pedido",
        emoji = "📦"
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
        /** Interpreta el texto recibido de Firestore; un valor desconocido se muestra como pendiente. */
        fun desdeTexto(status: String?): OrderStatus {
            val upper = status?.uppercase(Locale.getDefault()) ?: ""
            return when {
                upper.contains("LISTO") -> LISTO_PARA_ENTREGA
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
        fun obtenerTransicionesPermitidas(currentStatusStr: String?): List<OrderStatus> {
            return EstadoPedido.opciones(currentStatusStr, Role.ADMIN)
        }

        /**
         * REGLA DE NEGOCIO: El cliente solo puede cancelar el pedido mientras esté PENDIENTE.
         * En el momento que pasa a EN_PROCESO (en cocina/preparación) o posteriores,
         * el usuario YA NO PUEDE cancelar el pedido.
         */
        fun puedeCancelarCliente(statusStr: String?): Boolean {
            return CANCELADO in EstadoPedido.opciones(statusStr, Role.CLIENTE)
        }
    }
}
