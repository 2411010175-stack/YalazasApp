package com.yalazas.app.modelovista

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.yalazas.app.repositorio.RepositorioAutenticacion
import kotlinx.coroutines.launch

/** Estados que la pantalla observa mientras se comprueba o inicia la sesión. */
sealed class EstadoAutenticacion {
    object Inactivo : EstadoAutenticacion()
    object Cargando : EstadoAutenticacion()
    data class Exito(val user: FirebaseUser) : EstadoAutenticacion()
    data class Error(val message: String) : EstadoAutenticacion()
}

/** Expone a la interfaz los resultados del repositorio de autenticación. */
class ModeloVistaAutenticacion : ViewModel() {
    private val repository = RepositorioAutenticacion

    private val _estadoAutenticacion = MutableLiveData<EstadoAutenticacion>(EstadoAutenticacion.Inactivo)
    val estadoAutenticacion: LiveData<EstadoAutenticacion> = _estadoAutenticacion

    /** Recupera el perfil de una sesión previa y rechaza cuentas desactivadas. */
    fun verificarSesion() {
        val user = repository.currentUser ?: return
        _estadoAutenticacion.value = EstadoAutenticacion.Cargando
        viewModelScope.launch {
            try {
                if (repository.currentProfile == null) {
                    repository.obtenerPerfilUsuario(user.uid)
                }

                if (repository.currentProfile?.active == false) {
                    repository.cerrarSesion()
                    _estadoAutenticacion.value = EstadoAutenticacion.Error("Cuenta desactivada. Comunícate con el administrador.")
                } else {
                    _estadoAutenticacion.value = EstadoAutenticacion.Exito(user)
                }
            } catch (e: Exception) {
                _estadoAutenticacion.value = EstadoAutenticacion.Error("No se pudo restaurar la sesión. Intenta iniciar sesión de nuevo.")
            }
        }
    }

    /** Valida los campos antes de solicitar el ingreso al repositorio. */
    fun iniciarSesion(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _estadoAutenticacion.value = EstadoAutenticacion.Error("Llena todos los campos")
            return
        }
        _estadoAutenticacion.value = EstadoAutenticacion.Cargando
        viewModelScope.launch {
            val result = repository.iniciarSesion(email, pass)
            if (result.isSuccess) {
                _estadoAutenticacion.value = EstadoAutenticacion.Exito(result.getOrThrow())
            } else {
                _estadoAutenticacion.value = EstadoAutenticacion.Error(result.exceptionOrNull()?.message ?: "Error de autenticación")
            }
        }
    }

    /** Publica el resultado del acceso anónimo. */
    fun iniciarComoInvitado() {
        _estadoAutenticacion.value = EstadoAutenticacion.Cargando
        viewModelScope.launch {
            val result = repository.iniciarComoInvitado()
            if (result.isSuccess) {
                _estadoAutenticacion.value = EstadoAutenticacion.Exito(result.getOrThrow())
            } else {
                _estadoAutenticacion.value = EstadoAutenticacion.Error(result.exceptionOrNull()?.message ?: "Error al entrar como invitado")
            }
        }
    }

    /** Publica el resultado de la creación de una cuenta. */
    fun registrarCuenta(email: String, pass: String, name: String) {
        if (email.isBlank() || pass.isBlank() || name.isBlank()) {
            _estadoAutenticacion.value = EstadoAutenticacion.Error("Llena todos los campos obligatorios")
            return
        }
        _estadoAutenticacion.value = EstadoAutenticacion.Cargando
        viewModelScope.launch {
            val result = repository.registrarCuenta(email, pass, name)
            if (result.isSuccess) {
                _estadoAutenticacion.value = EstadoAutenticacion.Exito(result.getOrThrow())
            } else {
                _estadoAutenticacion.value = EstadoAutenticacion.Error(result.exceptionOrNull()?.message ?: "Error al registrar cuenta")
            }
        }
    }
}
