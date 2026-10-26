package com.yalazas.app.repositorio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.yalazas.app.model.ArticuloCarrito
import com.yalazas.app.model.Product

/** Carrito compartido en memoria. LiveData avisa a las pantallas cuando cambia su contenido. */
object RepositorioCarrito {
    private val _items = MutableLiveData<List<ArticuloCarrito>>(emptyList())
    val items: LiveData<List<ArticuloCarrito>> = _items

    /** Agrupa solo productos con las mismas salsas y notas; las otras variantes quedan separadas. */
    fun agregarProducto(
        product: Product,
        quantity: Int = 1,
        notes: String = "",
        selectedSauces: List<String> = emptyList()
    ) {
        val current = _items.value?.toMutableList() ?: mutableListOf()
        val existing = current.find {
            it.product.id == product.id &&
            it.selectedSauces == selectedSauces &&
            it.notes == notes
        }

        if (existing != null) {
            existing.quantity += quantity
        } else {
            current.add(ArticuloCarrito(product, quantity, notes, selectedSauces))
        }

        _items.value = current
    }

    /** Quita todas las variantes de un producto por su identificador. */
    fun quitarProducto(productId: String) {
        val current = _items.value?.toMutableList() ?: mutableListOf()
        current.removeAll { it.product.id == productId }
        _items.value = current
    }

    /** Quita únicamente la variante seleccionada del carrito. */
    fun quitarArticulo(item: ArticuloCarrito) {
        val current = _items.value?.toMutableList() ?: mutableListOf()
        val index = current.indexOfFirst {
            it.product.id == item.product.id &&
            it.selectedSauces == item.selectedSauces &&
            it.notes == item.notes
        }
        if (index != -1) {
            current.removeAt(index)
            _items.value = current
        }
    }

    /** Cambia la cantidad de la primera variante encontrada; cero elimina el producto. */
    fun actualizarCantidad(productId: String, quantity: Int) {
        if (quantity <= 0) {
            quitarProducto(productId)
            return
        }
        val current = _items.value?.toMutableList() ?: mutableListOf()
        current.find { it.product.id == productId }?.quantity = quantity
        _items.value = current
    }

    /** Cambia la cantidad de una variante concreta; cero la elimina. */
    fun actualizarCantidadArticulo(item: ArticuloCarrito, quantity: Int) {
        if (quantity <= 0) {
            quitarArticulo(item)
            return
        }
        val current = _items.value?.toMutableList() ?: mutableListOf()
        val found = current.find {
            it.product.id == item.product.id &&
            it.selectedSauces == item.selectedSauces &&
            it.notes == item.notes
        }
        if (found != null) {
            found.quantity = quantity
            _items.value = current
        }
    }

    /** Vacía el carrito después de registrar correctamente el pedido o al reiniciarlo. */
    fun vaciarCarrito() {
        _items.value = emptyList()
    }

    fun obtenerTotal(): Double {
        return _items.value?.sumOf { it.subtotal } ?: 0.0
    }

    fun obtenerAhorroTotal(): Double {
        return _items.value?.sumOf { it.totalSavings } ?: 0.0
    }
}
