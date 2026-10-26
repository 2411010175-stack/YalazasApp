package com.yalazas.app.model

/** Producto seleccionado en el carrito. Las salsas y notas distinguen variantes del mismo producto. */
data class ArticuloCarrito(
    val product: Product,
    var quantity: Int,
    var notes: String = "",
    var selectedSauces: List<String> = emptyList()
) {
    /** Decoradores activos para esta variante; `product` permanece sin cambios. */
    val configuracion: ProductoConfigurado
        get() = DecoradoresProducto.configurar(product, selectedSauces)

    val descripcionFinal: String
        get() = configuracion.descripcion

    val precioUnitarioFinal: Double
        get() = configuracion.precioUnitario

    val recargoSalsasPorUnidad: Double
        get() = (configuracion as? DecoradorSalsas)?.recargo ?: 0.0

    /** Importe de esta línea con la cantidad elegida. */
    val subtotal: Double
        get() = precioUnitarioFinal * quantity

    /** Diferencia acumulada entre precio original y precio de oferta. */
    val totalSavings: Double
        get() = configuracion.ahorroUnitario * quantity
}
