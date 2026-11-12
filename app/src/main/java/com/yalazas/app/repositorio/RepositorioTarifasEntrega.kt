package com.yalazas.app.repositorio

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.yalazas.app.utilidades.ConfiguracionTarifaEntrega

/** Tarifa vigente y versiones históricas; solo administración puede publicar cambios. */
object RepositorioTarifasEntrega {
    private val db = ServiciosFirebase.firestore
    private val actual = db.collection("settings").document("deliveryTariff")
    private val versiones = db.collection("delivery_tariff_versions")

    fun desde(documento: DocumentSnapshot): ConfiguracionTarifaEntrega {
        if (!documento.exists()) return ConfiguracionTarifaEntrega()
        val politica = ConfiguracionTarifaEntrega(
            version = documento.getLong("version")?.toInt() ?: -1,
            baseCents = documento.getLong("baseCents")?.toInt() ?: -1,
            includedMeters = documento.getLong("includedMeters")?.toInt() ?: -1,
            extraKmCents = documento.getLong("extraKmCents")?.toInt() ?: -1
        )
        require(politica.valida()) { "La configuración de entrega no es válida." }
        return politica
    }

    fun escuchar(
        alCambiar: (ConfiguracionTarifaEntrega, Boolean) -> Unit,
        alError: (Exception) -> Unit
    ): ListenerRegistration = actual.addSnapshotListener(MetadataChanges.INCLUDE) { documento, error ->
        if (error != null) alError(error)
        else if (documento != null) runCatching { desde(documento) }
            .onSuccess { alCambiar(it, !documento.metadata.isFromCache && !documento.metadata.hasPendingWrites()) }
            .onFailure { alError(it as? Exception ?: IllegalStateException(it)) }
    }

    /** La lectura y la nueva versión se confirman dentro de una transacción de Firestore. */
    fun guardar(
        nueva: ConfiguracionTarifaEntrega,
        alTerminar: (Result<ConfiguracionTarifaEntrega>) -> Unit
    ) {
        val uid = ServiciosFirebase.auth.currentUser?.uid
        if (uid.isNullOrBlank() || !nueva.valida()) {
            alTerminar(Result.failure(IllegalArgumentException("Revisa los importes y la sesión de administración.")))
            return
        }
        db.runTransaction { tx ->
            val vigente = desde(tx.get(actual))
            require(nueva.version == vigente.version) { "La tarifa cambió en otro panel. Recarga los valores." }
            val siguiente = nueva.copy(version = vigente.version + 1)
            val historial = versiones.document(siguiente.version.toString())
            require(!tx.get(historial).exists()) { "La versión ya existe. Vuelve a cargar la tarifa." }
            val datos = mapOf(
                "version" to siguiente.version,
                "baseCents" to siguiente.baseCents,
                "includedMeters" to siguiente.includedMeters,
                "extraKmCents" to siguiente.extraKmCents,
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid
            )
            tx.set(actual, datos)
            tx.set(historial, datos)
            siguiente
        }.addOnSuccessListener { alTerminar(Result.success(it)) }
            .addOnFailureListener { alTerminar(Result.failure(it)) }
    }
}
