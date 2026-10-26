package com.yalazas.app.repositorio

import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.model.Product
import com.yalazas.app.model.PromocionProducto

/** Guarda las ofertas aplicadas a productos existentes sin escribir en `products`. */
object RepositorioPromociones {
    private val coleccion = ServiciosFirebase.firestore.collection("promotions")

    fun escuchar(
        alCambiar: (List<PromocionProducto>) -> Unit,
        alError: (Exception) -> Unit
    ): ListenerRegistration = coleccion.addSnapshotListener { snapshot, error ->
        if (error != null) {
            alError(error)
        } else if (snapshot != null) {
            alCambiar(snapshot.toObjects(PromocionProducto::class.java))
        }
    }

    fun guardar(
        producto: Product,
        precioOferta: Double,
        etiqueta: String,
        alTerminar: (Result<Unit>) -> Unit
    ) {
        if (producto.id.isBlank() || producto.isOffer || !precioOferta.isFinite() ||
            precioOferta <= 0.0 || precioOferta >= producto.price
        ) {
            alTerminar(Result.failure(IllegalArgumentException("El precio de oferta debe ser menor que el precio regular.")))
            return
        }

        val promocion = PromocionProducto(
            productId = producto.id,
            offerPrice = precioOferta,
            offerTag = etiqueta.trim().ifBlank { "🔥 OFERTA" },
            active = true
        )
        coleccion.document(producto.id).set(promocion)
            .addOnSuccessListener { alTerminar(Result.success(Unit)) }
            .addOnFailureListener { alTerminar(Result.failure(it)) }
    }

    fun quitar(productId: String, alTerminar: (Result<Unit>) -> Unit) {
        if (productId.isBlank()) {
            alTerminar(Result.failure(IllegalArgumentException("Falta el producto de la oferta.")))
            return
        }
        coleccion.document(productId).delete()
            .addOnSuccessListener { alTerminar(Result.success(Unit)) }
            .addOnFailureListener { alTerminar(Result.failure(it)) }
    }
}
