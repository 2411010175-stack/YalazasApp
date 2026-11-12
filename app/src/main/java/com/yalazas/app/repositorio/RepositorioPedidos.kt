package com.yalazas.app.repositorio

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.yalazas.app.model.CalculadoraInsumos
import com.yalazas.app.model.Insumo
import com.yalazas.app.model.RecetaProducto
import com.yalazas.app.model.Product
import com.yalazas.app.model.ArticuloCarrito
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.model.Role
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
    val deliveryFee: Double,
    val deliveryTariffVersion: Int = 0,
    val serviceType: String = "",
    val tableNumber: Int = 0,
    val customerName: String = ""
)

/** Fachada del checkout: valida el pedido y concentra su escritura en Firestore. */
object RepositorioPedidos {
    /** Conserva la fachada del pedido y delega la transición al único repositorio State. */
    fun cambiarEstadoCocina(orderId: String, destino: OrderStatus, onResult: (Result<Unit>) -> Unit) {
        RepositorioEstadosPedido.cambiarCocina(orderId, destino, onResult)
    }

    /** El mesero comprueba el cobro y entrega una comanda lista en una sola transición. */
    fun entregarPedidoMesa(
        orderId: String, metodo: String, montoRecibido: Double,
        onResult: (Result<Unit>) -> Unit
    ) = RepositorioEstadosPedido.marcarEntregadoMesa(orderId, metodo, montoRecibido, onResult)

    /** Solicita cancelar un pedido propio, con validación del estado remoto. */
    fun cancelarPedidoPropio(order: Order, onResult: (Result<Unit>) -> Unit) {
        val uid = ServiciosFirebase.auth.currentUser?.uid
        if (uid == null || uid != order.userId || order.id.isBlank() || order.serviceType == "MESA" ||
            !OrderStatus.puedeCancelarCliente(order.status)
        ) {
            onResult(Result.failure(IllegalArgumentException("Este pedido ya no se puede cancelar.")))
            return
        }

        RepositorioEstadosPedido.cancelarPropio(order.id, onResult)
    }

    /** Valida los datos, registra `orders` y vacía el carrito solo cuando Firestore confirma. */
    fun registrarPedido(request: SolicitudPedido, onResult: (Result<String>) -> Unit) {
        val validationError = validate(request)
        if (validationError != null) {
            onResult(Result.failure(IllegalArgumentException(validationError)))
            return
        }

        if (ApiPedidosAtomicos.configurada) {
            ApiPedidosAtomicos.crear(request) { resultado ->
                resultado.onSuccess { if (request.serviceType != "MESA") RepositorioCarrito.vaciarCarrito() }
                onResult(resultado)
            }
            return
        }

        val profile = request.profile
        val total = request.totalAmount
        val enMesa = request.serviceType == "MESA"
        val isCash = request.paymentMethod == "EFECTIVO"
        val paidAmount = if (enMesa) 0.0 else if (isCash) request.paymentAmount else total
        val change = if (isCash) paidAmount - total else 0.0

        // Estas claves son el contrato de Firestore: no dependen de los nombres internos en Kotlin.
        val orderData = hashMapOf(
            "userId" to profile.uid,
            "customerName" to (if (enMesa) request.customerName.ifBlank { "Mesa ${request.tableNumber}" } else profile.name),
            "phone" to (if (enMesa) "" else profile.phone),
            "address" to request.address,
            "reference" to request.reference,
            "latitude" to (if (request.latitude != 0.0) request.latitude else profile.latitude),
            "longitude" to (if (request.longitude != 0.0) request.longitude else profile.longitude),
            "comment" to request.comment,
            "total" to total,
            "status" to OrderStatus.PENDIENTE.key,
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
            "deliveryTariffVersion" to request.deliveryTariffVersion,
            "routeOrderIndex" to 0,
            "serviceType" to request.serviceType.ifBlank { if (request.isDelivery) "DELIVERY" else "RETIRO" },
            "tableNumber" to request.tableNumber,
            "waiterId" to (if (enMesa) profile.uid else ""),
            "waiterName" to (if (enMesa) profile.name else ""),
            "paymentVerifiedBy" to "",
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

        // Una comanda abierta antes de un agotamiento debe revisarse contra el servidor.
        validarExistencias(request.items) { validacion ->
            validacion.onSuccess {
                val db = ServiciosFirebase.firestore
                if (enMesa) {
                    // La comanda y la configuración se leen en la misma transacción: una mesa retirada
                    // mientras se confirma el pedido deja de ser válida antes de guardarlo.
                    val referenciaSalon = db.collection("settings").document("salon")
                    val referenciaPedido = db.collection("orders").document()
                    db.runTransaction { transaccion ->
                        val salon = transaccion.get(referenciaSalon)
                        val mesas = if (salon.exists()) (salon.get("tableNumbers") as? List<*>)
                            .orEmpty().mapNotNull { (it as? Number)?.toInt() }
                        else RepositorioMesas.mesasIniciales
                        require(request.tableNumber in mesas) {
                            "La mesa ${request.tableNumber} ya no está disponible. Elige otra mesa."
                        }
                        transaccion.set(referenciaPedido, orderData)
                        referenciaPedido.id
                    }.addOnSuccessListener { id -> onResult(Result.success(id)) }
                        .addOnFailureListener { error -> onResult(Result.failure(error)) }
                } else if (request.isDelivery) {
                    // Una tarifa modificada mientras el cliente confirmaba invalida el importe anterior.
                    val referenciaTarifa = db.collection("settings").document("deliveryTariff")
                    val referenciaPedido = db.collection("orders").document()
                    db.runTransaction { tx ->
                        val vigente = RepositorioTarifasEntrega.desde(tx.get(referenciaTarifa))
                        val calculado = com.yalazas.app.utilidades.TarifaPorDistancia(vigente)
                            .calcular(request.deliveryDistanceKm)
                        require(request.deliveryTariffVersion == vigente.version &&
                            abs(request.deliveryFee - calculado) < 0.001) {
                            "La tarifa cambió. Revisa el total antes de confirmar."
                        }
                        tx.set(referenciaPedido, orderData)
                        referenciaPedido.id
                    }.addOnSuccessListener { id ->
                        RepositorioCarrito.vaciarCarrito()
                        onResult(Result.success(id))
                    }.addOnFailureListener { onResult(Result.failure(it)) }
                } else {
                    db.collection("orders").add(orderData)
                        .addOnSuccessListener { document ->
                            RepositorioCarrito.vaciarCarrito()
                            onResult(Result.success(document.id))
                        }
                        .addOnFailureListener { error -> onResult(Result.failure(error)) }
                }
            }.onFailure { onResult(Result.failure(it)) }
        }
    }

    /** Verifica la demanda agregada de la cesta antes de registrar el pedido. */
    private fun validarExistencias(items: List<ArticuloCarrito>, onResult: (Result<Unit>) -> Unit) {
        val db = ServiciosFirebase.firestore
        db.collection("products").get(Source.SERVER).addOnSuccessListener { productosSnapshot ->
            val productos = productosSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Product::class.java)?.apply { id = doc.id }
            }.associateBy(Product::id)
            db.collection("recipes").get(Source.SERVER).addOnSuccessListener { recetasSnapshot ->
                val recetas = recetasSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(RecetaProducto::class.java)?.apply { productId = doc.id }
                }.associateBy(RecetaProducto::productId)
                db.collection("ingredients").get(Source.SERVER).addOnSuccessListener stockOk@{ snapshot ->
                    val insumos = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Insumo::class.java)?.apply { id = doc.id }
                    }.associateBy(Insumo::id)
                    val demanda = mutableMapOf<String, Double>()
                    for (item in items) {
                        val producto = productos[item.product.id]
                        val receta = recetas[item.product.id]
                        if (producto == null || !producto.isAvailable || receta == null) {
                            onResult(Result.failure(IllegalStateException(
                                "${item.product.name} ya no está disponible. Actualiza el pedido.")))
                            return@stockOk
                        }
                        val estado = CalculadoraInsumos.evaluarProducto(receta, producto, insumos)
                        if (!estado.disponible) {
                            onResult(Result.failure(IllegalStateException(
                                "${producto.name} no tiene insumos suficientes o su receta está incompleta. Actualiza el pedido.")))
                            return@stockOk
                        }
                        if (item.selectedSauces.any {
                                it !in CalculadoraInsumos.salsasDisponibles(receta, producto, insumos)
                            }) {
                            onResult(Result.failure(IllegalStateException(
                                "Las salsas de ${producto.name} ya no están disponibles. Actualiza el pedido.")))
                            return@stockOk
                        }
                        val lineas = CalculadoraInsumos.demandaArticulo(receta, producto, item.selectedSauces)
                        if (lineas == null) {
                            onResult(Result.failure(IllegalStateException(
                                "Revisa las salsas de ${producto.name} antes de confirmar.")))
                            return@stockOk
                        }
                        for (linea in lineas) {
                            demanda[linea.ingredientId] = (demanda[linea.ingredientId] ?: 0.0) +
                                linea.quantity * item.quantity
                        }
                    }
                    val insuficiente = demanda.entries.firstOrNull { (id, cantidad) ->
                        val insumo = insumos[id]
                        insumo == null || !cantidad.isFinite() || cantidad <= 0.0 ||
                            insumo.stock < cantidad || insumo.stock < insumo.pauseThreshold
                    }
                    if (insuficiente != null) {
                        val nombre = insumos[insuficiente.key]?.name ?: "Un insumo"
                        onResult(Result.failure(IllegalStateException(
                            "$nombre no alcanza para este pedido. Reduce cantidades o actualiza la carta.")))
                    } else onResult(Result.success(Unit))
                }.addOnFailureListener { onResult(Result.failure(it)) }
            }.addOnFailureListener { onResult(Result.failure(it)) }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Devuelve un mensaje ante datos inválidos o `null` si la solicitud es coherente. */
    private fun validate(request: SolicitudPedido): String? {
        if (ServiciosFirebase.auth.currentUser?.uid != request.profile.uid) {
            return "La sesión del cliente no coincide con el pedido. Vuelve a iniciar sesión."
        }
        val enMesa = request.serviceType == "MESA"
        if (enMesa && (Role.desdeTexto(request.profile.role) != Role.MESERO ||
                    request.tableNumber !in 1..200 || request.isDelivery ||
                    request.deliveryFee != 0.0 || request.deliveryDistanceKm != 0.0)) {
            return "La comanda de mesa no es válida para esta cuenta."
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
        if (request.deliveryTariffVersion < 0 ||
            !request.deliveryDistanceKm.isFinite() || !request.deliveryFee.isFinite() ||
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
        if (request.paymentMethod !in setOf("EFECTIVO", "YAPE", "PLIN", "TRANSFERENCIA") &&
            !(enMesa && request.paymentMethod == "POR_DEFINIR")) {
            return "Selecciona un método de pago válido."
        }
        if (!enMesa && request.paymentMethod == "EFECTIVO" &&
            (!request.paymentAmount.isFinite() || request.paymentAmount < request.totalAmount)
        ) {
            return "El monto en efectivo no cubre el total del pedido."
        }
        return null
    }
}
