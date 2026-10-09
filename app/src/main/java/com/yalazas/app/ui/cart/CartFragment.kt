package com.yalazas.app.ui.cart

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yalazas.app.R
import com.yalazas.app.repository.AuthRepository
import com.yalazas.app.repository.CartRepository
import com.yalazas.app.ui.location.LocationPickerBottomSheet

class CartFragment : Fragment(R.layout.fragment_cart) {

    private lateinit var adapter: CartAdapter
    private var selectedAddress: String = ""
    private var selectedReference: String = ""
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.cartRecyclerView)
        val tvTotal = view.findViewById<TextView>(R.id.cartTotalPrice)
        val tvSavings = view.findViewById<TextView>(R.id.tvCartSavings)
        val tvEmpty = view.findViewById<TextView>(R.id.cartEmptyText)
        val summaryCard = view.findViewById<View>(R.id.cartSummaryCard)
        val btnSend = view.findViewById<MaterialButton>(R.id.btnSendOrder)
        val layoutDeliverySelector = view.findViewById<View>(R.id.layoutCartDeliverySelector)
        val tvDeliveryAddress = view.findViewById<TextView>(R.id.tvCartDeliveryAddress)

        adapter = CartAdapter { cartItem, newQuantity ->
            CartRepository.updateItemQuantity(cartItem, newQuantity)
        }
        recyclerView.adapter = adapter

        // Sincronizar dirección activa desde el perfil del usuario
        AuthRepository.liveProfile.observe(viewLifecycleOwner) { profile ->
            if (profile != null) {
                if (selectedAddress.isBlank()) {
                    selectedAddress = profile.address
                    selectedReference = profile.reference
                    selectedLat = profile.latitude
                    selectedLng = profile.longitude
                }
            }
            updateDeliveryText(tvDeliveryAddress)
        }

        // Selector interactivo de Mapa OpenStreetMap
        layoutDeliverySelector?.setOnClickListener {
            val prof = AuthRepository.currentProfile
            LocationPickerBottomSheet.newInstance(
                currentAddress = selectedAddress.ifBlank { prof?.address.orEmpty() },
                currentReference = selectedReference.ifBlank { prof?.reference.orEmpty() },
                lat = if (selectedLat != 0.0) selectedLat else (prof?.latitude ?: 0.0),
                lng = if (selectedLng != 0.0) selectedLng else (prof?.longitude ?: 0.0)
            ) { address, reference, lat, lng ->
                selectedAddress = address
                selectedReference = reference
                selectedLat = lat
                selectedLng = lng
                updateDeliveryText(tvDeliveryAddress)
            }.show(parentFragmentManager, LocationPickerBottomSheet.TAG)
        }

        CartRepository.items.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)

            if (items.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                summaryCard.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                summaryCard.visibility = View.VISIBLE
            }

            val totalSavings = CartRepository.getTotalSavings()
            if (totalSavings > 0.0) {
                tvSavings.visibility = View.VISIBLE
                tvSavings.text = "🎉 ¡Estás ahorrando S/ ${String.format(java.util.Locale.US, "%.2f", totalSavings)} con ofertas!"
            } else {
                tvSavings.visibility = View.GONE
            }

            tvTotal.text = "S/ ${String.format(java.util.Locale.US, "%.2f", CartRepository.getTotal())}"
        }

        btnSend.setOnClickListener {
            val items = CartRepository.items.value ?: emptyList()
            if (items.isEmpty()) return@setOnClickListener

            val profile = AuthRepository.currentProfile
            val finalAddress = selectedAddress.ifBlank { profile?.address.orEmpty() }
            val finalPhone = profile?.phone.orEmpty()
            val finalName = profile?.name.orEmpty()

            // Si falta teléfono, nombre o dirección, abrir el bottom sheet elegante para completar perfil
            if (finalPhone.isBlank() || finalName.isBlank() || finalAddress.isBlank()) {
                CompleteProfileBottomSheet.newInstance(
                    name = finalName,
                    phone = finalPhone,
                    address = finalAddress,
                    reference = selectedReference.ifBlank { profile?.reference.orEmpty() },
                    lat = if (selectedLat != 0.0) selectedLat else (profile?.latitude ?: 0.0),
                    lng = if (selectedLng != 0.0) selectedLng else (profile?.longitude ?: 0.0)
                ) { _, _, updatedAddress, updatedRef, lat, lng ->
                    selectedAddress = updatedAddress
                    selectedReference = updatedRef
                    selectedLat = lat
                    selectedLng = lng
                    updateDeliveryText(tvDeliveryAddress)

                    // Continuar de inmediato con la confirmación de la orden
                    openOrderConfirmationSheet(
                        address = updatedAddress,
                        reference = updatedRef,
                        lat = lat,
                        lng = lng,
                        tvDeliveryAddress = tvDeliveryAddress
                    )
                }.show(parentFragmentManager, CompleteProfileBottomSheet.TAG)
                return@setOnClickListener
            }

            openOrderConfirmationSheet(
                address = finalAddress,
                reference = selectedReference.ifBlank { profile?.reference.orEmpty() },
                lat = if (selectedLat != 0.0) selectedLat else (profile?.latitude ?: 0.0),
                lng = if (selectedLng != 0.0) selectedLng else (profile?.longitude ?: 0.0),
                tvDeliveryAddress = tvDeliveryAddress
            )
        }
    }

    private fun openOrderConfirmationSheet(
        address: String,
        reference: String,
        lat: Double,
        lng: Double,
        tvDeliveryAddress: TextView?
    ) {
        OrderConfirmationBottomSheet.newInstance(
            address = address,
            reference = reference,
            lat = lat,
            lng = lng,
            onAddressUpdated = { newAddress, newRef, newLat, newLng ->
                selectedAddress = newAddress
                selectedReference = newRef
                selectedLat = newLat
                selectedLng = newLng
                updateDeliveryText(tvDeliveryAddress)
            },
            onOrderPlaced = { orderId, total, finalAddress ->
                OrderSuccessDialogFragment.newInstance(
                    orderId = orderId,
                    total = total,
                    address = finalAddress
                ).show(parentFragmentManager, OrderSuccessDialogFragment.TAG)
            }
        ).show(parentFragmentManager, OrderConfirmationBottomSheet.TAG)
    }

    private fun updateDeliveryText(tvDeliveryAddress: TextView?) {
        if (tvDeliveryAddress == null) return
        val text = when {
            selectedAddress.isNotBlank() && selectedReference.isNotBlank() -> "📍 $selectedAddress ($selectedReference)"
            selectedAddress.isNotBlank() -> "📍 $selectedAddress"
            else -> "📍 Toca para fijar ubicación en el mapa 🛵"
        }
        tvDeliveryAddress.text = text
    }
}
