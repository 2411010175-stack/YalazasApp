package com.yalazas.app.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.yalazas.app.R
import com.yalazas.app.repository.AuthRepository
import com.yalazas.app.ui.dialogs.LogoutConfirmationDialogFragment

class AdminSettingsFragment : Fragment(R.layout.fragment_admin_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Botón Volver
        view.findViewById<ImageButton>(R.id.btnBackSettings).setOnClickListener {
            findNavController().popBackStack()
        }

        // Email del Administrador
        val adminEmail = AuthRepository.currentUser?.email ?: AuthRepository.currentProfile?.email ?: "admin@yalaza.com"
        view.findViewById<TextView>(R.id.tvAdminEmail).text = adminEmail

        // Cerrar sesión
        view.findViewById<MaterialButton>(R.id.btnAdminLogout).setOnClickListener {
            LogoutConfirmationDialogFragment.newInstance("¿Deseas salir del panel de administración?")
                .setOnConfirmListener {
                    AuthRepository.logout()
                    Toast.makeText(requireContext(), "Sesión de administrador cerrada", Toast.LENGTH_SHORT).show()
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
