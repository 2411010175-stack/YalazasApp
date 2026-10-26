package com.yalazas.app.model

/** Oferta de un producto existente; se guarda aparte del documento de `products`. */
data class PromocionProducto(
    val productId: String = "",
    val offerPrice: Double = 0.0,
    val offerTag: String = "",
    val active: Boolean = true
) {
    /** Crea una copia para la vista; el producto y su precio base no se modifican. */
    fun aplicarA(producto: Product): Product {
        if (!active || productId != producto.id || producto.isOffer ||
            !offerPrice.isFinite() || offerPrice <= 0.0 || offerPrice >= producto.price
        ) return producto

        val etiqueta = offerTag.ifBlank { "🔥 OFERTA" }
        val decorado = DecoradorOferta(ProductoBase(producto), offerPrice, etiqueta)
        return producto.copy(
            price = decorado.precioUnitario,
            originalPrice = producto.price,
            isOffer = true,
            offerTag = etiqueta
        )
    }
}
