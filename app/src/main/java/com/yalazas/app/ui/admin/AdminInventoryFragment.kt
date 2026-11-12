package com.yalazas.app.ui.admin

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
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
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.model.Product
import com.yalazas.app.model.CalculadoraInsumos
import com.yalazas.app.model.Insumo
import com.yalazas.app.model.RecetaProducto
import com.yalazas.app.model.PromocionProducto
import com.yalazas.app.repositorio.RepositorioPromociones
import com.yalazas.app.repositorio.RepositorioInsumos
import com.yalazas.app.repositorio.ServiciosFirebase
import com.yalazas.app.ui.menu.AdaptadorProductos
import java.util.Locale

/** Muestra y filtra el catálogo que administra el restaurante. */
class AdminInventoryFragment : Fragment(R.layout.fragment_admin_inventory) {

    private val db = ServiciosFirebase.firestore
    private var productsListener: ListenerRegistration? = null
    private var promotionsListener: ListenerRegistration? = null
    private var ingredientsListener: ListenerRegistration? = null
    private var recipesListener: ListenerRegistration? = null
    private lateinit var productAdapter: AdaptadorProductos

    private var allProducts = listOf<Product>()
    private var promociones = emptyMap<String, PromocionProducto>()
    private var insumos = emptyMap<String, Insumo>()
    private var recetas = emptyMap<String, RecetaProducto>()
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
        productAdapter = AdaptadorProductos(isAdminMode = true) { selectedProduct, _ ->
            // El adaptador puede mostrar una copia con descuento; el editor recibe el producto base.
            val productoBase = allProducts.firstOrNull { it.id == selectedProduct.id } ?: selectedProduct
            findNavController().navigate(
                R.id.nav_admin_panel,
                bundleOf("product" to productoBase)
            )
        }
        rvInventory.layoutManager = LinearLayoutManager(requireContext())
        rvInventory.adapter = productAdapter

        ingredientsListener = RepositorioInsumos.escucharInsumos(
            { insumos = it.associateBy(Insumo::id); filtrarProductos(currentSearchQuery) },
            { if (isAdded) Toast.makeText(requireContext(), "No se pudieron cargar insumos", Toast.LENGTH_SHORT).show() }
        )
        recipesListener = RepositorioInsumos.escucharRecetas(
            { recetas = it.associateBy(RecetaProducto::productId); filtrarProductos(currentSearchQuery) },
            { if (isAdded) Toast.makeText(requireContext(), "No se pudieron cargar recetas", Toast.LENGTH_SHORT).show() }
        )

        // 4. Búsqueda en tiempo real
        inputSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                currentSearchQuery = s?.toString() ?: ""
                filtrarProductos(currentSearchQuery)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 5. Escuchar cambios de productos en tiempo real
        escucharProductosEnTiempoReal(progressBar)
        promotionsListener = RepositorioPromociones.escuchar(
            alCambiar = { nuevas ->
                promociones = nuevas.associateBy { it.productId }
                filtrarProductos(currentSearchQuery)
            },
            alError = {
                promociones = emptyMap()
                filtrarProductos(currentSearchQuery)
            }
        )
    }

    private fun showNewOfferDialog() {
        val contenido = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_opciones_oferta, null)
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setView(contenido)
            .create()
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        contenido.findViewById<View>(R.id.cardCrearOferta).setOnClickListener {
            dialogo.dismiss()
            findNavController().navigate(R.id.nav_admin_panel, bundleOf("isOfferMode" to true))
        }
        contenido.findViewById<View>(R.id.cardAplicarOferta).setOnClickListener {
            dialogo.dismiss()
            showSelectProductForOfferDialog()
        }
        contenido.findViewById<View>(R.id.btnCancelarOferta).setOnClickListener { dialogo.dismiss() }
        dialogo.show()
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showSelectProductForOfferDialog() {
        val productosBase = allProducts.filterNot { it.isOffer }
        if (productosBase.isEmpty()) {
            Toast.makeText(requireContext(), "No hay productos registrados en la carta", Toast.LENGTH_SHORT).show()
            return
        }

        val productNames = productosBase.map { product ->
            val oferta = promociones[product.id]
            val status = if (oferta?.active == true) {
                "🔥 Oferta: S/ ${String.format(Locale.US, "%.2f", oferta.offerPrice)}"
            } else {
                "Regular: S/ ${String.format(Locale.US, "%.2f", product.price)}"
            }
            "${product.name} — $status"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Selecciona el producto para la oferta")
            .setItems(productNames) { _, which ->
                mostrarEditorPromocion(productosBase[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Edita la promoción separada del producto base y permite retirarla. */
    private fun mostrarEditorPromocion(producto: Product) {
        val contenido = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialogo_editar_promocion, null)
        val promocionActual = promociones[producto.id]
        val campoPrecio = contenido.findViewById<TextInputEditText>(R.id.inputPrecioPromocion)
        val campoEtiqueta = contenido.findViewById<TextInputEditText>(R.id.inputEtiquetaPromocion)
        val layoutPrecio = contenido.findViewById<TextInputLayout>(R.id.layoutPrecioPromocion)

        contenido.findViewById<TextView>(R.id.tvProductoPromocion).text = producto.name
        contenido.findViewById<TextView>(R.id.tvPrecioRegularPromocion).text =
            "Precio regular: S/ ${String.format(Locale.US, "%.2f", producto.price)}"
        if (promocionActual != null) {
            campoPrecio.setText(String.format(Locale.US, "%.2f", promocionActual.offerPrice))
            campoEtiqueta.setText(promocionActual.offerTag)
        }

        val constructor = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Oferta para producto existente")
            .setView(contenido)
            .setPositiveButton("Guardar oferta", null)
            .setNegativeButton("Cancelar", null)
        if (promocionActual != null) constructor.setNeutralButton("Quitar oferta", null)
        val dialogo = constructor.create()
        dialogo.setOnShowListener {
            val botonGuardar = dialogo.getButton(DialogInterface.BUTTON_POSITIVE)
            botonGuardar.setOnClickListener {
                val precio = campoPrecio.text.toString().trim().toDoubleOrNull()
                if (precio == null || !precio.isFinite() || precio <= 0.0 || precio >= producto.price) {
                    layoutPrecio.error = "Ingresa un precio mayor a 0 y menor al regular"
                    return@setOnClickListener
                }
                layoutPrecio.error = null
                botonGuardar.isEnabled = false
                RepositorioPromociones.guardar(producto, precio, campoEtiqueta.text.toString()) { resultado ->
                    if (!isAdded) return@guardar
                    resultado.onSuccess {
                        Toast.makeText(requireContext(), "Oferta guardada sin cambiar el producto base", Toast.LENGTH_SHORT).show()
                        dialogo.dismiss()
                    }.onFailure { error ->
                        botonGuardar.isEnabled = true
                        Toast.makeText(requireContext(), "No se pudo guardar la oferta: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (promocionActual != null) {
                val botonQuitar = dialogo.getButton(DialogInterface.BUTTON_NEUTRAL)
                botonQuitar.setOnClickListener {
                    botonQuitar.isEnabled = false
                    RepositorioPromociones.quitar(producto.id) { resultado ->
                        if (!isAdded) return@quitar
                        resultado.onSuccess {
                            Toast.makeText(requireContext(), "Oferta retirada; precio regular restaurado", Toast.LENGTH_SHORT).show()
                            dialogo.dismiss()
                        }.onFailure { error ->
                            botonQuitar.isEnabled = true
                            Toast.makeText(requireContext(), "No se pudo quitar la oferta: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialogo.show()
    }

    /** Refresca el catálogo administrativo al cambiar la colección `products`. */
    private fun escucharProductosEnTiempoReal(progressBar: ProgressBar) {
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
                    filtrarProductos(currentSearchQuery)
                }
            }
    }

    private fun filtrarProductos(query: String) {
        val lower = query.lowercase().trim()
        val catalogoVisible = allProducts.map { producto ->
            val oferta = promociones[producto.id]?.aplicarA(producto) ?: producto
            val receta = recetas[producto.id]
            if (receta == null) oferta else oferta.copy(
                isAvailable = oferta.isAvailable && CalculadoraInsumos.evaluarProducto(receta, oferta, insumos).disponible
            )
        }
        val filtered = if (lower.isEmpty()) {
            catalogoVisible
        } else {
            catalogoVisible.filter {
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
        promotionsListener?.remove()
        promotionsListener = null
        ingredientsListener?.remove()
        ingredientsListener = null
        recipesListener?.remove()
        recipesListener = null
    }
}
