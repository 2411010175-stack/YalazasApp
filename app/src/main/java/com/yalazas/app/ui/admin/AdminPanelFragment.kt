package com.yalazas.app.ui.admin

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.yalazas.app.R
import com.yalazas.app.model.Product
import java.util.Locale
import java.util.UUID

class AdminPanelFragment : Fragment(R.layout.fragment_admin_panel) {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference
    private val productsCollection = db.collection("products")

    private var selectedImageUri: Uri? = null
    private var editingProduct: Product? = null
    private var isOfferModeArg: Boolean = false

    // Vistas principales
    private lateinit var tvPanelTitle: TextView
    private lateinit var tvPanelSubtitle: TextView
    private lateinit var tvModeBadge: TextView
    private lateinit var etProductName: TextInputEditText
    private lateinit var etProductTagline: TextInputEditText
    private lateinit var etProductDescription: TextInputEditText
    private lateinit var etProductPrice: TextInputEditText
    private lateinit var etProductCategory: TextInputEditText
    private lateinit var etProductImageUrl: TextInputEditText
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var switchAvailable: SwitchMaterial
    private lateinit var tvStockStatusText: TextView
    private lateinit var ivProductImage: ImageView
    private lateinit var tvImagePlaceholderBadge: TextView
    private lateinit var tvImageStatus: TextView
    private lateinit var uploadProgress: ProgressBar
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDelete: MaterialButton

    // Vistas de Oferta
    private lateinit var switchProductOffer: SwitchMaterial
    private lateinit var tvOfferStatusText: TextView
    private lateinit var layoutOfferFields: LinearLayout
    private lateinit var etProductOriginalPrice: TextInputEditText
    private lateinit var tvDiscountCalculated: TextView
    private lateinit var chipGroupOfferTags: ChipGroup
    private lateinit var etProductOfferTag: TextInputEditText

    // Vistas de Salsas y Combos
    private lateinit var switchAllowsSauces: SwitchMaterial
    private lateinit var tvSaucesStatusText: TextView
    private lateinit var layoutSaucesFields: LinearLayout
    private lateinit var chipGroupMaxSauces: ChipGroup
    private lateinit var etMaxSauces: TextInputEditText
    private lateinit var chipGroupAvailableSauces: ChipGroup

    // Imágenes predeterminadas de alta calidad por categoría para Yalazas
    private val categoryDefaultImages = mapOf(
        "Alitas" to "https://images.unsplash.com/photo-1567620832903-9fc6debc209f?auto=format&fit=crop&w=800&q=80",
        "Hamburguesas" to "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=800&q=80",
        "Salchipapas" to "https://images.unsplash.com/photo-1585109649139-366815a0d713?auto=format&fit=crop&w=800&q=80",
        "Combos" to "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?auto=format&fit=crop&w=800&q=80",
        "Bebidas" to "https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=800&q=80",
        "Porciones" to "https://images.unsplash.com/photo-1573080496219-bb080dd4f877?auto=format&fit=crop&w=800&q=80"
    )

    private val fallbackDefaultImage = "https://images.unsplash.com/photo-1567620832903-9fc6debc209f?auto=format&fit=crop&w=800&q=80"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            etProductImageUrl.setText("") // Limpiar URL manual si se eligió de galería
            ivProductImage.load(uri) {
                crossfade(true)
                placeholder(android.R.color.darker_gray)
            }
            tvImagePlaceholderBadge.visibility = View.GONE
            tvImageStatus.text = "Foto de galería seleccionada"
            tvImageStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)

        // Botón Volver
        view.findViewById<ImageButton>(R.id.btnBackPanel).setOnClickListener {
            findNavController().popBackStack()
        }

        // Obtener argumentos
        isOfferModeArg = arguments?.getBoolean("isOfferMode", false) ?: false
        editingProduct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("product", Product::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("product")
        }

        setupChipsCategory()
        setupStockSwitch()
        setupOfferControls()
        setupSaucesControls()
        setupManualUrlInput()
        setupImageButtons(view)
        setupFormMode()

        // Guardar / Actualizar Producto
        btnSave.setOnClickListener {
            handleSaveProduct()
        }

        // Eliminar Producto
        btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun bindViews(view: View) {
        tvPanelTitle = view.findViewById(R.id.tvPanelTitle)
        tvPanelSubtitle = view.findViewById(R.id.tvPanelSubtitle)
        tvModeBadge = view.findViewById(R.id.tvModeBadge)
        etProductName = view.findViewById(R.id.etProductName)
        etProductTagline = view.findViewById(R.id.etProductTagline)
        etProductDescription = view.findViewById(R.id.etProductDescription)
        etProductPrice = view.findViewById(R.id.etProductPrice)
        etProductCategory = view.findViewById(R.id.etProductCategory)
        etProductImageUrl = view.findViewById(R.id.etProductImageUrl)
        chipGroupCategory = view.findViewById(R.id.chipGroupCategory)
        switchAvailable = view.findViewById(R.id.switchProductAvailable)
        tvStockStatusText = view.findViewById(R.id.tvStockStatusText)
        ivProductImage = view.findViewById(R.id.ivProductImage)
        tvImagePlaceholderBadge = view.findViewById(R.id.tvImagePlaceholderBadge)
        tvImageStatus = view.findViewById(R.id.tvImageStatus)
        uploadProgress = view.findViewById(R.id.uploadProgress)
        btnSave = view.findViewById(R.id.btnSaveProduct)
        btnDelete = view.findViewById(R.id.btnDeleteProduct)

        // Vistas de Ofertas
        switchProductOffer = view.findViewById(R.id.switchProductOffer)
        tvOfferStatusText = view.findViewById(R.id.tvOfferStatusText)
        layoutOfferFields = view.findViewById(R.id.layoutOfferFields)
        etProductOriginalPrice = view.findViewById(R.id.etProductOriginalPrice)
        tvDiscountCalculated = view.findViewById(R.id.tvDiscountCalculated)
        chipGroupOfferTags = view.findViewById(R.id.chipGroupOfferTags)
        etProductOfferTag = view.findViewById(R.id.etProductOfferTag)

        // Vistas de Salsas y Combos
        switchAllowsSauces = view.findViewById(R.id.switchAllowsSauces)
        tvSaucesStatusText = view.findViewById(R.id.tvSaucesStatusText)
        layoutSaucesFields = view.findViewById(R.id.layoutSaucesFields)
        chipGroupMaxSauces = view.findViewById(R.id.chipGroupMaxSauces)
        etMaxSauces = view.findViewById(R.id.etMaxSauces)
        chipGroupAvailableSauces = view.findViewById(R.id.chipGroupAvailableSauces)
    }

    private fun setupChipsCategory() {
        val chipMap = mapOf(
            R.id.chipAlitas to "Alitas",
            R.id.chipHamburguesas to "Hamburguesas",
            R.id.chipSalchipapas to "Salchipapas",
            R.id.chipCombos to "Combos",
            R.id.chipBebidas to "Bebidas",
            R.id.chipPapas to "Porciones"
        )

        chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val categoryName = chipMap[checkedIds.first()] ?: return@setOnCheckedStateChangeListener
                etProductCategory.setText(categoryName)

                // Si no tiene imagen personalizada ni URL manual, actualizamos preview con la foto por defecto
                if (selectedImageUri == null && etProductImageUrl.text.isNullOrBlank()) {
                    loadDefaultImageForCategory(categoryName)
                }
            }
        }

        etProductCategory.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim() ?: ""
                if (selectedImageUri == null && etProductImageUrl.text.isNullOrBlank() && text.isNotEmpty()) {
                    loadDefaultImageForCategory(text)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupOfferControls() {
        switchProductOffer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutOfferFields.visibility = View.VISIBLE
                tvOfferStatusText.text = "🟢 Oferta activa"
                tvOfferStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
                if (etProductOfferTag.text.isNullOrBlank()) {
                    etProductOfferTag.setText("🔥 OFERTA")
                }
            } else {
                layoutOfferFields.visibility = View.GONE
                tvOfferStatusText.text = "⚪ Precio regular (Sin oferta)"
                tvOfferStatusText.setTextColor(Color.parseColor("#9EADA4"))
            }
            recalculateDiscountPreview()
        }

        val tagMap = mapOf(
            R.id.chipTagOferta to "🔥 OFERTA",
            R.id.chipTagFlash to "⚡ FLASH",
            R.id.chipTag2x1 to "🍗 2x1",
            R.id.chipTagCombo to "✨ COMBO PROMO",
            R.id.chipTagPromoDia to "⭐ PROMO DEL DÍA"
        )

        chipGroupOfferTags.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val tag = tagMap[checkedIds.first()] ?: return@setOnCheckedStateChangeListener
                etProductOfferTag.setText(tag)
            }
        }

        val discountWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                recalculateDiscountPreview()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etProductOriginalPrice.addTextChangedListener(discountWatcher)
        etProductPrice.addTextChangedListener(discountWatcher)
    }

    private fun setupSaucesControls() {
        switchAllowsSauces.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutSaucesFields.visibility = View.VISIBLE
                val max = etMaxSauces.text.toString().trim().toIntOrNull() ?: 2
                tvSaucesStatusText.text = "🟢 Permite elegir hasta $max salsa${if (max != 1) "s" else ""}"
                tvSaucesStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
            } else {
                layoutSaucesFields.visibility = View.GONE
                tvSaucesStatusText.text = "⚪ Sin selección de salsas"
                tvSaucesStatusText.setTextColor(Color.parseColor("#9EADA4"))
            }
        }

        val qtyChipMap = mapOf(
            R.id.chipSauceQty1 to 1,
            R.id.chipSauceQty2 to 2,
            R.id.chipSauceQty3 to 3,
            R.id.chipSauceQty4 to 4
        )

        chipGroupMaxSauces.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val qty = qtyChipMap[checkedIds.first()] ?: 2
                etMaxSauces.setText(qty.toString())
            }
        }

        etMaxSauces.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val qty = s?.toString()?.trim()?.toIntOrNull() ?: 0
                if (switchAllowsSauces.isChecked) {
                    tvSaucesStatusText.text = if (qty > 0) {
                        "🟢 Permite elegir hasta $qty salsa${if (qty != 1) "s" else ""}"
                    } else {
                        "🟡 Ingresa la cantidad máxima de salsas"
                    }
                    tvSaucesStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun getSelectedSaucesList(): List<String> {
        val chipMap = mapOf(
            R.id.chipSauceAcevichada to "Acevichada",
            R.id.chipSauceBbq to "BBQ Clásica",
            R.id.chipSauceBufalo to "Búfalo Picante",
            R.id.chipSauceMaracuya to "Maracuyá Dulce",
            R.id.chipSauceHoney to "Honey Mustard",
            R.id.chipSauceMango to "Mango Habanero",
            R.id.chipSauceAjo to "Ajo Parmesano",
            R.id.chipSauceTartara to "Tártara Especial"
        )
        val checkedIds = chipGroupAvailableSauces.checkedChipIds
        return checkedIds.mapNotNull { chipMap[it] }
    }

    private fun setSelectedSaucesChips(sauces: List<String>) {
        val chipMap = mapOf(
            "Acevichada" to R.id.chipSauceAcevichada,
            "BBQ Clásica" to R.id.chipSauceBbq,
            "Búfalo Picante" to R.id.chipSauceBufalo,
            "Maracuyá Dulce" to R.id.chipSauceMaracuya,
            "Honey Mustard" to R.id.chipSauceHoney,
            "Mango Habanero" to R.id.chipSauceMango,
            "Ajo Parmesano" to R.id.chipSauceAjo,
            "Tártara Especial" to R.id.chipSauceTartara
        )

        if (sauces.isEmpty()) {
            chipMap.values.forEach { view?.findViewById<Chip>(it)?.isChecked = true }
        } else {
            chipMap.values.forEach { view?.findViewById<Chip>(it)?.isChecked = false }
            sauces.forEach { sauceName ->
                chipMap.entries.find { it.key.contains(sauceName, ignoreCase = true) || sauceName.contains(it.key, ignoreCase = true) }?.let {
                    view?.findViewById<Chip>(it.value)?.isChecked = true
                }
            }
        }
    }

    private fun recalculateDiscountPreview() {
        if (!switchProductOffer.isChecked) return

        val originalPrice = etProductOriginalPrice.text.toString().trim().toDoubleOrNull() ?: 0.0
        val finalPrice = etProductPrice.text.toString().trim().toDoubleOrNull() ?: 0.0

        if (originalPrice > 0.0 && finalPrice > 0.0) {
            if (originalPrice > finalPrice) {
                val savings = originalPrice - finalPrice
                val percent = (((originalPrice - finalPrice) / originalPrice) * 100).toInt()
                tvDiscountCalculated.text = "🎉 ¡Ahorro para el cliente: S/ ${String.format(Locale.US, "%.2f", savings)} ($percent% OFF)!"
                tvDiscountCalculated.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
            } else {
                tvDiscountCalculated.text = "⚠️ El precio regular (S/ $originalPrice) debe ser mayor al precio de oferta (S/ $finalPrice)"
                tvDiscountCalculated.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_yellow))
            }
        } else if (originalPrice <= 0.0) {
            tvDiscountCalculated.text = "💡 Ingresa el precio regular para calcular el ahorro del cliente"
            tvDiscountCalculated.setTextColor(Color.parseColor("#9EADA4"))
        } else {
            tvDiscountCalculated.text = "💡 Ingresa el precio final rebajado de oferta"
            tvDiscountCalculated.setTextColor(Color.parseColor("#9EADA4"))
        }
    }

    private fun setupStockSwitch() {
        switchAvailable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                tvStockStatusText.text = "🟢 Activo para pedidos"
                tvStockStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
            } else {
                tvStockStatusText.text = "🔴 No disponible / Pausado"
                tvStockStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_pink))
            }
        }
    }

    private fun setupManualUrlInput() {
        etProductImageUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString()?.trim() ?: ""
                if (url.isNotBlank()) {
                    selectedImageUri = null
                    tvImagePlaceholderBadge.visibility = View.GONE
                    tvImageStatus.text = "Usando enlace web directo"
                    tvImageStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_yellow))
                    ivProductImage.load(url) {
                        crossfade(true)
                        placeholder(android.R.color.darker_gray)
                        error(android.R.drawable.ic_menu_report_image)
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupImageButtons(view: View) {
        // Seleccionar de Galería
        view.findViewById<MaterialButton>(R.id.btnSelectImage).setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Restablecer a Imagen Predeterminada
        view.findViewById<MaterialButton>(R.id.btnDefaultImage).setOnClickListener {
            selectedImageUri = null
            etProductImageUrl.setText("")
            val category = etProductCategory.text.toString().trim()
            loadDefaultImageForCategory(category)
            Toast.makeText(requireContext(), "Restablecida imagen automática de Yalazas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDefaultImageForCategory(category: String) {
        val imageUrl = categoryDefaultImages[category] ?: fallbackDefaultImage
        ivProductImage.load(imageUrl) {
            crossfade(true)
            placeholder(android.R.color.darker_gray)
        }
        tvImagePlaceholderBadge.visibility = View.VISIBLE
        tvImageStatus.text = "Usando foto automática de Yalazas ($category)"
        tvImageStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_nav_orange))
    }

    private fun setupFormMode() {
        val product = editingProduct
        if (product != null) {
            // Modo EDICIÓN
            tvPanelTitle.text = if (product.isOffer) "Editar Oferta" else "Editar Producto"
            tvPanelSubtitle.text = "Modifica los precios, detalles o disponibilidad"
            tvModeBadge.text = if (product.isOffer) "OFERTA" else "EDICIÓN"
            tvModeBadge.setBackgroundResource(if (product.isOffer) R.drawable.bg_admin_chip_category else R.drawable.bg_admin_gear_btn)
            btnSave.text = "Guardar Cambios"
            btnDelete.visibility = View.VISIBLE

            etProductName.setText(product.name)
            etProductTagline.setText(product.tagline)
            etProductDescription.setText(product.description)
            etProductPrice.setText(String.format(Locale.US, "%.2f", product.price))
            etProductCategory.setText(product.category)
            switchAvailable.isChecked = product.isAvailable

            // Configurar oferta
            if (product.isOffer || isOfferModeArg) {
                switchProductOffer.isChecked = true
                layoutOfferFields.visibility = View.VISIBLE
                tvOfferStatusText.text = "🟢 Oferta activa"
                tvOfferStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
                if (product.originalPrice > 0.0) {
                    etProductOriginalPrice.setText(String.format(Locale.US, "%.2f", product.originalPrice))
                } else {
                    // Si se está convirtiendo a oferta, poner el precio actual como precio regular
                    etProductOriginalPrice.setText(String.format(Locale.US, "%.2f", product.price))
                }
                etProductOfferTag.setText(if (product.offerTag.isNotBlank()) product.offerTag else "🔥 OFERTA")
            } else {
                switchProductOffer.isChecked = false
                layoutOfferFields.visibility = View.GONE
                tvOfferStatusText.text = "⚪ Precio regular (Sin oferta)"
                tvOfferStatusText.setTextColor(Color.parseColor("#9EADA4"))
            }

            // Configurar salsas
            if (product.allowsSauces) {
                switchAllowsSauces.isChecked = true
                layoutSaucesFields.visibility = View.VISIBLE
                val max = if (product.maxSauces > 0) product.maxSauces else 2
                etMaxSauces.setText(max.toString())
                when (max) {
                    1 -> view?.findViewById<Chip>(R.id.chipSauceQty1)?.isChecked = true
                    2 -> view?.findViewById<Chip>(R.id.chipSauceQty2)?.isChecked = true
                    3 -> view?.findViewById<Chip>(R.id.chipSauceQty3)?.isChecked = true
                    4 -> view?.findViewById<Chip>(R.id.chipSauceQty4)?.isChecked = true
                }
                tvSaucesStatusText.text = "🟢 Permite elegir hasta $max salsa${if (max != 1) "s" else ""}"
                tvSaucesStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
                setSelectedSaucesChips(product.availableSauces)
            } else {
                switchAllowsSauces.isChecked = false
                layoutSaucesFields.visibility = View.GONE
                tvSaucesStatusText.text = "⚪ Sin selección de salsas"
                tvSaucesStatusText.setTextColor(Color.parseColor("#9EADA4"))
                setSelectedSaucesChips(emptyList())
            }

            // Seleccionar chip si coincide
            selectChipByCategory(product.category)

            if (product.imageUrl.isNotBlank()) {
                etProductImageUrl.setText(product.imageUrl)
                ivProductImage.load(product.imageUrl) {
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                    error(android.R.drawable.ic_menu_report_image)
                }
                tvImagePlaceholderBadge.visibility = View.GONE
                tvImageStatus.text = "Imagen actual del producto cargada"
                tvImageStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
            } else {
                loadDefaultImageForCategory(product.category)
            }
        } else if (isOfferModeArg) {
            // Modo CREACIÓN DE OFERTA
            tvPanelTitle.text = "Crear Nueva Oferta"
            tvPanelSubtitle.text = "Configura un nuevo plato o combo promocional"
            tvModeBadge.text = "OFERTA"
            tvModeBadge.setBackgroundResource(R.drawable.bg_admin_chip_category)
            btnSave.text = "Guardar Oferta"
            btnDelete.visibility = View.GONE
            switchAvailable.isChecked = true
            switchProductOffer.isChecked = true
            layoutOfferFields.visibility = View.VISIBLE
            tvOfferStatusText.text = "🟢 Oferta activa"
            tvOfferStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
            etProductOfferTag.setText("🔥 OFERTA")

            // Salsas por defecto desactivado
            switchAllowsSauces.isChecked = false
            layoutSaucesFields.visibility = View.GONE
            tvSaucesStatusText.text = "⚪ Sin selección de salsas"
            tvSaucesStatusText.setTextColor(Color.parseColor("#9EADA4"))
            setSelectedSaucesChips(emptyList())

            // Por defecto categoría Combos o Alitas
            view?.findViewById<Chip>(R.id.chipCombos)?.isChecked = true
            etProductCategory.setText("Combos")
            loadDefaultImageForCategory("Combos")
        } else {
            // Modo CREACIÓN REGULAR
            tvPanelTitle.text = "Agregar Nuevo Producto"
            tvPanelSubtitle.text = "Configura los detalles de tu nuevo plato o combo"
            tvModeBadge.text = "NUEVO"
            tvModeBadge.setBackgroundResource(R.drawable.bg_admin_ver_todas)
            btnSave.text = "Guardar Producto"
            btnDelete.visibility = View.GONE
            switchAvailable.isChecked = true
            switchProductOffer.isChecked = false
            layoutOfferFields.visibility = View.GONE

            // Salsas por defecto desactivado
            switchAllowsSauces.isChecked = false
            layoutSaucesFields.visibility = View.GONE
            tvSaucesStatusText.text = "⚪ Sin selección de salsas"
            tvSaucesStatusText.setTextColor(Color.parseColor("#9EADA4"))
            setSelectedSaucesChips(emptyList())

            // Por defecto categoría Alitas
            view?.findViewById<Chip>(R.id.chipAlitas)?.isChecked = true
            etProductCategory.setText("Alitas")
            loadDefaultImageForCategory("Alitas")
        }

        recalculateDiscountPreview()
    }

    private fun selectChipByCategory(category: String) {
        val categoryLower = category.lowercase().trim()
        when {
            categoryLower.contains("alita") -> view?.findViewById<Chip>(R.id.chipAlitas)?.isChecked = true
            categoryLower.contains("hamburguesa") -> view?.findViewById<Chip>(R.id.chipHamburguesas)?.isChecked = true
            categoryLower.contains("salchipapa") -> view?.findViewById<Chip>(R.id.chipSalchipapas)?.isChecked = true
            categoryLower.contains("combo") -> view?.findViewById<Chip>(R.id.chipCombos)?.isChecked = true
            categoryLower.contains("bebida") -> view?.findViewById<Chip>(R.id.chipBebidas)?.isChecked = true
            categoryLower.contains("papa") || categoryLower.contains("porcion") -> view?.findViewById<Chip>(R.id.chipPapas)?.isChecked = true
        }
    }

    private fun handleSaveProduct() {
        val name = etProductName.text.toString().trim()
        val tagline = etProductTagline.text.toString().trim()
        val description = etProductDescription.text.toString().trim()
        val priceStr = etProductPrice.text.toString().trim()
        val category = etProductCategory.text.toString().trim().ifEmpty { "Alitas" }
        val manualUrl = etProductImageUrl.text.toString().trim()
        val isAvailable = switchAvailable.isChecked
        val isOffer = switchProductOffer.isChecked
        val originalPriceStr = etProductOriginalPrice.text.toString().trim()
        var offerTag = etProductOfferTag.text.toString().trim()

        val allowsSauces = switchAllowsSauces.isChecked
        val maxSaucesStr = etMaxSauces.text.toString().trim()
        val maxSauces = if (allowsSauces) (maxSaucesStr.toIntOrNull() ?: 1).coerceAtLeast(1) else 0
        val availableSauces = if (allowsSauces) getSelectedSaucesList() else emptyList()

        if (name.isBlank() || priceStr.isBlank()) {
            Toast.makeText(requireContext(), "Ingresa el nombre y el precio del producto", Toast.LENGTH_SHORT).show()
            return
        }

        val price = priceStr.toDoubleOrNull() ?: 0.0
        if (price <= 0.0) {
            Toast.makeText(requireContext(), "El precio final debe ser mayor a 0", Toast.LENGTH_SHORT).show()
            return
        }

        if (allowsSauces && availableSauces.isEmpty()) {
            Toast.makeText(requireContext(), "Selecciona al menos una salsa disponible para este producto o combo", Toast.LENGTH_SHORT).show()
            return
        }

        val originalPrice: Double
        if (isOffer) {
            originalPrice = originalPriceStr.toDoubleOrNull() ?: 0.0
            if (originalPrice > 0.0 && originalPrice <= price) {
                Toast.makeText(
                    requireContext(),
                    "El precio regular (S/ $originalPrice) debe ser mayor que el precio de oferta (S/ $price)",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            if (offerTag.isBlank()) {
                offerTag = if (originalPrice > price) {
                    val percent = (((originalPrice - price) / originalPrice) * 100).toInt()
                    "🔥 $percent% OFF"
                } else {
                    "🔥 OFERTA"
                }
            }
        } else {
            originalPrice = 0.0
            offerTag = ""
        }

        if (selectedImageUri != null) {
            // Subir a Firebase Storage con tolerancia a fallos
            uploadImageWithFallback(name, tagline, description, price, originalPrice, isOffer, offerTag, category, isAvailable, manualUrl, allowsSauces, maxSauces, availableSauces)
        } else {
            val finalImageUrl = when {
                manualUrl.isNotBlank() -> manualUrl
                editingProduct?.imageUrl?.isNotBlank() == true -> editingProduct!!.imageUrl
                else -> categoryDefaultImages[category] ?: fallbackDefaultImage
            }
            saveToFirestore(name, tagline, description, price, originalPrice, isOffer, offerTag, category, isAvailable, finalImageUrl, allowsSauces, maxSauces, availableSauces)
        }
    }

    private fun uploadImageWithFallback(
        name: String,
        tagline: String,
        description: String,
        price: Double,
        originalPrice: Double,
        isOffer: Boolean,
        offerTag: String,
        category: String,
        isAvailable: Boolean,
        manualUrlFallback: String,
        allowsSauces: Boolean,
        maxSauces: Int,
        availableSauces: List<String>
    ) {
        val imageUri = selectedImageUri ?: return

        btnSave.isEnabled = false
        btnDelete.isEnabled = false
        uploadProgress.visibility = View.VISIBLE
        tvImageStatus.text = "Subiendo imagen a la nube..."

        val imageRef = storage.child("products/${UUID.randomUUID()}.jpg")
        imageRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Error en almacenamiento")
                imageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                saveToFirestore(name, tagline, description, price, originalPrice, isOffer, offerTag, category, isAvailable, downloadUri.toString(), allowsSauces, maxSauces, availableSauces)
            }
            .addOnFailureListener {
                val fallbackUrl = if (manualUrlFallback.isNotBlank()) {
                    manualUrlFallback
                } else {
                    categoryDefaultImages[category] ?: fallbackDefaultImage
                }
                Toast.makeText(
                    requireContext(),
                    "Almacenamiento no disponible. Guardando con imagen automática de Yalazas...",
                    Toast.LENGTH_LONG
                ).show()
                saveToFirestore(name, tagline, description, price, originalPrice, isOffer, offerTag, category, isAvailable, fallbackUrl, allowsSauces, maxSauces, availableSauces)
            }
    }

    private fun saveToFirestore(
        name: String,
        tagline: String,
        description: String,
        price: Double,
        originalPrice: Double,
        isOffer: Boolean,
        offerTag: String,
        category: String,
        isAvailable: Boolean,
        imageUrl: String,
        allowsSauces: Boolean,
        maxSauces: Int,
        availableSauces: List<String>
    ) {
        btnSave.isEnabled = false
        btnDelete.isEnabled = false
        uploadProgress.visibility = View.VISIBLE

        val isEdit = editingProduct != null
        val docRef = if (isEdit) {
            productsCollection.document(editingProduct!!.id)
        } else {
            productsCollection.document()
        }

        val product = Product(
            id = docRef.id,
            name = name,
            tagline = tagline,
            description = description,
            price = price,
            originalPrice = originalPrice,
            isOffer = isOffer,
            offerTag = offerTag,
            category = category,
            imageUrl = imageUrl,
            isAvailable = isAvailable,
            allowsSauces = allowsSauces,
            maxSauces = maxSauces,
            availableSauces = availableSauces
        )

        docRef.set(product)
            .addOnSuccessListener {
                val msg = if (isOffer) {
                    if (isEdit) "¡Oferta actualizada con éxito!" else "¡Oferta creada y publicada!"
                } else {
                    if (isEdit) "¡Producto actualizado con éxito!" else "¡Producto agregado a la carta!"
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al guardar en Firestore: ${e.message}", Toast.LENGTH_LONG).show()
                btnSave.isEnabled = true
                btnDelete.isEnabled = true
                uploadProgress.visibility = View.GONE
            }
    }

    private fun showDeleteConfirmationDialog() {
        val product = editingProduct ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Producto")
            .setMessage("¿Estás seguro de que deseas eliminar \"${product.name}\" de la carta de Yalazas?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteProductFromFirestore(product.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteProductFromFirestore(productId: String) {
        btnSave.isEnabled = false
        btnDelete.isEnabled = false
        uploadProgress.visibility = View.VISIBLE

        productsCollection.document(productId).delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Producto eliminado de la carta", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_LONG).show()
                btnSave.isEnabled = true
                btnDelete.isEnabled = true
                uploadProgress.visibility = View.GONE
            }
    }
}

