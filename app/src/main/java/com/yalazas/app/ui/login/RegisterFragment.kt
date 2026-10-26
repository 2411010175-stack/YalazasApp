package com.yalazas.app.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.yalazas.app.R
import com.yalazas.app.modelovista.EstadoAutenticacion
import com.yalazas.app.modelovista.ModeloVistaAutenticacion

/** Recoge los datos necesarios para crear una cuenta. */
class RegisterFragment : Fragment(R.layout.fragment_register) {

    private val modeloVista: ModeloVistaAutenticacion by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputName = view.findViewById<TextInputEditText>(R.id.inputName)
        val inputEmail = view.findViewById<TextInputEditText>(R.id.inputEmail)
        val inputPass = view.findViewById<TextInputEditText>(R.id.inputPass)
        val btnRegister = view.findViewById<MaterialButton>(R.id.btnRegister)
        val btnLoginFlow = view.findViewById<View>(R.id.btnLoginFlow)
        val progressBar = view.findViewById<View>(R.id.progressBar)
        val registerCard = view.findViewById<View>(R.id.registerCard)

        // Animación de entrada
        registerCard.alpha = 0f
        registerCard.translationY = 50f
        registerCard.animate().alpha(1f).translationY(0f).setDuration(600).start()

        btnRegister.setOnClickListener {
            val name = inputName.text.toString().trim()
            val email = inputEmail.text.toString().trim()
            val pass = inputPass.text.toString().trim()

            // Validación local para mostrar errores en TextInputLayout
            var hasError = false

            val layoutName = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.inputNameLayout)
            val layoutEmail = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.inputEmailLayout)
            val layoutPass = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.inputPassLayout)

            if (name.isEmpty()) {
                layoutName.error = "Ingresa tu nombre"
                hasError = true
            } else {
                layoutName.error = null
            }

            if (email.isEmpty()) {
                layoutEmail.error = "Ingresa un correo"
                hasError = true
            } else {
                layoutEmail.error = null
            }

            if (pass.isEmpty()) {
                layoutPass.error = "Ingresa una contraseña"
                hasError = true
            } else if (pass.length < 6) {
                layoutPass.error = "Mínimo 6 caracteres"
                hasError = true
            } else {
                layoutPass.error = null
            }

            if (!hasError) {
                modeloVista.registrarCuenta(email, pass, name)
            }
        }

        btnLoginFlow.setOnClickListener {
            findNavController().popBackStack()
        }

        modeloVista.estadoAutenticacion.observe(viewLifecycleOwner) { state ->
            when (state) {
                is EstadoAutenticacion.Cargando -> {
                    progressBar.visibility = View.VISIBLE
                    btnRegister.isEnabled = false
                }
                is EstadoAutenticacion.Exito -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Cuenta creada exitosamente", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_register_to_menu)
                }
                is EstadoAutenticacion.Error -> {
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
}
