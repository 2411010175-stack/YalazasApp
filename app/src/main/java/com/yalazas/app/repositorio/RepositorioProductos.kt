package com.yalazas.app.repositorio

import com.yalazas.app.model.Product
import com.yalazas.app.model.PromocionProducto
import com.yalazas.app.model.CalculadoraInsumos
import com.yalazas.app.model.Insumo
import com.yalazas.app.model.RecetaProducto
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Acceso a la colección de productos de Firestore para el menú. */
class RepositorioProductos {
    private val db = ServiciosFirebase.firestore
    private val productsCollection = db.collection("products")

    /** Observa el catálogo; al dejar de escucharlo se libera el listener de Firestore. */
    fun observarProductos(): Flow<Result<List<Product>>> = callbackFlow {
        var productosBase: List<Product>? = null
        var promociones = emptyMap<String, PromocionProducto>()
        var insumos: Map<String, Insumo>? = null
        var recetas: Map<String, RecetaProducto>? = null

        fun publicarCatalogo() {
            val productos = productosBase ?: return
            val existencias = insumos ?: return
            val recetasActuales = recetas ?: return
            trySend(Result.success(productos.map { producto ->
                val oferta = promociones[producto.id]?.aplicarA(producto) ?: producto
                val receta = recetasActuales[producto.id]
                if (receta == null) oferta else {
                    val estado = CalculadoraInsumos.evaluarProducto(receta, oferta, existencias)
                    val opciones = if (receta.sauceSlotsMl.isEmpty()) oferta.availableSauces
                        else CalculadoraInsumos.salsasDisponibles(receta, oferta, existencias)
                    oferta.copy(
                        isAvailable = oferta.isAvailable && estado.disponible,
                        availableSauces = opciones
                    )
                }
            }))
        }

        val registro = productsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                close()
                return@addSnapshotListener
            }

            if (snapshot != null) {
                try {
                    productosBase = snapshot.documents.mapNotNull { documento ->
                        documento.toObject(Product::class.java)?.apply { id = documento.id }
                    }
                    publicarCatalogo()
                } catch (e: Exception) {
                    trySend(Result.failure(e))
                }
            }
        }

        val registroPromociones = RepositorioPromociones.escuchar(
            alCambiar = { nuevas ->
                promociones = nuevas.associateBy { it.productId }
                publicarCatalogo()
            },
            alError = {
                // El catálogo base sigue disponible si aún no se publicaron las nuevas reglas.
                promociones = emptyMap()
                publicarCatalogo()
            }
        )

        val registroInsumos = RepositorioInsumos.escucharInsumos(
            alCambiar = { insumos = it.associateBy(Insumo::id); publicarCatalogo() },
            alError = { error ->
                if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    // Durante la migración, conserva el menú anterior hasta publicar las reglas nuevas.
                    insumos = emptyMap(); publicarCatalogo()
                } else trySend(Result.failure(error))
            }
        )
        val registroRecetas = RepositorioInsumos.escucharRecetas(
            alCambiar = { recetas = it.associateBy(RecetaProducto::productId); publicarCatalogo() },
            alError = { error ->
                if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    recetas = emptyMap(); publicarCatalogo()
                } else trySend(Result.failure(error))
            }
        )

        awaitClose {
            registro.remove()
            registroPromociones.remove()
            registroInsumos.remove()
            registroRecetas.remove()
        }
    }

    /** Carga datos de ejemplo solo si la colección está vacía; no se ejecuta al abrir el menú. */
    suspend fun cargarProductosIniciales() {
        val initial = listOf(
            Product(name = "Alitas Acevichadas", tagline = "El clásico peruano en tus alitas", description = "6 piezas bañadas en nuestra salsa acevichada secreta con toque de limón y ají limo. Acompañadas de papas fritas.", price = 22.0, category = "Alitas", imageUrl = "https://via.placeholder.com/400x300.png?text=Alitas+Acevichadas"),
            Product(name = "Alitas BBQ", tagline = "Dulce y ahumado perfecto", description = "6 piezas cubiertas con salsa BBQ artesanal. Un sabor clásico que nunca falla. Acompañadas de papas fritas.", price = 20.0, category = "Alitas", imageUrl = "https://via.placeholder.com/400x300.png?text=Alitas+BBQ"),
            Product(name = "Alitas Crispy", tagline = "El crujido que esperabas", description = "6 piezas empanizadas con nuestra receta secreta extracrujiente. Ideales para dipear. Acompañadas de papas fritas.", price = 18.0, category = "Alitas", imageUrl = "https://via.placeholder.com/400x300.png?text=Alitas+Crispy")
        )
        
        // Verificamos si ya hay productos para no duplicar
        val existing = productsCollection.limit(1).get().await()
        if (existing.isEmpty) {
            for (p in initial) {
                val doc = productsCollection.document()
                p.id = doc.id
                doc.set(p).await()
            }
        }
    }
}
