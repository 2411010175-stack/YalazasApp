package com.yalazas.app.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentLoginBinding
import com.yalazas.app.viewmodel.AuthState
import com.yalazas.app.viewmodel.AuthViewModel

class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        setupObservers()
        setupListeners()
        
        // Verificar si ya hay sesión iniciada (para empleados)
        viewModel.checkSession()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.inputEmail.text.toString()
            val pass = binding.inputPass.text.toString()
            viewModel.login(email, pass)
        }

        binding.root.findViewById<View>(R.id.btnRegisterFlow).setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.root.findViewById<View>(R.id.btnGuest).setOnClickListener {
            viewModel.loginAsGuest()
        }
    }

    private fun setupObservers() {
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                }
                is AuthState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is AuthState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    navigateBasedOnRole()
                }
                is AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateBasedOnRole() {
        val roleStr = com.yalazas.app.repository.AuthRepository.currentProfile?.role ?: "CLIENTE"
        val role = com.yalazas.app.model.Role.fromString(roleStr)

        when (role) {
            com.yalazas.app.model.Role.ADMIN -> findNavController().navigate(R.id.nav_admin_dashboard)
            com.yalazas.app.model.Role.REPARTIDOR -> findNavController().navigate(R.id.nav_delivery_orders)
            else -> findNavController().navigate(R.id.action_login_to_home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
