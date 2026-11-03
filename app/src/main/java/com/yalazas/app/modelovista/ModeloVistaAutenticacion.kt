package com.yalazas.app.modelovista

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.yalazas.app.repositorio.CuentaDesactivadaException
import com.yalazas.app.repositorio.RepositorioAutenticacion
import kotlinx.coroutines.launch

/** Estados que la pantalla observa mientras se comprueba o inicia la sesión. */
sealed class EstadoAutenticacion {
    object Inactivo : EstadoAutenticacion()
    object Cargando : EstadoAutenticacion()
    data class Exito(val user: FirebaseUser) : EstadoAutenticacion()
    data class Error(
        val message: String,
        val tipo: TipoErrorIngreso = TipoErrorIngreso.GENERAL
    ) : EstadoAutenticacion()
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
                    _estadoAutenticacion.value = EstadoAutenticacion.Error(
                        TipoErrorIngreso.CUENTA_DESACTIVADA.mensaje,
                        TipoErrorIngreso.CUENTA_DESACTIVADA
                    )
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
                val error = result.exceptionOrNull()
                val tipo = when (error) {
                    is CuentaDesactivadaException -> TipoErrorIngreso.CUENTA_DESACTIVADA
                    is FirebaseNetworkException -> TipoErrorIngreso.SIN_CONEXION
                    is FirebaseAuthException -> {
                        val clasificado = TipoErrorIngreso.desdeCodigo(error.errorCode)
                        if (clasificado != TipoErrorIngreso.GENERAL) clasificado
                        else if (error is FirebaseAuthInvalidCredentialsException ||
                            error is FirebaseAuthInvalidUserException
                        ) TipoErrorIngreso.CREDENCIALES else TipoErrorIngreso.GENERAL
                    }
                    else -> TipoErrorIngreso.GENERAL
                }
                _estadoAutenticacion.value = EstadoAutenticacion.Error(tipo.mensaje, tipo)
            }
        }
    }

    /** Evita que una alerta ya mostrada vuelva a abrirse al recrear la pantalla. */
    fun limpiarError() {
        if (_estadoAutenticacion.value is EstadoAutenticacion.Error) {
            _estadoAutenticacion.value = EstadoAutenticacion.Inactivo
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
