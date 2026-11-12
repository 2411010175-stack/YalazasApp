package com.yalazas.app

import com.yalazas.app.ui.ubicacion.HojaSeleccionUbicacion
import com.yalazas.app.ui.menu.AdaptadorProductos
import com.yalazas.app.ui.menu.HojaDetalleProducto
import com.yalazas.app.ui.menu.HojaSeleccionSalsas

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.yalazas.app.MainActivity
import com.yalazas.app.R
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioCarrito
import com.yalazas.app.modelovista.EstadoMenu
import com.yalazas.app.modelovista.ModeloVistaMenu
import kotlinx.coroutines.launch

/** Muestra los productos disponibles y conecta filtros, búsqueda y carrito. */
class MenuFragment : Fragment(R.layout.fragment_menu) {

    private val modeloVista: ModeloVistaMenu by viewModels()
    private lateinit var productAdapter: AdaptadorProductos

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupProfileHeader(view)
        configurarLista(view)
        observarEstados(view)
        configurarEventos(view)

        // El listener solo permanece activo mientras la pantalla es visible.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                modeloVista.observarProductos()
            }
        }
    }

    private fun setupProfileHeader(view: View) {
        val tvGreetingName = view.findViewById<TextView>(R.id.tvGreetingName)
        val tvMenuAvatarInitials = view.findViewById<TextView>(R.id.tvMenuAvatarInitials)
        val tvMenuDeliveryAddress = view.findViewById<TextView>(R.id.tvMenuDeliveryAddress)
        val layoutMenuDeliveryPill = view.findViewById<View>(R.id.layoutMenuDeliveryPill)
        val layoutMenuAvatar = view.findViewById<View>(R.id.layoutMenuAvatar)

        // Observar perfil en tiempo real
        RepositorioAutenticacion.liveProfile.observe(viewLifecycleOwner) { profile ->
            if (profile != null && profile.name.isNotBlank()) {
                val firstName = profile.name.trim().split(" ").firstOrNull() ?: profile.name
                tvGreetingName.text = "¡Hola, $firstName! 👋"

                val initials = profile.name.trim()
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .map { it.first().uppercase() }
                    .joinToString("")
                tvMenuAvatarInitials.text = if (initials.isNotBlank()) initials else "YA"

                if (profile.address.isNotBlank()) {
                    tvMenuDeliveryAddress.text = "📍 ${profile.address}"
                } else {
                    tvMenuDeliveryAddress.text = "📍 Toca para fijar tu ubicación en el mapa 🛵"
                }
            } else {
                tvGreetingName.text = "¡Hola, Yalaza Lover! 👋"
                tvMenuAvatarInitials.text = "YA"
                tvMenuDeliveryAddress.text = "📍 Toca para fijar tu ubicación en el mapa 🛵"
            }
        }

        // Click en la píldora de dirección para abrir OpenStreetMap Location Picker
        layoutMenuDeliveryPill?.setOnClickListener {
            val currentProfile = RepositorioAutenticacion.currentProfile
            HojaSeleccionUbicacion.crear(
                currentAddress = currentProfile?.address.orEmpty(),
                currentReference = currentProfile?.reference.orEmpty(),
                lat = currentProfile?.latitude ?: 0.0,
                lng = currentProfile?.longitude ?: 0.0
            ) { selectedAddress, _, _, _ ->
                tvMenuDeliveryAddress.text = "📍 $selectedAddress"
                Toast.makeText(requireContext(), "¡Ubicación de entrega guardada! 🛵", Toast.LENGTH_SHORT).show()
            }.show(parentFragmentManager, HojaSeleccionUbicacion.TAG)
        }

        // Click en el avatar para ir a Perfil
        layoutMenuAvatar?.setOnClickListener {
            findNavController().navigate(R.id.nav_profile)
        }
    }

    /** Conecta el adaptador con el carrito y abre el detalle del producto seleccionado. */
    private fun configurarLista(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.menuRecyclerView)
        productAdapter = AdaptadorProductos(
            isAdminMode = false,
            onItemClick = { product ->
                abrirProducto(product.id) { completo ->
                    HojaDetalleProducto.crear(completo)
                        .show(parentFragmentManager, HojaDetalleProducto.TAG)
                }
            },
            onActionClick = { product, clickedView ->
                abrirProducto(product.id) { completo ->
                    if (completo.allowsSauces && completo.maxSauces > 0) {
                        HojaSeleccionSalsas.crear(completo)
                            .show(parentFragmentManager, HojaSeleccionSalsas.TAG)
                    } else {
                        RepositorioCarrito.agregarProducto(completo)
                        (activity as? MainActivity)?.animarAgregadoAlCarrito(clickedView)
                    }
                }
            }
        )
        recyclerView.adapter = productAdapter
    }

    private fun abrirProducto(id: String, alAbrir: (com.yalazas.app.model.Product) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val completo = modeloVista.completarProducto(id)
                if (completo != null && isAdded) alAbrir(completo)
                else if (isAdded) Toast.makeText(requireContext(), "El producto ya no está disponible.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                if (isAdded) Toast.makeText(requireContext(), "No se pudo cargar el producto. Revisa tu conexión.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Refleja en pantalla la carga, los productos filtrados o un error. */
    private fun observarEstados(view: View) {
        val progressBar = view.findViewById<View>(R.id.menuProgressBar)
        val recyclerView = view.findViewById<RecyclerView>(R.id.menuRecyclerView)
        val tvEmptyMenu = view.findViewById<View>(R.id.tvEmptyMenu)

        modeloVista.estadoMenu.observe(viewLifecycleOwner) { state ->
            when (state) {
                is EstadoMenu.Cargando -> {
                    progressBar.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    tvEmptyMenu.visibility = View.GONE
                }
                is EstadoMenu.Exito -> {
                    progressBar.visibility = View.GONE
                    if (state.products.isEmpty()) {
                        recyclerView.visibility = View.GONE
                        tvEmptyMenu.visibility = View.VISIBLE
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        tvEmptyMenu.visibility = View.GONE
                        productAdapter.submitList(state.products)
                    }
                }
                is EstadoMenu.Error -> {
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    tvEmptyMenu.visibility = View.VISIBLE
                }
            }
        }
    }

    /** Vincula la búsqueda y las categorías con el modelo de vista. */
    private fun configurarEventos(view: View) {
        // Búsqueda en vivo
        view.findViewById<TextInputEditText>(R.id.inputSearchMenu)?.addTextChangedListener { text ->
            modeloVista.filtrarProductos(text?.toString() ?: "")
        }

        // Selector dinámico de categorías
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupMenuCategories)
        chipGroup?.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipCatAll
            val category = when (checkedId) {
                R.id.chipCatAlitas -> "ALITAS"
                R.id.chipCatCombos -> "COMBOS"
                R.id.chipCatBurgers -> "HAMBURGUESAS"
                R.id.chipCatSalchipapas -> "SALCHIPAPAS"
                R.id.chipCatDrinks -> "BEBIDAS"
                R.id.chipCatSides -> "PORCIONES"
                else -> "TODOS"
            }
            modeloVista.seleccionarCategoria(category)
        }
    }
}
