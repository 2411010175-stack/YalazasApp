package com.yalazas.app.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yalazas.app.R
import com.yalazas.app.repository.AuthRepository
import com.yalazas.app.ui.dialogs.LogoutConfirmationDialogFragment
import com.yalazas.app.ui.location.LocationPickerBottomSheet
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputName = view.findViewById<TextInputEditText>(R.id.inputProfileName)
        val inputPhone = view.findViewById<TextInputEditText>(R.id.inputProfilePhone)
        val inputAddressLayout = view.findViewById<TextInputLayout>(R.id.inputAddressLayout)
        val inputAddress = view.findViewById<TextInputEditText>(R.id.inputProfileAddress)
        val inputRef = view.findViewById<TextInputEditText>(R.id.inputProfileRef)
        val btnPickLocation = view.findViewById<MaterialButton>(R.id.btnPickLocationMap)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveProfile)
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnLogout)

        // Cargar datos actuales
        val currentProfile = AuthRepository.currentProfile
        if (currentProfile != null) {
            inputName.setText(currentProfile.name)
            inputPhone.setText(currentProfile.phone)
            inputAddress.setText(currentProfile.address)
            inputRef.setText(currentProfile.reference)
            selectedLat = currentProfile.latitude
            selectedLng = currentProfile.longitude
        }

        fun openLocationPicker() {
            LocationPickerBottomSheet.newInstance(
                currentAddress = inputAddress.text.toString().trim(),
                currentReference = inputRef.text.toString().trim(),
                lat = if (selectedLat != 0.0) selectedLat else (currentProfile?.latitude ?: 0.0),
                lng = if (selectedLng != 0.0) selectedLng else (currentProfile?.longitude ?: 0.0)
            ) { address, reference, lat, lng ->
                inputAddress.setText(address)
                if (reference.isNotBlank()) {
                    inputRef.setText(reference)
                }
                selectedLat = lat
                selectedLng = lng
                Toast.makeText(requireContext(), "📍 Ubicación fijada en el mapa", Toast.LENGTH_SHORT).show()
            }.show(parentFragmentManager, LocationPickerBottomSheet.TAG)
        }

        btnPickLocation.setOnClickListener { openLocationPicker() }
        inputAddressLayout.setEndIconOnClickListener { openLocationPicker() }

        btnSave.setOnClickListener {
            val name = inputName.text.toString().trim()
            val phone = inputPhone.text.toString().trim()
            val address = inputAddress.text.toString().trim()
            val reference = inputRef.text.toString().trim()

            if (name.isBlank() || phone.isBlank() || address.isBlank()) {
                Toast.makeText(requireContext(), "Nombre, teléfono y dirección son requeridos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            lifecycleScope.launch {
                val result = AuthRepository.updateProfile(
                    name = name,
                    phone = phone,
                    address = address,
                    reference = reference,
                    latitude = selectedLat,
                    longitude = selectedLng
                )
                btnSave.isEnabled = true
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error al guardar perfil", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnLogout.setOnClickListener {
            LogoutConfirmationDialogFragment.newInstance("¿Estás seguro de que deseas cerrar sesión de tu cuenta?")
                .setOnConfirmListener {
                    AuthRepository.logout()
                    Toast.makeText(requireContext(), "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(
                        R.id.nav_login,
                        null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                }
                .show(parentFragmentManager, LogoutConfirmationDialogFragment.TAG)
        }
    }
}
