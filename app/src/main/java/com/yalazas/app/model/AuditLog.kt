package com.yalazas.app.model

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
