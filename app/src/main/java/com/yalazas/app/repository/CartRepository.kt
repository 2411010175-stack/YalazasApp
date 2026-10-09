package com.yalazas.app.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.yalazas.app.model.CartItem
import com.yalazas.app.model.Product

object CartRepository {
    private val _items = MutableLiveData<List<CartItem>>(emptyList())
    val items: LiveData<List<CartItem>> = _items

    fun addProduct(
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
            current.add(CartItem(product, quantity, notes, selectedSauces))
        }

        _items.value = current
    }

    fun removeProduct(productId: String) {
        val current = _items.value?.toMutableList() ?: mutableListOf()
        current.removeAll { it.product.id == productId }
        _items.value = current
    }

    fun removeItem(item: CartItem) {
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

    fun updateQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            removeProduct(productId)
            return
        }
        val current = _items.value?.toMutableList() ?: mutableListOf()
        current.find { it.product.id == productId }?.quantity = quantity
        _items.value = current
    }

    fun updateItemQuantity(item: CartItem, quantity: Int) {
        if (quantity <= 0) {
            removeItem(item)
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

    fun clearCart() {
        _items.value = emptyList()
    }

    fun getTotal(): Double {
        return _items.value?.sumOf { it.subtotal } ?: 0.0
    }

    fun getTotalSavings(): Double {
        return _items.value?.sumOf { it.totalSavings } ?: 0.0
    }
}
