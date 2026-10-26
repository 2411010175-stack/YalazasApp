package com.yalazas.app.model

import java.util.Locale

/** Precio y descripción de un producto configurado, sin modificar el producto original. */
interface ProductoConfigurado {
    val descripcion: String
    val precioUnitario: Double
    val ahorroUnitario: Double
}

class ProductoBase(private val producto: Product) : ProductoConfigurado {
    override val descripcion: String = producto.name
    override val precioUnitario: Double =
        if (producto.hasDiscount) producto.originalPrice else producto.price
    override val ahorroUnitario: Double = 0.0
}

abstract class DecoradorProducto(protected val anterior: ProductoConfigurado) : ProductoConfigurado {
    override val descripcion: String get() = anterior.descripcion
    override val precioUnitario: Double get() = anterior.precioUnitario
    override val ahorroUnitario: Double get() = anterior.ahorroUnitario
}

/** Aplica una oferta al precio calculado, conservando intacto el objeto base. */
class DecoradorOferta(
    anterior: ProductoConfigurado,
    private val precioOferta: Double,
    private val etiqueta: String
) : DecoradorProducto(anterior) {
    override val descripcion: String
        get() = "${anterior.descripcion} · ${etiqueta.ifBlank { "Oferta" }}"
    override val precioUnitario: Double get() = precioOferta
    override val ahorroUnitario: Double
        get() = anterior.ahorroUnitario + (anterior.precioUnitario - precioOferta).coerceAtLeast(0.0)
}

/** Añade las salsas elegidas y cobra S/ 1 por cada una fuera de las incluidas. */
class DecoradorSalsas(
    anterior: ProductoConfigurado,
    private val salsas: List<String>,
    private val cantidadIncluida: Int
) : DecoradorProducto(anterior) {
    val cantidadExtra: Int get() = (salsas.size - cantidadIncluida).coerceAtLeast(0)
    val recargo: Double get() = cantidadExtra * PRECIO_SALSA_EXTRA

    override val descripcion: String
        get() = buildString {
            append(anterior.descripcion)
            append(" · Salsas: ")
            append(salsas.joinToString(", "))
            if (cantidadExtra > 0) {
                append(" (+ S/ ")
                append(String.format(Locale.US, "%.2f", recargo))
                append(" en extras)")
            }
        }
    override val precioUnitario: Double get() = anterior.precioUnitario + recargo

    companion object {
        const val PRECIO_SALSA_EXTRA = 1.0
    }
}

/** Compone oferta y salsas para mostrar o registrar el artículo del pedido. */
object DecoradoresProducto {
    fun configurar(producto: Product, salsas: List<String> = emptyList()): ProductoConfigurado {
        var configurado: ProductoConfigurado = ProductoBase(producto)
        if (producto.isOffer) {
            configurado = DecoradorOferta(configurado, producto.price, producto.offerTag)
        }
        if (salsas.isNotEmpty()) {
            configurado = DecoradorSalsas(configurado, salsas, producto.maxSauces)
        }
        return configurado
    }
}
