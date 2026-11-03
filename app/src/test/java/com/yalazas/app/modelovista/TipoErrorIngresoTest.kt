package com.yalazas.app.modelovista

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TipoErrorIngresoTest {
    @Test fun erroresDeCredencialesDanElMismoAviso() {
        for (codigo in listOf(
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND"
        )) {
            assertEquals(TipoErrorIngreso.CREDENCIALES, TipoErrorIngreso.desdeCodigo(codigo))
        }
        assertTrue(TipoErrorIngreso.CREDENCIALES.mensaje.contains("correo o la contraseña"))
    }

    @Test fun erroresConOtraSolucionMantienenSuAviso() {
        assertEquals(TipoErrorIngreso.CORREO_INVALIDO, TipoErrorIngreso.desdeCodigo("ERROR_INVALID_EMAIL"))
        assertEquals(TipoErrorIngreso.SIN_CONEXION, TipoErrorIngreso.desdeCodigo("ERROR_NETWORK_REQUEST_FAILED"))
        assertEquals(TipoErrorIngreso.DEMASIADOS_INTENTOS, TipoErrorIngreso.desdeCodigo("ERROR_TOO_MANY_REQUESTS"))
        assertEquals(TipoErrorIngreso.CUENTA_DESACTIVADA, TipoErrorIngreso.desdeCodigo("ERROR_USER_DISABLED"))
        assertEquals(TipoErrorIngreso.GENERAL, TipoErrorIngreso.desdeCodigo("ERROR_INESPERADO"))
    }
}
