package com.yalazas.app.modelovista

/** Mensajes seguros y comprensibles para los errores que puede recibir el formulario de ingreso. */
enum class TipoErrorIngreso(val titulo: String, val mensaje: String) {
    CREDENCIALES(
        "Revisa tus datos",
        "El correo o la contraseña son incorrectos. Verifica ambos datos e inténtalo de nuevo."
    ),
    CORREO_INVALIDO(
        "Correo no válido",
        "Escribe una dirección de correo válida para iniciar sesión."
    ),
    CUENTA_DESACTIVADA(
        "Cuenta desactivada",
        "Esta cuenta está desactivada. Comunícate con el administrador del restaurante."
    ),
    DEMASIADOS_INTENTOS(
        "Demasiados intentos",
        "Se bloqueó el acceso temporalmente. Espera unos minutos antes de intentarlo de nuevo."
    ),
    SIN_CONEXION(
        "Sin conexión",
        "No pudimos conectar con el servicio. Revisa tu conexión a Internet e inténtalo de nuevo."
    ),
    GENERAL(
        "No se pudo ingresar",
        "No pudimos iniciar sesión en este momento. Inténtalo de nuevo."
    );

    companion object {
        /** Firebase puede ocultar si existe el correo; esos códigos reciben el mismo mensaje. */
        fun desdeCodigo(codigo: String?): TipoErrorIngreso = when (codigo) {
            "ERROR_INVALID_EMAIL" -> CORREO_INVALIDO
            "ERROR_USER_DISABLED" -> CUENTA_DESACTIVADA
            "ERROR_TOO_MANY_REQUESTS" -> DEMASIADOS_INTENTOS
            "ERROR_NETWORK_REQUEST_FAILED" -> SIN_CONEXION
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> CREDENCIALES
            else -> GENERAL
        }
    }
}
