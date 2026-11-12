package com.yalazas.app.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.material.textfield.TextInputEditText
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.yalazas.app.R
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioTarifasEntrega
import com.yalazas.app.ui.dialogos.DialogoCerrarSesion
import com.yalazas.app.utilidades.ConfiguracionTarifaEntrega
import java.math.BigDecimal
import java.util.Locale

/** Presenta los ajustes disponibles para administración. */
class AdminSettingsFragment : Fragment(R.layout.fragment_admin_settings) {
    private var escuchaTarifa: ListenerRegistration? = null
    private var tarifaActual = ConfiguracionTarifaEntrega()
    private var tarifaConfirmada = false
    private var guardandoTarifa = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Botón Volver
        view.findViewById<ImageButton>(R.id.btnBackSettings).setOnClickListener {
            findNavController().popBackStack()
        }

        // Email del Administrador
        val adminEmail = RepositorioAutenticacion.currentUser?.email ?: RepositorioAutenticacion.currentProfile?.email ?: "admin@yalaza.com"
        view.findViewById<TextView>(R.id.tvAdminEmail).text = adminEmail

        view.findViewById<View>(R.id.btnGestionarMesas).setOnClickListener {
            findNavController().navigate(R.id.nav_admin_mesas)
        }

        configurarTarifa(view)

        // Cerrar sesión
        view.findViewById<MaterialButton>(R.id.btnAdminLogout).setOnClickListener {
            DialogoCerrarSesion.crear("¿Deseas salir del panel de administración?")
                .definirAccionConfirmacion {
                    RepositorioAutenticacion.cerrarSesion()
                    Toast.makeText(requireContext(), "Sesión de administrador cerrada", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(
                        R.id.nav_login,
                        null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                }
                .show(parentFragmentManager, DialogoCerrarSesion.TAG)
        }
    }

    private fun configurarTarifa(view: View) {
        val version = view.findViewById<TextView>(R.id.tvVersionTarifaAdmin)
        val base = view.findViewById<TextInputEditText>(R.id.etTarifaBaseAdmin)
        val distancia = view.findViewById<TextInputEditText>(R.id.etDistanciaIncluidaAdmin)
        val extra = view.findViewById<TextInputEditText>(R.id.etKmExtraAdmin)
        val guardar = view.findViewById<MaterialButton>(R.id.btnGuardarTarifaAdmin)
        var versionMostrada = -1

        escuchaTarifa?.remove()
        escuchaTarifa = RepositorioTarifasEntrega.escuchar(
            alCambiar = { nueva, confirmada ->
                if (this.view == null) return@escuchar
                tarifaActual = nueva
                tarifaConfirmada = confirmada
                if (versionMostrada != nueva.version) {
                    base.setText(String.format(Locale.US, "%.2f", nueva.baseCents / 100.0))
                    distancia.setText(String.format(Locale.US, "%.3f", nueva.includedMeters / 1000.0))
                    extra.setText(String.format(Locale.US, "%.2f", nueva.extraKmCents / 100.0))
                    versionMostrada = nueva.version
                }
                version.text = if (confirmada) "Versión ${nueva.version} · vigente"
                    else "Versión ${nueva.version} · verificando conexión"
                guardar.isEnabled = confirmada && !guardandoTarifa
            },
            alError = { error ->
                if (this.view == null) return@escuchar
                tarifaConfirmada = false
                guardar.isEnabled = false
                version.text = "No se pudo consultar la tarifa"
                Toast.makeText(requireContext(), error.localizedMessage ?: "Revisa tu conexión.", Toast.LENGTH_LONG).show()
            }
        )

        guardar.setOnClickListener {
            if (!tarifaConfirmada) return@setOnClickListener
            val nueva = ConfiguracionTarifaEntrega(
                version = tarifaActual.version,
                baseCents = convertir(base.text?.toString(), 100) ?: -1,
                includedMeters = convertir(distancia.text?.toString(), 1000) ?: -1,
                extraKmCents = convertir(extra.text?.toString(), 100) ?: -1
            )
            if (!nueva.valida()) {
                Toast.makeText(requireContext(), "Revisa importes (S/ 0–100) y distancia (0,001–50 km).", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            guardandoTarifa = true
            guardar.isEnabled = false
            RepositorioTarifasEntrega.guardar(nueva) { resultado ->
                if (this.view == null) return@guardar
                guardandoTarifa = false
                guardar.isEnabled = tarifaConfirmada
                resultado.onSuccess {
                    Toast.makeText(requireContext(), "Tarifa v${it.version} publicada.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(requireContext(), it.localizedMessage ?: "No se pudo guardar la tarifa.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun convertir(valor: String?, factor: Int): Int? = try {
        BigDecimal(valor?.trim()?.replace(',', '.') ?: "").multiply(BigDecimal(factor)).intValueExact()
    } catch (_: Exception) { null }

    override fun onDestroyView() {
        escuchaTarifa?.remove()
        escuchaTarifa = null
        super.onDestroyView()
    }
}
