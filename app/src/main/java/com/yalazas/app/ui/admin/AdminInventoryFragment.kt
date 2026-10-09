package com.yalazas.app.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.model.Product
import com.yalazas.app.ui.menu.ProductAdapter

class AdminInventoryFragment : Fragment(R.layout.fragment_admin_inventory) {

    private val db = FirebaseFirestore.getInstance()
    private var productsListener: ListenerRegistration? = null
    private lateinit var productAdapter: ProductAdapter

    private var allProducts = listOf<Product>()
    private var currentSearchQuery = ""

    private lateinit var tvProductsCount: TextView
    private lateinit var layoutEmpty: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvProductsCount = view.findViewById(R.id.tvProductsCount)
        layoutEmpty = view.findViewById(R.id.layoutEmptyProducts)
        val btnAddProduct = view.findViewById<MaterialButton>(R.id.btnAddProduct)
        val btnAddOffer = view.findViewById<MaterialButton>(R.id.btnAddOffer)
        val rvInventory = view.findViewById<RecyclerView>(R.id.rvInventory)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val inputSearch = view.findViewById<TextInputEditText>(R.id.inputSearchProduct)

        // 1. Agregar nuevo producto regular
        btnAddProduct.setOnClickListener {
            findNavController().navigate(
                R.id.nav_admin_panel,
                bundleOf("isOfferMode" to false)
            )
        }

        // 2. Nueva Oferta / Promoción
        btnAddOffer.setOnClickListener {
            showNewOfferDialog()
        }

        // 3. Configurar Adapter en Modo Administrador
        productAdapter = ProductAdapter(isAdminMode = true) { selectedProduct, _ ->
            findNavController().navigate(
                R.id.nav_admin_panel,
                bundleOf("product" to selectedProduct)
            )
        }
        rvInventory.layoutManager = LinearLayoutManager(requireContext())
        rvInventory.adapter = productAdapter

        // 4. Búsqueda en tiempo real
        inputSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                currentSearchQuery = s?.toString() ?: ""
                filterProducts(currentSearchQuery)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 5. Escuchar cambios de productos en tiempo real
        listenToProductsRealtime(progressBar)
    }

    private fun showNewOfferDialog() {
        val options = arrayOf(
            "🔥 Crear Nueva Oferta / Combo Promocional",
            "✨ Aplicar Oferta a Producto Existente de la Carta"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Promociones y Ofertas")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Crear nuevo producto en modo oferta
                        findNavController().navigate(
                            R.id.nav_admin_panel,
                            bundleOf("isOfferMode" to true)
                        )
                    }
                    1 -> {
                        // Seleccionar producto existente
                        showSelectProductForOfferDialog()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSelectProductForOfferDialog() {
        if (allProducts.isEmpty()) {
            Toast.makeText(requireContext(), "No hay productos registrados en la carta", Toast.LENGTH_SHORT).show()
            return
        }

        val productNames = allProducts.map { product ->
            val status = if (product.isOffer) "🔥 [Oferta activa: S/ ${product.price}]" else "S/ ${product.price}"
            "${product.name} — $status"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Selecciona el producto para la oferta")
            .setItems(productNames) { _, which ->
                val selectedProduct = allProducts[which]
                findNavController().navigate(
                    R.id.nav_admin_panel,
                    bundleOf(
                        "product" to selectedProduct,
                        "isOfferMode" to true
                    )
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun listenToProductsRealtime(progressBar: ProgressBar) {
        progressBar.visibility = View.VISIBLE
        productsListener?.remove()
        productsListener = db.collection("products")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE
                if (error != null) {
                    Toast.makeText(requireContext(), "Error al cargar inventario: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allProducts = snapshot.toObjects(Product::class.java)
                    tvProductsCount.text = "${allProducts.size} productos"
                    filterProducts(currentSearchQuery)
                }
            }
    }

    private fun filterProducts(query: String) {
        val lower = query.lowercase().trim()
        val filtered = if (lower.isEmpty()) {
            allProducts
        } else {
            allProducts.filter {
                it.name.lowercase().contains(lower) ||
                it.category.lowercase().contains(lower) ||
                it.tagline.lowercase().contains(lower) ||
                (it.isOffer && it.offerTag.lowercase().contains(lower))
            }
        }
        productAdapter.submitList(filtered)
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsListener?.remove()
        productsListener = null
    }
}