package com.yalazas.app.ui.cart

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yalazas.app.R
import com.yalazas.app.repository.AuthRepository
import com.yalazas.app.ui.location.LocationPickerBottomSheet
import kotlinx.coroutines.launch

class CompleteProfileBottomSheet : BottomSheetDialogFragment() {

    private var initialName: String = ""
    private var initialPhone: String = ""
    private var initialAddress: String = ""
    private var initialReference: String = ""
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0

    private var onProfileCompleted: ((name: String, phone: String, address: String, reference: String, lat: Double, lng: Double) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialName = arguments?.getString(ARG_NAME).orEmpty()
        initialPhone = arguments?.getString(ARG_PHONE).orEmpty()
        initialAddress = arguments?.getString(ARG_ADDRESS).orEmpty()
        initialReference = arguments?.getString(ARG_REFERENCE).orEmpty()
        currentLat = arguments?.getDouble(ARG_LAT) ?: 0.0
        currentLng = arguments?.getDouble(ARG_LNG) ?: 0.0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_complete_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnClose = view.findViewById<AppCompatImageButton>(R.id.btnCloseProfileSheet)
        val layoutName = view.findViewById<TextInputLayout>(R.id.layoutSheetProfileName)
        val etName = view.findViewById<TextInputEditText>(R.id.etSheetProfileName)
        val layoutPhone = view.findViewById<TextInputLayout>(R.id.layoutSheetProfilePhone)
        val etPhone = view.findViewById<TextInputEditText>(R.id.etSheetProfilePhone)
        val layoutAddress = view.findViewById<TextInputLayout>(R.id.layoutSheetProfileAddress)
        val etAddress = view.findViewById<TextInputEditText>(R.id.etSheetProfileAddress)
        val etReference = view.findViewById<TextInputEditText>(R.id.etSheetProfileReference)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveProfileAndContinue)
        val pbLoading = view.findViewById<ProgressBar>(R.id.pbSavingProfile)

        // Close button
        btnClose.setOnClickListener {
            dismiss()
        }

        // Prefill inputs
        if (initialName.isNotBlank()) etName.setText(initialName)
        if (initialPhone.isNotBlank()) etPhone.setText(initialPhone.replace("+51", "").trim())
        if (initialAddress.isNotBlank()) etAddress.setText(initialAddress)
        if (initialReference.isNotBlank()) etReference.setText(initialReference)

        // Map picker click from address input icon
        layoutAddress.setEndIconOnClickListener {
            LocationPickerBottomSheet.newInstance(
                currentAddress = etAddress.text.toString().trim().ifBlank { initialAddress },
                currentReference = etReference.text.toString().trim().ifBlank { initialReference },
                lat = currentLat,
                lng = currentLng
            ) { selectedAddress, selectedRef, lat, lng ->
                etAddress.setText(selectedAddress)
                if (selectedRef.isNotBlank()) {
                    etReference.setText(selectedRef)
                }
                currentLat = lat
                currentLng = lng
                layoutAddress.error = null
            }.show(parentFragmentManager, LocationPickerBottomSheet.TAG)
        }

        // Save & Continue
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val rawPhone = etPhone.text.toString().trim().replace(" ", "").replace("-", "")
            val address = etAddress.text.toString().trim()
            val reference = etReference.text.toString().trim()

            var isValid = true

            if (name.isBlank()) {
                layoutName.error = "Por favor ingresa tu nombre"
                isValid = false
            } else {
                layoutName.error = null
            }

            if (rawPhone.length < 9) {
                layoutPhone.error = "Ingresa un número de celular válido (9 dígitos)"
                isValid = false
            } else {
                layoutPhone.error = null
            }

            if (address.isBlank()) {
                layoutAddress.error = "Ingresa o selecciona tu dirección en el mapa"
                isValid = false
            } else {
                layoutAddress.error = null
            }

            if (!isValid) return@setOnClickListener

            // Button animation
            val scaleX = ObjectAnimator.ofFloat(btnSave, View.SCALE_X, 1f, 0.95f, 1f)
            val scaleY = ObjectAnimator.ofFloat(btnSave, View.SCALE_Y, 1f, 0.95f, 1f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 200
                start()
            }

            btnSave.isEnabled = false
            btnSave.text = "Guardando datos..."
            pbLoading.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Si el usuario no ha iniciado sesión anónima ni regular, loguear como invitado
                    if (AuthRepository.currentUser == null) {
                        AuthRepository.loginAsGuest()
                    }

                    val formattedPhone = if (rawPhone.startsWith("+51")) rawPhone else "+51$rawPhone"

                    val result = AuthRepository.updateProfile(
                        name = name,
                        phone = formattedPhone,
                        address = address,
                        reference = reference,
                        latitude = currentLat,
                        longitude = currentLng
                    )

                    if (result.isSuccess) {
                        pbLoading.visibility = View.GONE
                        dismissAllowingStateLoss()
                        onProfileCompleted?.invoke(name, formattedPhone, address, reference, currentLat, currentLng)
                    } else {
                        btnSave.isEnabled = true
                        btnSave.text = "Guardar y Continuar Pedido 🚀"
                        pbLoading.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            result.exceptionOrNull()?.localizedMessage ?: "Error al guardar tus datos",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    btnSave.isEnabled = true
                    btnSave.text = "Guardar y Continuar Pedido 🚀"
                    pbLoading.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            bottomSheet.requestLayout()
        }
    }

    companion object {
        const val TAG = "CompleteProfileBottomSheet"
        private const val ARG_NAME = "arg_name"
        private const val ARG_PHONE = "arg_phone"
        private const val ARG_ADDRESS = "arg_address"
        private const val ARG_REFERENCE = "arg_reference"
        private const val ARG_LAT = "arg_lat"
        private const val ARG_LNG = "arg_lng"

        fun newInstance(
            name: String,
            phone: String,
            address: String,
            reference: String,
            lat: Double,
            lng: Double,
            onProfileCompleted: (name: String, phone: String, address: String, reference: String, lat: Double, lng: Double) -> Unit
        ): CompleteProfileBottomSheet {
            return CompleteProfileBottomSheet().apply {
                this.onProfileCompleted = onProfileCompleted
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)
                    putString(ARG_PHONE, phone)
                    putString(ARG_ADDRESS, address)
                    putString(ARG_REFERENCE, reference)
                    putDouble(ARG_LAT, lat)
                    putDouble(ARG_LNG, lng)
                }
            }
        }
    }
}
