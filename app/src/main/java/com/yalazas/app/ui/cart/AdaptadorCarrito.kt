package com.yalazas.app.ui.cart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yalazas.app.R
import com.yalazas.app.model.ArticuloCarrito
import com.yalazas.app.utilidades.ImagenCatalogo
import java.util.Locale

/** Dibuja cada artículo del carrito y comunica cambios de cantidad. */
class AdaptadorCarrito(
    private val onQuantityChange: (ArticuloCarrito, Int) -> Unit
) : RecyclerView.Adapter<AdaptadorCarrito.CartViewHolder>() {

    private var items: List<ArticuloCarrito> = emptyList()

    fun submitList(newItems: List<ArticuloCarrito>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart_row, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.cartItemName)
        private val sauces: TextView = itemView.findViewById(R.id.cartItemSauces)
        private val notes: TextView = itemView.findViewById(R.id.cartItemNotes)
        private val offerTag: TextView = itemView.findViewById(R.id.cartItemOfferTag)
        private val price: TextView = itemView.findViewById(R.id.cartItemPrice)
        private val quantity: TextView = itemView.findViewById(R.id.cartItemQuantity)
        private val image: android.widget.ImageView = itemView.findViewById(R.id.cartItemImage)
        private val btnMinus: ImageButton = itemView.findViewById(R.id.btnMinus)
        private val btnPlus: ImageButton = itemView.findViewById(R.id.btnPlus)

        fun bind(item: ArticuloCarrito) {
            name.text = item.product.name

            if (item.selectedSauces.isNotEmpty()) {
                sauces.visibility = View.VISIBLE
                val recargo = if (item.recargoSalsasPorUnidad > 0.0) {
                    " · + S/ ${String.format(Locale.US, "%.2f", item.recargoSalsasPorUnidad)} por unidad"
                } else ""
                sauces.text = "🍗 Salsas: ${item.selectedSauces.joinToString(", ")}$recargo"
            } else {
                sauces.visibility = View.GONE
            }

            if (item.notes.isNotBlank()) {
                notes.visibility = View.VISIBLE
                notes.text = "📝 Nota: ${item.notes}"
            } else {
                notes.visibility = View.GONE
            }

            if (item.product.isOffer) {
                offerTag.visibility = View.VISIBLE
                offerTag.text = if (item.product.offerTag.isNotBlank()) {
                    item.product.offerTag
                } else if (item.product.hasDiscount) {
                    "🔥 ${item.product.discountPercent}% OFF"
                } else {
                    "🔥 OFERTA"
                }
            } else {
                offerTag.visibility = View.GONE
            }

            price.text = "S/ ${String.format(Locale.US, "%.2f", item.subtotal)}"
            quantity.text = item.quantity.toString()

            image.load(ImagenCatalogo.miniatura(item.product.imageUrl, 160, 160)) {
                crossfade(true)
                size(160, 160)
                placeholder(android.R.color.darker_gray)
                error(android.R.drawable.ic_menu_report_image)
            }

            btnMinus.setOnClickListener {
                onQuantityChange(item, item.quantity - 1)
            }

            btnPlus.setOnClickListener {
                onQuantityChange(item, item.quantity + 1)
            }
        }
    }
}
