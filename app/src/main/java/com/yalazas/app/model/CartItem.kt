package com.yalazas.app.model

data class CartItem(
    val product: Product,
    var quantity: Int,
    var notes: String = "",
    var selectedSauces: List<String> = emptyList()
) {
    val subtotal: Double
        get() = product.price * quantity

    val totalSavings: Double
        get() = product.savedAmountPerUnit * quantity
}
