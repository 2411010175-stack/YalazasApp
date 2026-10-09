package com.yalazas.app.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.yalazas.app.R
import com.yalazas.app.viewmodel.AuthState
import com.yalazas.app.viewmodel.AuthViewModel

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private val viewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputName = view.findViewById<TextInputEditText>(R.id.inputName)
        val inputEmail = view.findViewById<TextInputEditText>(R.id.inputEmail)
        val inputPass = view.findViewById<TextInputEditText>(R.id.inputPass)
        val btnRegister = view.findViewById<MaterialButton>(R.id.btnRegister)
        val btnLoginFlow = view.findViewById<View>(R.id.btnLoginFlow)
        val progressBar = view.findViewById<View>(R.id.progressBar)

        btnRegister.setOnClickListener {
            viewModel.register(
                email = inputEmail.text.toString(),
                pass = inputPass.text.toString(),
                name = inputName.text.toString()
            )
        }

        btnLoginFlow.setOnClickListener {
            findNavController().popBackStack()
        }

        viewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnRegister.isEnabled = false
                }
                is AuthState.Success -> {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Cuenta creada exitosamente", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_register_to_menu)
                }
                is AuthState.Error -> {
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
}
