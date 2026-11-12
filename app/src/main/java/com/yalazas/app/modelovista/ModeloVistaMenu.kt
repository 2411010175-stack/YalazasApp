package com.yalazas.app.modelovista

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.yalazas.app.model.Product
import com.yalazas.app.repositorio.RepositorioProductos
import kotlinx.coroutines.flow.collect

/** Resultado que observa la pantalla del menú durante la carga y el filtrado. */
sealed class EstadoMenu {
    object Cargando : EstadoMenu()
    data class Exito(val products: List<Product>) : EstadoMenu()
    data class Error(val message: String) : EstadoMenu()
}

/** Mantiene el catálogo cargado y publica la selección visible para la pantalla. */
class ModeloVistaMenu : ViewModel() {
    private val repositorio = RepositorioProductos()

    // La pantalla observa este LiveData y se actualiza cuando cambia el resultado.
    private val _estadoMenu = MutableLiveData<EstadoMenu>()
    val estadoMenu: LiveData<EstadoMenu> = _estadoMenu

    private var todosLosProductos: List<Product> = emptyList()
    private var catalogoRecibido = false
    private var categoriaActual: String = "TODOS"
    private var busquedaActual: String = ""

    /** Recibe cada cambio del catálogo y vuelve a aplicar los filtros activos. */
    suspend fun observarProductos() {
        if (!catalogoRecibido) _estadoMenu.value = EstadoMenu.Cargando
        repositorio.observarCatalogoCompacto().collect { resultado ->
            if (resultado.isSuccess) {
                todosLosProductos = resultado.getOrThrow()
                catalogoRecibido = true
                aplicarFiltros()
            } else {
                todosLosProductos = emptyList()
                catalogoRecibido = false
                _estadoMenu.value = EstadoMenu.Error(
                    resultado.exceptionOrNull()?.message ?: "Error al cargar el menú"
                )
            }
        }
    }

    /** Completa la tarjeta vigente con la descripción antes de abrirla o comprarla. */
    suspend fun completarProducto(id: String): Product? {
        val tarjeta = todosLosProductos.firstOrNull { it.id == id && it.isAvailable } ?: return null
        val completo = repositorio.obtenerProductoCompleto(id) ?: return null
        if (!completo.isAvailable) return null
        val precioBaseTarjeta = if (tarjeta.isOffer && !completo.isOffer) tarjeta.originalPrice
            else tarjeta.price
        if (completo.price != precioBaseTarjeta) return null
        return completo.copy(
            price = tarjeta.price, originalPrice = tarjeta.originalPrice,
            isOffer = tarjeta.isOffer, offerTag = tarjeta.offerTag,
            availableSauces = tarjeta.availableSauces
        )
    }

    /** Selecciona una categoría sin volver a descargar los productos. */
    fun seleccionarCategoria(category: String) {
        categoriaActual = category.trim().uppercase()
        aplicarFiltros()
    }

    /** Aplica la búsqueda de texto sobre el catálogo ya cargado. */
    fun filtrarProductos(query: String) {
        busquedaActual = query.trim()
        aplicarFiltros()
    }

    /** Combina disponibilidad, categoría y texto antes de avisar a la vista. */
    private fun aplicarFiltros() {
        if (!catalogoRecibido) return
        if (todosLosProductos.isEmpty()) {
            _estadoMenu.value = EstadoMenu.Exito(emptyList())
            return
        }

        var filtered = todosLosProductos.filter { it.isAvailable }

        // Cada categoría admite las etiquetas equivalentes usadas en el catálogo.
        if (categoriaActual != "TODOS" && categoriaActual.isNotBlank()) {
            filtered = filtered.filter { prod ->
                val prodCat = prod.category.trim().uppercase()
                when (categoriaActual) {
                    "ALITAS" -> prodCat == "ALITAS" || prodCat.contains("ALITA") || prod.allowsSauces
                    "COMBOS" -> prodCat == "COMBOS" || prodCat.contains("COMBO")
                    "HAMBURGUESAS" -> prodCat == "HAMBURGUESAS" || prodCat.contains("HAMBURGUESA") || prodCat.contains("BURGER")
                    "SALCHIPAPAS" -> prodCat == "SALCHIPAPAS" || prodCat.contains("SALCHIPAPA")
                    "BEBIDAS" -> prodCat == "BEBIDAS" || prodCat.contains("BEBIDA") || prodCat.contains("GASEOSA") || prodCat.contains("JUGO")
                    "PORCIONES" -> prodCat == "PORCIONES" || prodCat.contains("PORCION") || prodCat.contains("PIQUEO") || prodCat.contains("PAPAS")
                    else -> prodCat == categoriaActual || prodCat.contains(categoriaActual)
                }
            }
        }

        // 2. Filtrar por búsqueda de texto
        if (busquedaActual.isNotBlank()) {
            filtered = filtered.filter { prod ->
                prod.name.contains(busquedaActual, ignoreCase = true) ||
                prod.description.contains(busquedaActual, ignoreCase = true) ||
                prod.tagline.contains(busquedaActual, ignoreCase = true) ||
                prod.category.contains(busquedaActual, ignoreCase = true)
            }
        }

        _estadoMenu.value = EstadoMenu.Exito(filtered)
    }
}
