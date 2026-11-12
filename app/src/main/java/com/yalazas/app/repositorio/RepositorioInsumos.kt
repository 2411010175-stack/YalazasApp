package com.yalazas.app.repositorio

import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.yalazas.app.model.Insumo
import com.yalazas.app.model.RecetaProducto
import com.yalazas.app.model.Product

/** Centraliza las colecciones de existencias y recetas; sus escrituras requieren rol admin. */
object RepositorioInsumos {
    private val db = ServiciosFirebase.firestore
    private val insumos = db.collection("ingredients")
    private val recetas = db.collection("recipes")

    fun escucharInsumos(alCambiar: (List<Insumo>) -> Unit, alError: (Exception) -> Unit): ListenerRegistration =
        insumos.addSnapshotListener { snapshot, error ->
            if (error != null) alError(error)
            else if (snapshot != null) alCambiar(snapshot.documents.mapNotNull { doc ->
                doc.toObject(Insumo::class.java)?.apply { id = doc.id }
            })
        }

    fun escucharRecetas(alCambiar: (List<RecetaProducto>) -> Unit, alError: (Exception) -> Unit): ListenerRegistration =
        recetas.addSnapshotListener { snapshot, error ->
            if (error != null) alError(error)
            else if (snapshot != null) alCambiar(snapshot.documents.mapNotNull { doc ->
                doc.toObject(RecetaProducto::class.java)?.apply { productId = doc.id }
            })
        }

    fun guardarInsumo(insumo: Insumo, onResult: (Result<Unit>) -> Unit) {
        val ref = if (insumo.id.isBlank()) insumos.document() else insumos.document(insumo.id)
        ref.set(insumo.copy(id = ref.id))
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun guardarReceta(receta: RecetaProducto, onResult: (Result<Unit>) -> Unit, producto: Product? = null) {
        val batch = db.batch()
        batch.set(recetas.document(receta.productId), receta)
        if (producto != null && receta.sauceSlotsMl.isNotEmpty() &&
            (!producto.allowsSauces || producto.maxSauces < receta.sauceSlotsMl.size || producto.availableSauces.isEmpty())) {
            val cambios = mapOf(
                "allowsSauces" to true,
                "maxSauces" to maxOf(producto.maxSauces, receta.sauceSlotsMl.size),
                "availableSauces" to producto.availableSauces.ifEmpty { Product.DEFAULT_YALAZA_SAUCES }
            )
            batch.update(db.collection("products").document(producto.id), cambios)
            batch.set(db.collection("catalog_cards").document(producto.id), cambios, SetOptions.merge())
        }
        batch.commit().addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Suma una compra al saldo actual sin reemplazarlo si otro administrador registró otra. */
    fun registrarIngreso(id: String, cantidadBase: Double, onResult: (Result<Unit>) -> Unit) {
        if (!cantidadBase.isFinite() || cantidadBase <= 0.0) {
            onResult(Result.failure(IllegalArgumentException("La cantidad debe ser mayor que cero.")))
            return
        }
        insumos.document(id).update("stock", FieldValue.increment(cantidadBase))
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun actualizarUmbrales(id: String, amarillo: Double, rojo: Double, onResult: (Result<Unit>) -> Unit) {
        insumos.document(id).update(mapOf("lowThreshold" to amarillo, "pauseThreshold" to rojo))
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    /** Corrige el saldo físico tras contar cocina, pérdidas o compras anteriores. */
    fun ajustarStock(id: String, saldoBase: Double, onResult: (Result<Unit>) -> Unit) {
        if (!saldoBase.isFinite() || saldoBase < 0.0) {
            onResult(Result.failure(IllegalArgumentException("El saldo no puede ser negativo.")))
            return
        }
        insumos.document(id).update("stock", saldoBase)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun eliminarReceta(productId: String, onResult: (Result<Unit>) -> Unit) {
        recetas.document(productId).delete()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}
