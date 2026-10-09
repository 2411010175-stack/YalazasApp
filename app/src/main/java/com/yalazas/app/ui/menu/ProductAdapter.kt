package com.yalazas.app.ui.menu

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yalazas.app.R
import com.yalazas.app.model.Product
import java.util.Locale

class ProductAdapter(
    private val isAdminMode: Boolean = false,
    private val onItemClick: ((Product) -> Unit)? = null,
    private val onActionClick: (Product, View) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private var products: List<Product> = emptyList()

    fun submitList(newList: List<Product>) {
        products = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured_card, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView as MaterialCardView
        private val image: ImageView = itemView.findViewById(R.id.featuredImage)
        private val tvOfferBadge: TextView = itemView.findViewById(R.id.tvOfferBadge)
        private val title: TextView = itemView.findViewById(R.id.featuredTitle)
        private val tagline: TextView = itemView.findViewById(R.id.featuredTagline)
        private val description: TextView = itemView.findViewById(R.id.featuredDescription)
        private val originalPrice: TextView = itemView.findViewById(R.id.featuredOriginalPrice)
        private val price: TextView = itemView.findViewById(R.id.featuredPrice)
        private val btnAction: MaterialButton = itemView.findViewById(R.id.btnAddToCart)

        fun bind(product: Product) {
            val context = itemView.context

            // Asignar color dinámico basado en oferta o categoría
            val accent = when {
                product.isOffer -> ContextCompat.getColor(context, R.color.admin_nav_orange)
                product.category.equals("Alitas", ignoreCase = true) -> ContextCompat.getColor(context, R.color.yalaza_lime)
                product.category.equals("Combos", ignoreCase = true) -> ContextCompat.getColor(context, R.color.yalaza_yellow)
                else -> ContextCompat.getColor(context, R.color.yalaza_pink)
            }

            card.strokeColor = accent
            title.text = product.name
            title.setTextColor(accent)

            // Manejo de insignias y precios de Oferta
            if (product.isOffer) {
                tvOfferBadge.visibility = View.VISIBLE
                val badgeText = if (product.offerTag.isNotBlank()) {
                    product.offerTag
                } else if (product.hasDiscount) {
                    "🔥 ${product.discountPercent}% OFF"
                } else {
                    "🔥 OFERTA"
                }
                tvOfferBadge.text = badgeText

                if (product.hasDiscount) {
                    originalPrice.visibility = View.VISIBLE
                    originalPrice.paintFlags = originalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    originalPrice.text = "S/ ${String.format(Locale.US, "%.2f", product.originalPrice)}"
                } else {
                    originalPrice.visibility = View.GONE
                }
            } else {
                tvOfferBadge.visibility = View.GONE
                originalPrice.visibility = View.GONE
            }

            if (isAdminMode && !product.isAvailable) {
                tagline.text = "⚠️ [AGOTADO / PAUSADO] • ${product.tagline}"
                card.alpha = 0.7f
            } else {
                tagline.text = product.tagline
                card.alpha = 1.0f
            }
            tagline.setTextColor(accent)

            description.text = product.description
            price.text = "S/ ${String.format(Locale.US, "%.2f", product.price)}"
            price.setTextColor(accent)

            image.load(product.imageUrl) {
                crossfade(true)
                placeholder(android.R.color.darker_gray)
                error(android.R.drawable.ic_menu_report_image)
            }

            if (isAdminMode) {
                btnAction.text = "Editar"
                btnAction.setIconResource(android.R.drawable.ic_menu_edit)
                card.setOnClickListener {
                    onActionClick(product, card)
                }
            } else {
                if (product.allowsSauces && product.maxSauces > 0) {
                    btnAction.text = "Elegir Salsas"
                    btnAction.setIconResource(android.R.drawable.ic_input_add)
                } else {
                    btnAction.text = "Agregar"
                    btnAction.setIconResource(android.R.drawable.ic_menu_add)
                }
                card.setOnClickListener {
                    onItemClick?.invoke(product)
                }
            }

            btnAction.setOnClickListener {
                onActionClick(product, btnAction)
            }
        }
    }
}