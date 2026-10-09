package com.yalazas.app.ui.menu

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yalazas.app.MainActivity
import com.yalazas.app.R
import com.yalazas.app.model.Product
import com.yalazas.app.repository.CartRepository
import java.util.Locale

class ProductDetailBottomSheet : BottomSheetDialogFragment() {

    private var product: Product? = null
    private var quantity: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        product = arguments?.getParcelable(ARG_PRODUCT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_product_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prod = product ?: run {
            dismiss()
            return
        }

        val ivHero = view.findViewById<ImageView>(R.id.ivDetailHeroImage)
        val tvCategoryBadge = view.findViewById<TextView>(R.id.tvDetailCategoryBadge)
        val tvOfferBadge = view.findViewById<TextView>(R.id.tvDetailOfferBadge)
        val tvName = view.findViewById<TextView>(R.id.tvDetailProductName)
        val tvTagline = view.findViewById<TextView>(R.id.tvDetailProductTagline)
        val tvPrice = view.findViewById<TextView>(R.id.tvDetailPrice)
        val tvOriginalPrice = view.findViewById<TextView>(R.id.tvDetailOriginalPrice)
        val tvDiscountPill = view.findViewById<TextView>(R.id.tvDetailDiscountPill)
        val tvDescription = view.findViewById<TextView>(R.id.tvDetailDescription)

        val cardSaucesInfo = view.findViewById<View>(R.id.cardDetailSaucesInfo)
        val tvSauceAllowance = view.findViewById<TextView>(R.id.tvDetailSauceAllowance)

        val layoutNotes = view.findViewById<TextInputLayout>(R.id.layoutDetailNotes)
        val etNotes = view.findViewById<TextInputEditText>(R.id.etDetailNotes)

        val layoutQtyStepper = view.findViewById<LinearLayout>(R.id.layoutDetailQtyStepper)
        val btnMinus = view.findViewById<ImageButton>(R.id.btnDetailQtyMinus)
        val tvQuantity = view.findViewById<TextView>(R.id.tvDetailQuantity)
        val btnPlus = view.findViewById<ImageButton>(R.id.btnDetailQtyPlus)
        val btnAction = view.findViewById<MaterialButton>(R.id.btnDetailAction)

        // 1. Imagen y Categoría
        ivHero.load(prod.imageUrl) {
            crossfade(true)
            placeholder(android.R.color.darker_gray)
            error(android.R.drawable.ic_menu_report_image)
        }

        val categoryText = when (prod.category.uppercase(Locale.ROOT)) {
            "ALITAS" -> "🍗 ALITAS"
            "COMBOS" -> "🔥 COMBO"
            "HAMBURGUESAS" -> "🍔 HAMBURGUESA"
            "SALCHIPAPAS" -> "🍟 SALCHIPAPA"
            "BEBIDAS" -> "🥤 BEBIDA"
            "PORCIONES" -> "✨ PORCIÓN"
            else -> "✨ ${prod.category.uppercase(Locale.ROOT)}"
        }
        tvCategoryBadge.text = categoryText

        // 2. Información General
        tvName.text = prod.name
        tvTagline.text = prod.tagline.ifBlank { "¡Especialidad de la casa!" }
        tvDescription.text = prod.description.ifBlank { prod.tagline.ifBlank { "Disfruta del auténtico sabor Yalaza con insumos de alta calidad." } }
        tvPrice.text = "S/ ${String.format(Locale.US, "%.2f", prod.price)}"

        // 3. Ofertas y Descuentos
        if (prod.isOffer) {
            tvOfferBadge.visibility = View.VISIBLE
            val badgeText = if (prod.offerTag.isNotBlank()) {
                prod.offerTag
            } else if (prod.hasDiscount) {
                "🔥 ${prod.discountPercent}% OFF"
            } else {
                "🔥 OFERTA"
            }
            tvOfferBadge.text = badgeText

            if (prod.hasDiscount) {
                tvOriginalPrice.visibility = View.VISIBLE
                tvOriginalPrice.paintFlags = tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvOriginalPrice.text = "S/ ${String.format(Locale.US, "%.2f", prod.originalPrice)}"

                val savings = prod.originalPrice - prod.price
                if (savings > 0) {
                    tvDiscountPill.visibility = View.VISIBLE
                    tvDiscountPill.text = "Ahorras S/ ${String.format(Locale.US, "%.2f", savings)}"
                } else {
                    tvDiscountPill.visibility = View.GONE
                }
            } else {
                tvOriginalPrice.visibility = View.GONE
                tvDiscountPill.visibility = View.GONE
            }
        } else {
            tvOfferBadge.visibility = View.GONE
            tvOriginalPrice.visibility = View.GONE
            tvDiscountPill.visibility = View.GONE
        }

        // 4. Verificación de Combos / Salsas
        val hasSauces = prod.allowsSauces && prod.maxSauces > 0
        if (hasSauces) {
            cardSaucesInfo.visibility = View.VISIBLE
            val maxSauces = prod.maxSauces
            tvSauceAllowance.text = "Hasta $maxSauces salsa${if (maxSauces != 1) "s" else ""}"

            // Para combos, las salsas y notas se configuran en el SauceSelectionBottomSheet
            layoutNotes.visibility = View.GONE
            layoutQtyStepper.visibility = View.GONE

            btnAction.text = "Elegir Salsas 🍗"
            btnAction.setIconResource(android.R.drawable.ic_input_add)

            btnAction.setOnClickListener {
                dismiss()
                SauceSelectionBottomSheet.newInstance(prod)
                    .show(parentFragmentManager, SauceSelectionBottomSheet.TAG)
            }
        } else {
            cardSaucesInfo.visibility = View.GONE
            layoutNotes.visibility = View.VISIBLE
            layoutQtyStepper.visibility = View.VISIBLE

            fun updateButtonPrice() {
                tvQuantity.text = quantity.toString()
                val total = prod.price * quantity
                btnAction.text = "Agregar • S/ ${String.format(Locale.US, "%.2f", total)}"
            }

            updateButtonPrice()

            btnMinus.setOnClickListener {
                if (quantity > 1) {
                    quantity--
                    updateButtonPrice()
                }
            }

            btnPlus.setOnClickListener {
                if (quantity < 50) {
                    quantity++
                    updateButtonPrice()
                }
            }

            btnAction.setOnClickListener {
                val notes = etNotes.text.toString().trim()
                CartRepository.addProduct(
                    product = prod,
                    quantity = quantity,
                    notes = notes
                )
                (activity as? MainActivity)?.playAddToCartAnimation(btnAction)
                dismiss()
            }
        }
    }

    companion object {
        const val TAG = "ProductDetailBottomSheet"
        private const val ARG_PRODUCT = "arg_product"

        fun newInstance(product: Product): ProductDetailBottomSheet {
            return ProductDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PRODUCT, product)
                }
            }
        }
    }
}
