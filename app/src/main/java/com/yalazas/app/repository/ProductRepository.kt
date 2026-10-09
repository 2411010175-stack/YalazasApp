package com.yalazas.app.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.yalazas.app.model.Product
import kotlinx.coroutines.tasks.await

class ProductRepository {
    private val db = FirebaseFirestore.getInstance()
    private val productsCollection = db.collection("products")

    suspend fun getAllProducts(): Result<List<Product>> {
        return try {
            val snapshot = productsCollection.get().await()
            val products = snapshot.toObjects(Product::class.java)
            Result.success(products)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Función que ejecutaremos una sola vez para poblar la base de datos de Firebase
    suspend fun seedInitialProducts() {
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
