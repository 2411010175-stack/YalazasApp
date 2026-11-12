package com.yalazas.app.repositorio

import com.yalazas.app.model.Product
import com.yalazas.app.model.PromocionProducto
import com.yalazas.app.model.CalculadoraInsumos
import com.yalazas.app.model.Insumo
import com.yalazas.app.model.RecetaProducto
import com.yalazas.app.model.TarjetaCatalogo
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Acceso a la colección de productos de Firestore para el menú. */
class RepositorioProductos {
    private val db = ServiciosFirebase.firestore
    private val productsCollection = db.collection("products")
    private val tarjetasCollection = db.collection("catalog_cards")

    /** Observa el catálogo; al dejar de escucharlo se libera el listener de Firestore. */
    fun observarProductos(): Flow<Result<List<Product>>> = observarCatalogo(false)

    /** Usa tarjetas ligeras cuando el catálogo migrado está listo; el legado sigue funcionando. */
    fun observarCatalogoCompacto(): Flow<Result<List<Product>>> = observarCatalogo(true)

    private fun observarCatalogo(compacto: Boolean): Flow<Result<List<Product>>> = callbackFlow {
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
                val estado = CalculadoraInsumos.evaluarProducto(receta, oferta, existencias)
                val opciones = if (receta == null || receta.sauceSlotsMl.isEmpty()) oferta.availableSauces
                    else CalculadoraInsumos.salsasDisponibles(receta, oferta, existencias)
                oferta.copy(
                    isAvailable = oferta.isAvailable && estado.disponible,
                    availableSauces = opciones
                )
            }))
        }

        var registroProductos: ListenerRegistration? = null
        var registroTarjetas: ListenerRegistration? = null
        fun escucharProductosCompletos() {
            if (registroProductos != null) return
            registroTarjetas?.remove()
            registroTarjetas = null
            productosBase = null
            registroProductos = productsCollection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    close()
                } else if (snapshot != null) {
                    try {
                        productosBase = snapshot.documents.mapNotNull { documento ->
                            documento.toObject(Product::class.java)?.apply { id = documento.id }
                        }
                        publicarCatalogo()
                    } catch (e: Exception) { trySend(Result.failure(e)) }
                }
            }
        }
        fun escucharTarjetas() {
            if (registroTarjetas != null) return
            registroProductos?.remove()
            registroProductos = null
            productosBase = null
            registroTarjetas = tarjetasCollection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    close()
                } else if (snapshot != null) {
                    try {
                        productosBase = snapshot.documents.filter { it.id != "_meta" }.mapNotNull { documento ->
                            documento.toObject(TarjetaCatalogo::class.java)?.aProducto(documento.id)
                        }
                        publicarCatalogo()
                    } catch (e: Exception) { trySend(Result.failure(e)) }
                }
            }
        }
        val registroMeta = if (compacto) tarjetasCollection.document("_meta")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot?.getBoolean("ready") != true) escucharProductosCompletos()
                else escucharTarjetas()
            } else {
            escucharProductosCompletos()
            null
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
                insumos = null
                trySend(Result.failure(error))
            }
        )
        val registroRecetas = RepositorioInsumos.escucharRecetas(
            alCambiar = { recetas = it.associateBy(RecetaProducto::productId); publicarCatalogo() },
            alError = { error ->
                recetas = null
                trySend(Result.failure(error))
            }
        )

        awaitClose {
            registroMeta?.remove()
            registroProductos?.remove()
            registroTarjetas?.remove()
            registroPromociones.remove()
            registroInsumos.remove()
            registroRecetas.remove()
        }
    }

    /** Obtiene la descripción y el resto de campos solo al abrir o comprar un plato. */
    suspend fun obtenerProductoCompleto(id: String): Product? {
        val documento = productsCollection.document(id).get().await()
        return documento.toObject(Product::class.java)?.apply { this.id = documento.id }
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
                val lote = db.batch()
                lote.set(doc, p)
                lote.set(tarjetasCollection.document(doc.id), TarjetaCatalogo.desde(p))
                lote.commit().await()
            }
        }
    }
}
