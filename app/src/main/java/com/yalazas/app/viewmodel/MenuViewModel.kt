package com.yalazas.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yalazas.app.model.Product
import com.yalazas.app.repository.ProductRepository
import kotlinx.coroutines.launch

sealed class MenuState {
    object Loading : MenuState()
    data class Success(val products: List<Product>) : MenuState()
    data class Error(val message: String) : MenuState()
}

class MenuViewModel : ViewModel() {
    private val repository = ProductRepository()

    private val _menuState = MutableLiveData<MenuState>()
    val menuState: LiveData<MenuState> = _menuState

    private var allProducts: List<Product> = emptyList()
    private var currentCategory: String = "TODOS"
    private var currentQuery: String = ""

    init {
        loadProducts()
    }

    fun loadProducts() {
        _menuState.value = MenuState.Loading
        viewModelScope.launch {
            try {
                repository.seedInitialProducts()
            } catch (_: Exception) {
                // Si Firestore falla al sembrar datos, igual intentamos leer productos existentes
            }

            val result = repository.getAllProducts()
            if (result.isSuccess) {
                allProducts = result.getOrThrow()
                applyFilters()
            } else {
                _menuState.value = MenuState.Error(result.exceptionOrNull()?.message ?: "Error al cargar el menú")
            }
        }
    }

    fun setCategory(category: String) {
        currentCategory = category.trim().uppercase()
        applyFilters()
    }

    fun filterProducts(query: String) {
        currentQuery = query.trim()
        applyFilters()
    }

    private fun applyFilters() {
        if (allProducts.isEmpty()) {
            _menuState.value = MenuState.Success(emptyList())
            return
        }

        var filtered = allProducts

        // 1. Filtrar por categoría
        if (currentCategory != "TODOS" && currentCategory.isNotBlank()) {
            filtered = filtered.filter { prod ->
                val prodCat = prod.category.trim().uppercase()
                when (currentCategory) {
                    "ALITAS" -> prodCat == "ALITAS" || prodCat.contains("ALITA") || prod.allowsSauces
                    "COMBOS" -> prodCat == "COMBOS" || prodCat.contains("COMBO")
                    "HAMBURGUESAS" -> prodCat == "HAMBURGUESAS" || prodCat.contains("HAMBURGUESA") || prodCat.contains("BURGER")
                    "SALCHIPAPAS" -> prodCat == "SALCHIPAPAS" || prodCat.contains("SALCHIPAPA")
                    "BEBIDAS" -> prodCat == "BEBIDAS" || prodCat.contains("BEBIDA") || prodCat.contains("GASEOSA") || prodCat.contains("JUGO")
                    "PORCIONES" -> prodCat == "PORCIONES" || prodCat.contains("PORCION") || prodCat.contains("PIQUEO") || prodCat.contains("PAPAS")
                    else -> prodCat == currentCategory || prodCat.contains(currentCategory)
                }
            }
        }

        // 2. Filtrar por búsqueda de texto
        if (currentQuery.isNotBlank()) {
            filtered = filtered.filter { prod ->
                prod.name.contains(currentQuery, ignoreCase = true) ||
                prod.description.contains(currentQuery, ignoreCase = true) ||
                prod.tagline.contains(currentQuery, ignoreCase = true) ||
                prod.category.contains(currentQuery, ignoreCase = true)
            }
        }

        _menuState.value = MenuState.Success(filtered)
    }
}
