package com.yalazas.app.repositorio

import android.location.Location
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.model.UbicacionRepartidor

/** Mantiene la posición fuera del pedido para no interferir con sus transiciones State. */
object RepositorioUbicacionRepartidor {
    private fun referencia(pedidoId: String) = ServiciosFirebase.firestore
        .collection("orders").document(pedidoId).collection("tracking").document("actual")

    fun observar(pedidoId: String, alCambiar: (UbicacionRepartidor?) -> Unit, alError: (Exception) -> Unit): ListenerRegistration =
        referencia(pedidoId).addSnapshotListener { documento, error ->
            if (error != null) {
                alError(error)
            } else {
                alCambiar(documento?.takeIf { it.exists() }?.toObject(UbicacionRepartidor::class.java))
            }
        }

    fun publicar(pedidoId: String, ubicacion: Location, alError: (Exception) -> Unit) {
        referencia(pedidoId).set(mapOf(
            "latitude" to ubicacion.latitude,
            "longitude" to ubicacion.longitude,
            "accuracyMeters" to ubicacion.accuracy.toDouble(),
            "updatedAt" to FieldValue.serverTimestamp()
        )).addOnFailureListener(alError)
    }
}
