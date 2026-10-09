package com.yalazas.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.yalazas.app.repository.AuthRepository
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    fun checkSession() {
        val user = repository.currentUser ?: return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                if (repository.currentProfile == null) {
                    repository.fetchUserProfile(user.uid)
                }

                if (repository.currentProfile?.active == false) {
                    repository.logout()
                    _authState.value = AuthState.Error("Cuenta desactivada. Comunícate con el administrador.")
                } else {
                    _authState.value = AuthState.Success(user)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("No se pudo restaurar la sesión. Intenta iniciar sesión de nuevo.")
            }
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authState.value = AuthState.Error("Llena todos los campos")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.login(email, pass)
            if (result.isSuccess) {
                _authState.value = AuthState.Success(result.getOrThrow())
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error de autenticación")
            }
        }
    }

    fun loginAsGuest() {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.loginAsGuest()
            if (result.isSuccess) {
                _authState.value = AuthState.Success(result.getOrThrow())
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error al entrar como invitado")
            }
        }
    }

    fun register(email: String, pass: String, name: String) {
        if (email.isBlank() || pass.isBlank() || name.isBlank()) {
            _authState.value = AuthState.Error("Llena todos los campos obligatorios")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.register(email, pass, name)
            if (result.isSuccess) {
                _authState.value = AuthState.Success(result.getOrThrow())
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Error al registrar cuenta")
            }
        }
    }
}
