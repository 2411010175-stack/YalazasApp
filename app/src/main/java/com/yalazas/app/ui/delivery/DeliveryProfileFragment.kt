package com.yalazas.app.ui.delivery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.yalazas.app.ui.dialogs.LogoutConfirmationDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.yalazas.app.R
import com.yalazas.app.databinding.DialogEditVehicleBinding
import com.yalazas.app.databinding.FragmentDeliveryProfileBinding
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repository.AuthRepository

class DeliveryProfileFragment : Fragment(R.layout.fragment_delivery_profile) {

    private var _binding: FragmentDeliveryProfileBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().getSharedPreferences("delivery_prefs", Context.MODE_PRIVATE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryProfileBinding.bind(view)

        loadProfileData()
        loadVehicleData()
        loadDeliveredCountStat()
        setupListeners()
    }

    private fun loadProfileData() {
        val profile = AuthRepository.currentProfile
        val user = FirebaseAuth.getInstance().currentUser

        val name = profile?.name?.ifBlank { "Repartidor Yalazas" } ?: (user?.displayName ?: "Repartidor Yalazas")
        val email = profile?.email?.ifBlank { user?.email ?: "repartidor@yalazas.pe" } ?: (user?.email ?: "repartidor@yalazas.pe")
        val phone = if (!profile?.phone.isNullOrBlank()) profile?.phone!! else "921 456 789"

        binding.tvDriverProfileName.text = name
        binding.tvDriverProfileEmail.text = email
        binding.tvDriverProfilePhone.text = "📞 $phone"

        // Estado de disponibilidad
        val isAvailable = prefs.getBoolean("driver_available", true)
        binding.switchDriverAvailability.isChecked = isAvailable
        updateAvailabilityUI(isAvailable)
    }

    private fun updateAvailabilityUI(isAvailable: Boolean) {
        if (isAvailable) {
            binding.tvDriverStatusTitle.text = "Disponible para Entregas"
            binding.tvDriverStatusTitle.setTextColor(0xFF00E676.toInt())
            binding.tvDriverStatusSubtitle.text = "Recibiendo asignaciones y pedidos en tiempo real"
        } else {
            binding.tvDriverStatusTitle.text = "En Descanso / Fuera de Turno"
            binding.tvDriverStatusTitle.setTextColor(0xFFFFAB40.toInt())
            binding.tvDriverStatusSubtitle.text = "No recibirás nuevos pedidos por ahora"
        }
    }

    private fun loadVehicleData() {
        val vType = prefs.getString("vehicle_type", "Moto Lineal") ?: "Moto Lineal"
        val vPlate = prefs.getString("vehicle_plate", "7842-1F") ?: "7842-1F"
        val vModel = prefs.getString("vehicle_model", "Honda GL 150") ?: "Honda GL 150"

        binding.tvVehicleType.text = vType
        binding.tvVehiclePlate.text = vPlate
        binding.tvVehicleModel.text = vModel
    }

    private fun loadDeliveredCountStat() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val db = FirebaseFirestore.getInstance()

        db.collection("orders")
            .whereEqualTo("status", OrderStatus.ENTREGADO.name)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                val count = snapshot.documents.count { doc ->
                    val driverId = doc.getString("deliveryDriverId")
                    driverId == currentUid || driverId.isNullOrEmpty()
                }
                binding.tvProfileStatDeliveries.text = count.toString()
            }
    }

    private fun setupListeners() {
        binding.switchDriverAvailability.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("driver_available", isChecked).apply()
            updateAvailabilityUI(isChecked)
            val msg = if (isChecked) "🟢 Ahora estás disponible para recibir pedidos" else "⏸️ Turno pausado. Que descanses."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnEditVehicle.setOnClickListener {
            showEditVehicleDialog()
        }

        binding.btnCallCentral.setOnClickListener {
            val dialIntent = Intent(Intent.ACTION_DIAL, "tel:921456789".toUri())
            startActivity(dialIntent)
        }

        binding.btnWhatsAppCentral.setOnClickListener {
            val driverName = AuthRepository.currentProfile?.name ?: "Repartidor"
            val message = "¡Hola Administración Yalazas! Soy el repartidor $driverName. Me comunico desde la app para:"
            val url = "https://wa.me/51921456789?text=${Uri.encode(message)}"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDriverLogout.setOnClickListener {
            confirmLogout()
        }
    }

    private fun showEditVehicleDialog() {
        val dialogBinding = DialogEditVehicleBinding.inflate(LayoutInflater.from(requireContext()))

        val currentType = binding.tvVehicleType.text.toString()
        val currentPlate = binding.tvVehiclePlate.text.toString()
        val currentModel = binding.tvVehicleModel.text.toString()

        dialogBinding.etVehicleType.setText(currentType)
        dialogBinding.etVehiclePlate.setText(currentPlate)
        dialogBinding.etVehicleModel.setText(currentModel)

        val dialog = AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelVehicle.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveVehicle.setOnClickListener {
            val newType = dialogBinding.etVehicleType.text?.toString()?.trim() ?: ""
            val newPlate = dialogBinding.etVehiclePlate.text?.toString()?.trim() ?: ""
            val newModel = dialogBinding.etVehicleModel.text?.toString()?.trim() ?: ""

            if (newType.isEmpty() || newPlate.isEmpty()) {
                Toast.makeText(requireContext(), "Por favor completa el tipo y la placa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit()
                .putString("vehicle_type", newType)
                .putString("vehicle_plate", newPlate)
                .putString("vehicle_model", newModel.ifBlank { "-" })
                .apply()

            loadVehicleData()
            dialog.dismiss()
            Toast.makeText(requireContext(), "✅ Datos del vehículo actualizados", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun confirmLogout() {
        LogoutConfirmationDialogFragment.newInstance("¿Deseas cerrar tu turno y salir de tu cuenta de repartidor?")
            .setOnConfirmListener {
                AuthRepository.logout()
                Toast.makeText(requireContext(), "Sesión de repartidor cerrada", Toast.LENGTH_SHORT).show()
                try {
                    findNavController().navigate(
                        R.id.nav_login,
                        null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                } catch (_: Exception) {
                    try {
                        findNavController().navigate(R.id.nav_home)
                    } catch (_: Exception) { }
                }
            }
            .show(parentFragmentManager, LogoutConfirmationDialogFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
