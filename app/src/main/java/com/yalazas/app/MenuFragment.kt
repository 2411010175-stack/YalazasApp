package com.yalazas.app

import com.yalazas.app.ui.location.LocationPickerBottomSheet
import com.yalazas.app.ui.menu.ProductAdapter
import com.yalazas.app.ui.menu.ProductDetailBottomSheet
import com.yalazas.app.ui.menu.SauceSelectionBottomSheet

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.yalazas.app.MainActivity
import com.yalazas.app.R
import com.yalazas.app.repository.AuthRepository
import com.yalazas.app.repository.CartRepository
import com.yalazas.app.viewmodel.MenuState
import com.yalazas.app.viewmodel.MenuViewModel

class MenuFragment : Fragment(R.layout.fragment_menu) {

    private val viewModel: MenuViewModel by viewModels()
    private lateinit var productAdapter: ProductAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupProfileHeader(view)
        setupRecyclerView(view)
        setupObservers(view)
        setupListeners(view)

        // Refrescar los productos cada vez que volvemos a la pantalla
        viewModel.loadProducts()
    }

    private fun setupProfileHeader(view: View) {
        val tvGreetingName = view.findViewById<TextView>(R.id.tvGreetingName)
        val tvMenuAvatarInitials = view.findViewById<TextView>(R.id.tvMenuAvatarInitials)
        val tvMenuDeliveryAddress = view.findViewById<TextView>(R.id.tvMenuDeliveryAddress)
        val layoutMenuDeliveryPill = view.findViewById<View>(R.id.layoutMenuDeliveryPill)
        val layoutMenuAvatar = view.findViewById<View>(R.id.layoutMenuAvatar)

        // Observar perfil en tiempo real
        AuthRepository.liveProfile.observe(viewLifecycleOwner) { profile ->
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
            val currentProfile = AuthRepository.currentProfile
            LocationPickerBottomSheet.newInstance(
                currentAddress = currentProfile?.address.orEmpty(),
                currentReference = currentProfile?.reference.orEmpty(),
                lat = currentProfile?.latitude ?: 0.0,
                lng = currentProfile?.longitude ?: 0.0
            ) { selectedAddress, _, _, _ ->
                tvMenuDeliveryAddress.text = "📍 $selectedAddress"
                Toast.makeText(requireContext(), "¡Ubicación de entrega guardada! 🛵", Toast.LENGTH_SHORT).show()
            }.show(parentFragmentManager, LocationPickerBottomSheet.TAG)
        }

        // Click en el avatar para ir a Perfil
        layoutMenuAvatar?.setOnClickListener {
            findNavController().navigate(R.id.nav_profile)
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.menuRecyclerView)
        productAdapter = ProductAdapter(
            isAdminMode = false,
            onItemClick = { product ->
                ProductDetailBottomSheet.newInstance(product)
                    .show(parentFragmentManager, ProductDetailBottomSheet.TAG)
            },
            onActionClick = { product, clickedView ->
                if (product.allowsSauces && product.maxSauces > 0) {
                    SauceSelectionBottomSheet.newInstance(product)
                        .show(parentFragmentManager, SauceSelectionBottomSheet.TAG)
                } else {
                    CartRepository.addProduct(product)
                    (activity as? MainActivity)?.playAddToCartAnimation(clickedView)
                }
            }
        )
        recyclerView.adapter = productAdapter
    }

    private fun setupObservers(view: View) {
        val progressBar = view.findViewById<View>(R.id.menuProgressBar)
        val recyclerView = view.findViewById<RecyclerView>(R.id.menuRecyclerView)
        val tvEmptyMenu = view.findViewById<View>(R.id.tvEmptyMenu)

        viewModel.menuState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MenuState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    tvEmptyMenu.visibility = View.GONE
                }
                is MenuState.Success -> {
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
                is MenuState.Error -> {
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    tvEmptyMenu.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupListeners(view: View) {
        // Búsqueda en vivo
        view.findViewById<TextInputEditText>(R.id.inputSearchMenu)?.addTextChangedListener { text ->
            viewModel.filterProducts(text?.toString() ?: "")
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
            viewModel.setCategory(category)
        }
    }
}
