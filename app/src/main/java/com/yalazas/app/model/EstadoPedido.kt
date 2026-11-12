package com.yalazas.app.model

/** Patrón State: cada etapa decide qué acciones están disponibles para cada rol. */
sealed class EstadoPedido(val tipo: OrderStatus) {
    abstract fun siguientes(rol: Role): List<OrderStatus>

    object Pendiente : EstadoPedido(OrderStatus.PENDIENTE) {
        override fun siguientes(rol: Role) = when (rol) {
            Role.COCINERO -> listOf(OrderStatus.EN_PROCESO)
            Role.CLIENTE -> listOf(OrderStatus.CANCELADO)
            Role.ADMIN -> listOf(OrderStatus.EN_PROCESO, OrderStatus.CANCELADO)
            else -> emptyList()
        }
    }

    object EnProceso : EstadoPedido(OrderStatus.EN_PROCESO) {
        override fun siguientes(rol: Role) = when (rol) {
            Role.COCINERO -> listOf(OrderStatus.LISTO_PARA_ENTREGA)
            Role.ADMIN -> listOf(OrderStatus.LISTO_PARA_ENTREGA, OrderStatus.CANCELADO)
            else -> emptyList()
        }
    }

    object Listo : EstadoPedido(OrderStatus.LISTO_PARA_ENTREGA) {
        override fun siguientes(rol: Role) = when (rol) {
            Role.COCINERO -> listOf(OrderStatus.EN_PROCESO)
            Role.MESERO -> listOf(OrderStatus.ENTREGADO)
            Role.REPARTIDOR -> listOf(OrderStatus.EN_CAMINO)
            Role.ADMIN -> listOf(OrderStatus.EN_CAMINO, OrderStatus.CANCELADO)
            else -> emptyList()
        }
    }

    object EnCamino : EstadoPedido(OrderStatus.EN_CAMINO) {
        override fun siguientes(rol: Role) = when (rol) {
            Role.REPARTIDOR -> listOf(OrderStatus.ENTREGADO)
            Role.ADMIN -> listOf(OrderStatus.ENTREGADO, OrderStatus.CANCELADO)
            else -> emptyList()
        }
    }

    object Entregado : EstadoPedido(OrderStatus.ENTREGADO) {
        override fun siguientes(rol: Role) = emptyList<OrderStatus>()
    }

    object Cancelado : EstadoPedido(OrderStatus.CANCELADO) {
        override fun siguientes(rol: Role) = emptyList<OrderStatus>()
    }

    companion object {
        fun desdeTexto(texto: String?): EstadoPedido = desdeTipo(OrderStatus.desdeTexto(texto))

        private fun desdeTipo(tipo: OrderStatus): EstadoPedido = when (tipo) {
            OrderStatus.PENDIENTE -> Pendiente
            OrderStatus.EN_PROCESO -> EnProceso
            OrderStatus.LISTO_PARA_ENTREGA -> Listo
            OrderStatus.EN_CAMINO -> EnCamino
            OrderStatus.ENTREGADO -> Entregado
            OrderStatus.CANCELADO -> Cancelado
        }

        /** Solo las claves canónicas almacenadas en Firestore pueden iniciar una transición. */
        fun opciones(actual: String?, rol: Role): List<OrderStatus> {
            val tipo = OrderStatus.values().firstOrNull { it.key == actual } ?: return emptyList()
            return desdeTipo(tipo).siguientes(rol)
        }

        fun permite(actual: String?, destino: OrderStatus, rol: Role): Boolean =
            destino in opciones(actual, rol)
    }
}
