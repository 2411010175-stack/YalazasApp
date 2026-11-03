package com.yalazas.app.repositorio

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Transaction
import com.yalazas.app.model.AuditLog
import com.yalazas.app.model.EstadoPedido
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.model.Role

/** Una parada de reparto y su posición en la ruta calculada. */
data class ParadaPedido(val pedidoId: String, val indice: Int)

/**
 * Único punto de escritura de estados. Lee el estado vigente dentro de una transacción,
 * consulta State y añade los datos que exige cada etapa antes de guardar en Firestore.
 */
object RepositorioEstadosPedido {
    private val db get() = ServiciosFirebase.firestore
    private val uidActual get() = ServiciosFirebase.auth.currentUser?.uid

    /** Cliente: únicamente su propio pedido pendiente puede pasar a cancelado. */
    fun cancelarPropio(pedidoId: String, onResult: (Result<Unit>) -> Unit) {
        val uid = uidActual
        if (uid.isNullOrBlank() || pedidoId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Pedido o sesión inválidos.")))
            return
        }
        val perfilRef = db.collection("users").document(uid)
        val pedidoRef = db.collection("orders").document(pedidoId)
        db.runTransaction { tx ->
            val perfil = tx.get(perfilRef)
            // Las sesiones invitadas pueden no tener documento de perfil en Firestore.
            if (perfil.exists() && (
                    perfil.getBoolean("active") == false ||
                        Role.desdeTexto(perfil.getString("role").orEmpty()) != Role.CLIENTE
                    )
            ) throw IllegalStateException("Esta cuenta no puede cancelar como cliente.")
            val pedido = tx.get(pedidoRef)
            if (pedido.getString("userId") != uid) {
                throw IllegalStateException("Solo puedes cancelar tus propios pedidos.")
            }
            exigirTransicion(pedido, OrderStatus.CANCELADO, Role.CLIENTE)
            tx.update(pedidoRef, mapOf(
                "status" to OrderStatus.CANCELADO.key,
                "updatedAt" to System.currentTimeMillis()
            ))
        }.addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Cocina: inicia, termina o reabre una comanda todavía no tomada por reparto. */
    fun cambiarCocina(pedidoId: String, destino: OrderStatus, onResult: (Result<Unit>) -> Unit) {
        val uid = uidActual
        if (uid.isNullOrBlank() || pedidoId.isBlank() ||
            destino !in listOf(OrderStatus.EN_PROCESO, OrderStatus.LISTO_PARA_ENTREGA)
        ) {
            onResult(Result.failure(IllegalArgumentException("Acción de cocina inválida.")))
            return
        }
        val perfilRef = db.collection("users").document(uid)
        val pedidoRef = db.collection("orders").document(pedidoId)
        db.runTransaction { tx ->
            exigirRol(tx, perfilRef, Role.COCINERO)
            val pedido = tx.get(pedidoRef)
            exigirTransicion(pedido, destino, Role.COCINERO)
            tx.update(pedidoRef, mapOf(
                "status" to destino.key,
                "updatedAt" to System.currentTimeMillis()
            ))
        }.addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Administración: avanza un estado y registra la auditoría en la misma transacción. */
    fun cambiarAdministrador(pedidoId: String, destino: OrderStatus, onResult: (Result<Unit>) -> Unit) {
        val uid = uidActual
        if (uid.isNullOrBlank() || pedidoId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Pedido o sesión inválidos.")))
            return
        }
        val perfilRef = db.collection("users").document(uid)
        val pedidoRef = db.collection("orders").document(pedidoId)
        val auditoriaRef = db.collection("auditoria").document()
        db.runTransaction { tx ->
            exigirRol(tx, perfilRef, Role.ADMIN)
            val pedido = tx.get(pedidoRef)
            val anterior = exigirTransicion(pedido, destino, Role.ADMIN)
            val registro = AuditLog(
                id = auditoriaRef.id,
                usuarioAfectadoId = pedido.getString("userId").orEmpty().ifBlank { pedidoId },
                usuarioAdministradorId = uid,
                accion = "CAMBIO_ESTADO_PEDIDO",
                rolAnterior = anterior.key,
                rolNuevo = destino.key
            )
            tx.update(pedidoRef, mapOf(
                "status" to destino.key,
                "updatedAt" to System.currentTimeMillis()
            ))
            tx.set(auditoriaRef, registro)
        }.addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Reparto: toma uno o varios pedidos listos de manera atómica y asigna la ruta. */
    fun tomarParaReparto(paradas: List<ParadaPedido>, onResult: (Result<Unit>) -> Unit) {
        val uid = uidActual
        if (uid.isNullOrBlank() || paradas.isEmpty() || paradas.size > 100 ||
            paradas.any { it.pedidoId.isBlank() || it.indice <= 0 } ||
            paradas.map { it.pedidoId }.toSet().size != paradas.size ||
            paradas.map { it.indice }.toSet().size != paradas.size
        ) {
            onResult(Result.failure(IllegalArgumentException("La selección de pedidos no es válida.")))
            return
        }
        val perfilRef = db.collection("users").document(uid)
        val refs = paradas.map { db.collection("orders").document(it.pedidoId) }
        db.runTransaction { tx ->
            val perfil = exigirRol(tx, perfilRef, Role.REPARTIDOR)
            val pedidos = refs.map { tx.get(it) }
            pedidos.forEach { pedido ->
                exigirTransicion(pedido, OrderStatus.EN_CAMINO, Role.REPARTIDOR)
                if (pedido.getBoolean("isDelivery") != true ||
                    !pedido.getString("deliveryDriverId").isNullOrEmpty()
                ) throw IllegalStateException("Un pedido ya no está disponible para reparto.")
            }
            refs.forEachIndexed { i, ref ->
                tx.update(ref, mapOf(
                    "status" to OrderStatus.EN_CAMINO.key,
                    "deliveryDriverId" to uid,
                    "deliveryDriverName" to perfil.getString("name").orEmpty().ifBlank { "Repartidor Yalazas" },
                    "deliveryDriverPhone" to perfil.getString("phone").orEmpty(),
                    "routeOrderIndex" to paradas[i].indice
                ))
            }
        }.addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Reparto: solo el conductor asignado puede finalizar y marcar el pago. */
    fun marcarEntregado(pedidoId: String, onResult: (Result<Unit>) -> Unit) {
        val uid = uidActual
        if (uid.isNullOrBlank() || pedidoId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Pedido o sesión inválidos.")))
            return
        }
        val perfilRef = db.collection("users").document(uid)
        val pedidoRef = db.collection("orders").document(pedidoId)
        db.runTransaction { tx ->
            exigirRol(tx, perfilRef, Role.REPARTIDOR)
            val pedido = tx.get(pedidoRef)
            exigirTransicion(pedido, OrderStatus.ENTREGADO, Role.REPARTIDOR)
            if (pedido.getBoolean("isDelivery") != true || pedido.getString("deliveryDriverId") != uid) {
                throw IllegalStateException("Este pedido no está asignado a tu cuenta.")
            }
            tx.update(pedidoRef, mapOf(
                "status" to OrderStatus.ENTREGADO.key,
                "paymentStatus" to "PAGADO"
            ))
        }.addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun exigirRol(tx: Transaction, perfilRef: com.google.firebase.firestore.DocumentReference, rol: Role): DocumentSnapshot {
        val perfil = tx.get(perfilRef)
        if (!perfil.exists() || perfil.getBoolean("active") == false ||
            Role.desdeTexto(perfil.getString("role").orEmpty()) != rol
        ) throw IllegalStateException("Tu cuenta no tiene permiso para cambiar este pedido.")
        return perfil
    }

    private fun exigirTransicion(pedido: DocumentSnapshot, destino: OrderStatus, rol: Role): OrderStatus {
        val actual = OrderStatus.values().firstOrNull { it.key == pedido.getString("status") }
            ?: throw IllegalStateException("El pedido no tiene un estado válido.")
        if (!EstadoPedido.permite(actual.key, destino, rol)) {
            throw IllegalStateException("El pedido cambió de estado. Revisa la lista actualizada.")
        }
        return actual
    }
}
