package com.yalazas.app.ui.login

import android.animation.ObjectAnimator
import android.content.DialogInterface
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentLoginBinding
import com.yalazas.app.modelovista.EstadoAutenticacion
import com.yalazas.app.modelovista.ModeloVistaAutenticacion
import com.yalazas.app.modelovista.TipoErrorIngreso

/** Recoge las credenciales y observa el resultado del inicio de sesión. */
class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val modeloVista: ModeloVistaAutenticacion by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        setupAnimations()
        observarEstados()
        configurarEventos()

        // Verificar si ya hay sesión iniciada (para empleados)
        modeloVista.verificarSesion()
    }

    private fun setupAnimations() {
        // Logo floating animation
        ObjectAnimator.ofFloat(binding.logoImage, "translationY", -20f, 20f).apply {
            duration = 2000
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Fade in and slide up for card
        binding.loginCard.alpha = 0f
        binding.loginCard.translationY = 50f
        binding.loginCard.animate().alpha(1f).translationY(0f).setDuration(600).start()
    }

    private fun configurarEventos() {
        binding.btnLogin.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val pass = binding.inputPass.text.toString().trim()

            // Validación local para mostrar errores en TextInputLayout
            var hasError = false
            if (email.isEmpty()) {
                binding.inputEmailLayout.error = "Ingresa un correo"
                hasError = true
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.inputEmailLayout.error = "Ingresa un correo válido"
                hasError = true
            } else {
                binding.inputEmailLayout.error = null
            }

            if (pass.isEmpty()) {
                binding.inputPassLayout.error = "Ingresa tu contraseña"
                hasError = true
            } else {
                binding.inputPassLayout.error = null
            }

            if (!hasError) {
                modeloVista.iniciarSesion(email, pass)
            }
        }

        binding.root.findViewById<View>(R.id.btnRegisterFlow).setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.root.findViewById<View>(R.id.btnGuest).setOnClickListener {
            modeloVista.iniciarComoInvitado()
        }
    }

    private fun observarEstados() {
        modeloVista.estadoAutenticacion.observe(viewLifecycleOwner) { state ->
            when (state) {
                is EstadoAutenticacion.Inactivo -> {
                    binding.progressBar.visibility = View.GONE
                }
                is EstadoAutenticacion.Cargando -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is EstadoAutenticacion.Exito -> {
                    binding.progressBar.visibility = View.GONE
                    navigateBasedOnRole()
                }
                is EstadoAutenticacion.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    mostrarErrorIngreso(state)
                    modeloVista.limpiarError()
                }
            }
        }
    }

    /** Aviso coherente con los colores de Yalazas y con el tipo de fallo recibido. */
    private fun mostrarErrorIngreso(error: EstadoAutenticacion.Error) {
        val tipo = error.tipo
        when (tipo) {
            TipoErrorIngreso.CREDENCIALES -> {
                binding.inputEmailLayout.error = "Verifica tu correo"
                binding.inputPassLayout.error = "Verifica tu contraseña"
            }
            TipoErrorIngreso.CORREO_INVALIDO -> {
                binding.inputEmailLayout.error = "Ingresa un correo válido"
                binding.inputPassLayout.error = null
            }
            else -> {
                binding.inputEmailLayout.error = null
                binding.inputPassLayout.error = null
            }
        }

        val contenido = layoutInflater.inflate(R.layout.dialogo_error_ingreso, null)
        contenido.findViewById<TextView>(R.id.tvTituloErrorIngreso).text = tipo.titulo
        contenido.findViewById<TextView>(R.id.tvMensajeErrorIngreso).text = error.message

        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setView(contenido)
            .setPositiveButton(
                if (tipo == TipoErrorIngreso.CREDENCIALES) "Intentar de nuevo" else "Entendido"
            ) { _, _ ->
                if (_binding != null) {
                    if (tipo == TipoErrorIngreso.CREDENCIALES) binding.inputPass.requestFocus()
                    if (tipo == TipoErrorIngreso.CORREO_INVALIDO) binding.inputEmail.requestFocus()
                }
            }
            .show()
        dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(requireContext(), R.color.yalaza_pink)
        )
    }

    private fun navigateBasedOnRole() {
        val roleStr = com.yalazas.app.repositorio.RepositorioAutenticacion.currentProfile?.role ?: "CLIENTE"
        val role = com.yalazas.app.model.Role.desdeTexto(roleStr)

        when (role) {
            com.yalazas.app.model.Role.ADMIN -> findNavController().navigate(R.id.nav_admin_dashboard)
            com.yalazas.app.model.Role.REPARTIDOR -> findNavController().navigate(R.id.nav_delivery_orders)
            com.yalazas.app.model.Role.COCINERO -> findNavController().navigate(R.id.nav_cocina)
            else -> findNavController().navigate(R.id.action_login_to_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
