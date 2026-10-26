package com.yalazas.app.model

/** Datos del registro de auditoría de los cambios administrativos. */
data class AuditLog(
    val id: String = "",
    val usuarioAfectadoId: String = "",
    val usuarioAdministradorId: String = "",
    val accion: String = "",
    val rolAnterior: String = "",
    val rolNuevo: String = "",
    val fecha: Long = System.currentTimeMillis(),
    val detalles: String = ""
)
