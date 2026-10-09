package com.yalazas.app.ui.menu

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.yalazas.app.R
import com.yalazas.app.model.Product
import com.yalazas.app.repository.CartRepository
import java.util.Locale

class SauceSelectionBottomSheet : BottomSheetDialogFragment() {

    private var product: Product? = null
    private var quantity: Int = 1
    private val selectedSauces = mutableListOf<String>()

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
        return inflater.inflate(R.layout.bottom_sheet_sauce_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prod = product ?: run {
            dismiss()
            return
        }

        val tvName = view.findViewById<TextView>(R.id.tvSauceSheetProductName)
        val tvDesc = view.findViewById<TextView>(R.id.tvSauceSheetProductDesc)
        val tvOfferBadge = view.findViewById<TextView>(R.id.tvSauceSheetOfferBadge)
        val tvOriginalPrice = view.findViewById<TextView>(R.id.tvSauceSheetOriginalPrice)
        val tvPrice = view.findViewById<TextView>(R.id.tvSauceSheetPrice)
        val tvInstructions = view.findViewById<TextView>(R.id.tvSauceSheetInstructions)
        val tvCounterBadge = view.findViewById<TextView>(R.id.tvSauceSheetCounterBadge)
        val chipGroupSauces = view.findViewById<ChipGroup>(R.id.chipGroupSheetSauces)
        val etNotes = view.findViewById<TextInputEditText>(R.id.etSauceSheetNotes)
        val btnMinus = view.findViewById<ImageButton>(R.id.btnSauceQtyMinus)
        val tvQuantity = view.findViewById<TextView>(R.id.tvSauceQuantity)
        val btnPlus = view.findViewById<ImageButton>(R.id.btnSauceQtyPlus)
        val btnAddToCart = view.findViewById<MaterialButton>(R.id.btnSauceAddToCart)

        // 1. Información del Producto
        tvName.text = prod.name
        tvDesc.text = prod.description.ifBlank { prod.tagline }
        tvPrice.text = "S/ ${String.format(Locale.US, "%.2f", prod.price)}"

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
            } else {
                tvOriginalPrice.visibility = View.GONE
            }
        } else {
            tvOfferBadge.visibility = View.GONE
            tvOriginalPrice.visibility = View.GONE
        }

        val maxSauces = if (prod.maxSauces > 0) prod.maxSauces else 2
        tvInstructions.text = "Puedes elegir hasta $maxSauces salsa${if (maxSauces != 1) "s" else ""} diferentes para tu orden."

        fun updateCounter() {
            val count = selectedSauces.size
            tvCounterBadge.text = "$count / $maxSauces seleccionadas"
            if (count == maxSauces) {
                tvCounterBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
            } else {
                tvCounterBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_date_orange))
            }
        }

        fun updatePriceButton() {
            tvQuantity.text = quantity.toString()
            val total = prod.price * quantity
            btnAddToCart.text = "Agregar • S/ ${String.format(Locale.US, "%.2f", total)}"
        }

        updateCounter()
        updatePriceButton()

        // 2. Poblar Chips de Salsas
        val availableSauces = if (prod.availableSauces.isNotEmpty()) {
            prod.availableSauces
        } else {
            Product.DEFAULT_YALAZA_SAUCES
        }

        chipGroupSauces.removeAllViews()
        for (sauce in availableSauces) {
            val chip = Chip(requireContext()).apply {
                text = sauce
                isCheckable = true
                isClickable = true
                textSize = 13f
                shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(24f).build()
                chipStrokeWidth = 2f
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#38483F"))
                chipBackgroundColor = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    ),
                    intArrayOf(
                        Color.parseColor("#1B3B28"),
                        Color.parseColor("#18221D")
                    )
                )
                setTextColor(
                    ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf(-android.R.attr.state_checked)
                        ),
                        intArrayOf(
                            ContextCompat.getColor(context, R.color.yalaza_lime),
                            Color.parseColor("#D0DDD5")
                        )
                    )
                )
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (selectedSauces.size >= maxSauces) {
                        chip.isChecked = false
                        Toast.makeText(
                            requireContext(),
                            "Máximo $maxSauces salsa${if (maxSauces != 1) "s" else ""} para este producto",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnCheckedChangeListener
                    }
                    if (!selectedSauces.contains(sauce)) {
                        selectedSauces.add(sauce)
                    }
                } else {
                    selectedSauces.remove(sauce)
                }
                updateCounter()
            }

            chipGroupSauces.addView(chip)
        }

        // 3. Cantidad Stepper
        btnMinus.setOnClickListener {
            if (quantity > 1) {
                quantity--
                updatePriceButton()
            }
        }

        btnPlus.setOnClickListener {
            if (quantity < 50) {
                quantity++
                updatePriceButton()
            }
        }

        // 4. Agregar al Carrito
        btnAddToCart.setOnClickListener {
            if (selectedSauces.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Por favor, elige al menos 1 salsa para tu orden",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val notes = etNotes.text.toString().trim()
            CartRepository.addProduct(
                product = prod,
                quantity = quantity,
                notes = notes,
                selectedSauces = selectedSauces.toList()
            )

            (activity as? com.yalazas.app.MainActivity)?.playAddToCartAnimation(btnAddToCart)

            dismiss()
        }
    }

    companion object {
        const val TAG = "SauceSelectionBottomSheet"
        private const val ARG_PRODUCT = "arg_product"

        fun newInstance(product: Product): SauceSelectionBottomSheet {
            return SauceSelectionBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PRODUCT, product)
                }
            }
        }
    }
}
