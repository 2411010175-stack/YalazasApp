package com.yalazas.app.ui.delivery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.yalazas.app.ui.dialogos.DialogoCerrarSesion
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentDeliveryProfileBinding
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.ServiciosFirebase

/** Muestra los datos y la disponibilidad del repartidor. */
class DeliveryProfileFragment : Fragment(R.layout.fragment_delivery_profile) {

    private var _binding: FragmentDeliveryProfileBinding? = null
    private val binding get() = _binding!!
    private var escuchaEntregas: ListenerRegistration? = null
    private var escuchaVehiculo: ListenerRegistration? = null
    private var hayEntregaActiva = false
    private var actualizandoSwitch = false
    private var verificandoDescanso = false

    private val prefs by lazy {
        requireContext().getSharedPreferences("delivery_prefs", Context.MODE_PRIVATE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryProfileBinding.bind(view)
        hayEntregaActiva = false
        verificandoDescanso = false
        actualizandoSwitch = false

        loadProfileData()
        escucharVehiculo()
        loadDeliveredCountStat()
        configurarEventos()
        escucharEntregasActivas()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            val disponible = prefs.getBoolean("driver_available", true)
            fijarSwitch(disponible || hayEntregaActiva)
            updateAvailabilityUI(disponible)
        }
    }

    private fun loadProfileData() {
        val profile = RepositorioAutenticacion.currentProfile
        val user = ServiciosFirebase.auth.currentUser

        val name = profile?.name?.ifBlank { "Repartidor Yalazas" } ?: (user?.displayName ?: "Repartidor Yalazas")
        val email = profile?.email?.ifBlank { user?.email ?: "repartidor@yalazas.pe" } ?: (user?.email ?: "repartidor@yalazas.pe")
        val phone = profile?.phone?.ifBlank { "No registrado" } ?: "No registrado"

        binding.tvDriverProfileName.text = name
        binding.tvDriverProfileEmail.text = email
        binding.tvDriverProfilePhone.text = "📞 $phone"

        // Estado de disponibilidad
        val isAvailable = prefs.getBoolean("driver_available", true)
        binding.switchDriverAvailability.isChecked = isAvailable
        binding.switchDriverAvailability.isEnabled = false
        updateAvailabilityUI(isAvailable)
    }

    private fun updateAvailabilityUI(isAvailable: Boolean) {
        if (hayEntregaActiva) {
            binding.tvDriverStatusTitle.text = "Entrega en curso"
            binding.tvDriverStatusTitle.setTextColor(0xFF00E676.toInt())
            binding.tvDriverStatusSubtitle.text = "Termina tus entregas antes de pasar a descanso"
        } else if (isAvailable) {
            binding.tvDriverStatusTitle.text = "Disponible para Entregas"
            binding.tvDriverStatusTitle.setTextColor(0xFF00E676.toInt())
            binding.tvDriverStatusSubtitle.text = "Recibiendo asignaciones y pedidos en tiempo real"
        } else {
            binding.tvDriverStatusTitle.text = "En Descanso / Fuera de Turno"
            binding.tvDriverStatusTitle.setTextColor(0xFFFFAB40.toInt())
            binding.tvDriverStatusSubtitle.text = "No recibirás nuevos pedidos por ahora"
        }
    }

    /** El vehículo se consulta del perfil común, que solo administración puede modificar. */
    private fun escucharVehiculo() {
        val uid = ServiciosFirebase.auth.currentUser?.uid ?: return
        escuchaVehiculo?.remove()
        escuchaVehiculo = ServiciosFirebase.firestore.collection("users").document(uid)
            .addSnapshotListener { documento, error ->
                if (_binding == null) return@addSnapshotListener
                if (error != null) {
                    binding.tvVehicleType.text = "Sin conexión"
                    binding.tvVehiclePlate.text = "—"
                    binding.tvVehicleModel.text = "—"
                    return@addSnapshotListener
                }
                binding.tvVehicleType.text = documento?.getString("vehicleType")?.ifBlank { "Por registrar" } ?: "Por registrar"
                binding.tvVehiclePlate.text = documento?.getString("vehiclePlate")?.ifBlank { "Por registrar" } ?: "Por registrar"
                binding.tvVehicleModel.text = documento?.getString("vehicleModel")?.ifBlank { "—" } ?: "—"
            }
    }

    /** Bloquea descanso mientras exista alguna entrega EN_CAMINO asignada a esta cuenta. */
    private fun escucharEntregasActivas() {
        val uid = ServiciosFirebase.auth.currentUser?.uid ?: return
        escuchaEntregas?.remove()
        escuchaEntregas = ServiciosFirebase.firestore.collection("orders")
            .whereEqualTo("isDelivery", true)
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener
                if (error != null) {
                    binding.switchDriverAvailability.isEnabled = false
                    binding.tvDriverStatusSubtitle.text = "No se pudo verificar tus entregas. Revisa la conexión."
                    return@addSnapshotListener
                }
                hayEntregaActiva = snapshot?.documents?.any {
                    it.getString("status") == OrderStatus.EN_CAMINO.name &&
                        it.getString("deliveryDriverId") == uid
                } == true
                if (hayEntregaActiva) {
                    prefs.edit().putBoolean("driver_available", true).apply()
                    fijarSwitch(true)
                }
                binding.switchDriverAvailability.isEnabled = !hayEntregaActiva && !verificandoDescanso
                updateAvailabilityUI(prefs.getBoolean("driver_available", true))
            }
    }

    private fun fijarSwitch(disponible: Boolean) {
        actualizandoSwitch = true
        binding.switchDriverAvailability.isChecked = disponible
        actualizandoSwitch = false
    }

    private fun loadDeliveredCountStat() {
        val currentUid = ServiciosFirebase.auth.currentUser?.uid ?: ""
        val db = ServiciosFirebase.firestore

        db.collection("orders")
            .whereEqualTo("isDelivery", true)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener
                val count = snapshot.documents.count { doc ->
                    val driverId = doc.getString("deliveryDriverId")
                    doc.getString("status") == OrderStatus.ENTREGADO.name &&
                        (driverId == currentUid || driverId.isNullOrEmpty())
                }
                binding.tvProfileStatDeliveries.text = count.toString()
            }
    }

    private fun configurarEventos() {
        binding.switchDriverAvailability.setOnCheckedChangeListener { _, isChecked ->
            if (actualizandoSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                prefs.edit().putBoolean("driver_available", true).apply()
                updateAvailabilityUI(true)
                Toast.makeText(requireContext(), "Disponible para recibir pedidos", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            if (hayEntregaActiva) {
                fijarSwitch(true)
                Toast.makeText(requireContext(), "Termina tus entregas antes de descansar", Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            verificandoDescanso = true
            binding.switchDriverAvailability.isEnabled = false
            val vistaOrigen = _binding
            ServiciosFirebase.firestore.collection("orders").whereEqualTo("isDelivery", true)
                .get(Source.SERVER)
                .addOnSuccessListener { snapshot ->
                    if (_binding !== vistaOrigen) return@addOnSuccessListener
                    val uid = ServiciosFirebase.auth.currentUser?.uid
                    val enRuta = snapshot.documents.any {
                        it.getString("status") == OrderStatus.EN_CAMINO.name &&
                            it.getString("deliveryDriverId") == uid
                    }
                    verificandoDescanso = false
                    if (enRuta || hayEntregaActiva) {
                        hayEntregaActiva = true
                        prefs.edit().putBoolean("driver_available", true).apply()
                        fijarSwitch(true)
                        Toast.makeText(requireContext(), "Tienes entregas en curso; no puedes descansar", Toast.LENGTH_LONG).show()
                    } else {
                        prefs.edit().putBoolean("driver_available", false).apply()
                        Toast.makeText(requireContext(), "Ahora estás en descanso", Toast.LENGTH_SHORT).show()
                    }
                    binding.switchDriverAvailability.isEnabled = !hayEntregaActiva
                    updateAvailabilityUI(prefs.getBoolean("driver_available", true))
                }
                .addOnFailureListener {
                    if (_binding !== vistaOrigen) return@addOnFailureListener
                    verificandoDescanso = false
                    prefs.edit().putBoolean("driver_available", true).apply()
                    fijarSwitch(true)
                    binding.switchDriverAvailability.isEnabled = !hayEntregaActiva
                    updateAvailabilityUI(true)
                    Toast.makeText(requireContext(), "No se pudo verificar tus entregas", Toast.LENGTH_LONG).show()
                }
        }

        binding.btnCallCentral.setOnClickListener {
            val dialIntent = Intent(Intent.ACTION_DIAL, "tel:921456789".toUri())
            startActivity(dialIntent)
        }

        binding.btnWhatsAppCentral.setOnClickListener {
            val driverName = RepositorioAutenticacion.currentProfile?.name ?: "Repartidor"
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

    private fun confirmLogout() {
        DialogoCerrarSesion.crear("¿Deseas cerrar tu turno y salir de tu cuenta de repartidor?")
            .definirAccionConfirmacion {
                requireContext().stopService(Intent(requireContext(), ServicioUbicacionRepartidor::class.java))
                RepositorioAutenticacion.cerrarSesion()
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
            .show(parentFragmentManager, DialogoCerrarSesion.TAG)
    }

    override fun onDestroyView() {
        escuchaEntregas?.remove()
        escuchaEntregas = null
        escuchaVehiculo?.remove()
        escuchaVehiculo = null
        super.onDestroyView()
        _binding = null
    }
}
