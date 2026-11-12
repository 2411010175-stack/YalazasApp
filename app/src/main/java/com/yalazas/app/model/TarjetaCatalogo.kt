package com.yalazas.app.model

/** Datos mínimos para dibujar la carta; la descripción completa permanece en `products`. */
data class TarjetaCatalogo(
    val name: String = "",
    val tagline: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val originalPrice: Double = 0.0,
    val isOffer: Boolean = false,
    val offerTag: String = "",
    val imageUrl: String = "",
    val category: String = "",
    val isAvailable: Boolean = true,
    val allowsSauces: Boolean = false,
    val maxSauces: Int = 0,
    val availableSauces: List<String> = emptyList()
) {
    fun aProducto(id: String) = Product(
        id = id, name = name, tagline = tagline, description = description, price = price,
        originalPrice = originalPrice, isOffer = isOffer, offerTag = offerTag,
        imageUrl = imageUrl, category = category, isAvailable = isAvailable,
        allowsSauces = allowsSauces, maxSauces = maxSauces,
        availableSauces = availableSauces
    )

    companion object {
        fun desde(producto: Product) = TarjetaCatalogo(
            name = producto.name, tagline = producto.tagline,
            description = producto.description.take(120), price = producto.price,
            originalPrice = producto.originalPrice, isOffer = producto.isOffer,
            offerTag = producto.offerTag, imageUrl = producto.imageUrl,
            category = producto.category, isAvailable = producto.isAvailable,
            allowsSauces = producto.allowsSauces, maxSauces = producto.maxSauces,
            availableSauces = producto.availableSauces
        )
    }
}
