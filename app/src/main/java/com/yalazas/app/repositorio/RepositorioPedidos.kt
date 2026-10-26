package com.yalazas.app.repositorio

import com.google.firebase.firestore.FieldValue
import com.yalazas.app.model.ArticuloCarrito
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.model.UserProfile
import kotlin.math.abs

/** Datos construidos durante la confirmación para registrar una compra. */
data class SolicitudPedido(
    val items: List<ArticuloCarrito>,
    val profile: UserProfile,
    val address: String,
    val reference: String,
    val latitude: Double,
    val longitude: Double,
    val comment: String,
    val totalAmount: Double,
    val paymentMethod: String,
    val paymentAmount: Double,
    val isDelivery: Boolean,
    val deliveryDistanceKm: Double,
    val deliveryFee: Double
)

/** Fachada del checkout: valida el pedido y concentra su escritura en Firestore. */
object RepositorioPedidos {
    /** Solicita cancelar un pedido propio. La regla publicada de Firestore decide el permiso final. */
    fun cancelarPedidoPropio(order: Order, onResult: (Result<Unit>) -> Unit) {
        val uid = ServiciosFirebase.auth.currentUser?.uid
        if (uid == null || uid != order.userId || order.id.isBlank() ||
            !OrderStatus.puedeCancelarCliente(order.status)
        ) {
            onResult(Result.failure(IllegalArgumentException("Este pedido ya no se puede cancelar.")))
            return
        }

        ServiciosFirebase.firestore.collection("orders").document(order.id)
            .update("status", OrderStatus.CANCELADO.key, "updatedAt", System.currentTimeMillis())
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { error -> onResult(Result.failure(error)) }
    }

    /** Valida los datos, registra `orders` y vacía el carrito solo cuando Firestore confirma. */
    fun registrarPedido(request: SolicitudPedido, onResult: (Result<String>) -> Unit) {
        val validationError = validate(request)
        if (validationError != null) {
            onResult(Result.failure(IllegalArgumentException(validationError)))
            return
        }

        val profile = request.profile
        val total = request.totalAmount
        val isCash = request.paymentMethod == "EFECTIVO"
        val paidAmount = if (isCash) request.paymentAmount else total
        val change = if (isCash) paidAmount - total else 0.0

        // Estas claves son el contrato de Firestore: no dependen de los nombres internos en Kotlin.
        val orderData = hashMapOf(
            "userId" to profile.uid,
            "customerName" to profile.name,
            "phone" to profile.phone,
            "address" to request.address,
            "reference" to request.reference,
            "latitude" to (if (request.latitude != 0.0) request.latitude else profile.latitude),
            "longitude" to (if (request.longitude != 0.0) request.longitude else profile.longitude),
            "comment" to request.comment,
            "total" to total,
            "status" to "PENDIENTE",
            "paymentMethod" to request.paymentMethod,
            "paymentAmount" to paidAmount,
            "paymentChange" to change,
            "paymentStatus" to "PENDIENTE",
            "deliveryDriverId" to "",
            "deliveryDriverName" to "",
            "deliveryDriverPhone" to "",
            "isDelivery" to request.isDelivery,
            "deliveryDistanceKm" to request.deliveryDistanceKm,
            "deliveryFee" to request.deliveryFee,
            "routeOrderIndex" to 0,
            "items" to request.items.map { item ->
                hashMapOf(
                    "productId" to item.product.id,
                    "name" to item.product.name,
                    "description" to item.descripcionFinal,
                    "quantity" to item.quantity,
                    "subtotal" to item.subtotal,
                    "notes" to item.notes,
                    "selectedSauces" to item.selectedSauces
                )
            },
            "timestamp" to FieldValue.serverTimestamp()
        )

        ServiciosFirebase.firestore.collection("orders").add(orderData)
            .addOnSuccessListener { document ->
                RepositorioCarrito.vaciarCarrito()
                onResult(Result.success(document.id))
            }
            .addOnFailureListener { error -> onResult(Result.failure(error)) }
    }

    /** Devuelve un mensaje ante datos inválidos o `null` si la solicitud es coherente. */
    private fun validate(request: SolicitudPedido): String? {
        if (ServiciosFirebase.auth.currentUser?.uid != request.profile.uid) {
            return "La sesión del cliente no coincide con el pedido. Vuelve a iniciar sesión."
        }
        if (request.items.isEmpty() || request.items.size > 50 ||
            request.items.any {
                it.quantity <= 0 || it.product.id.isBlank() ||
                    !it.product.price.isFinite() || it.product.price <= 0.0
            }
        ) {
            return "El pedido debe contener productos válidos."
        }
        if (request.address.isBlank()) return "Falta la dirección del pedido."
        if (!request.deliveryDistanceKm.isFinite() || !request.deliveryFee.isFinite() ||
            request.deliveryDistanceKm < 0.0 || request.deliveryFee < 0.0 ||
            (!request.isDelivery && request.deliveryFee != 0.0)
        ) {
            return "El costo de entrega no es válido."
        }
        val expectedTotal = request.items.sumOf { it.subtotal } + request.deliveryFee
        if (!expectedTotal.isFinite() || !request.totalAmount.isFinite() ||
            request.totalAmount <= 0.0 ||
            abs(expectedTotal - request.totalAmount) > 0.01
        ) {
            return "El total del pedido no coincide con los productos y el envío."
        }
        if (request.paymentMethod !in setOf("EFECTIVO", "YAPE", "PLIN", "TRANSFERENCIA")) {
            return "Selecciona un método de pago válido."
        }
        if (request.paymentMethod == "EFECTIVO" &&
            (!request.paymentAmount.isFinite() || request.paymentAmount < request.totalAmount)
        ) {
            return "El monto en efectivo no cubre el total del pedido."
        }
        return null
    }
}
