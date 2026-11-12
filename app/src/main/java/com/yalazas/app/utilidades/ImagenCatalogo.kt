package com.yalazas.app.utilidades

import java.net.URI

/** Solicita al CDN una imagen pequeña; otras URL conservan su formato original. */
object ImagenCatalogo {
    fun miniatura(url: String, ancho: Int, alto: Int): String {
        if (ancho !in 1..2000 || alto !in 1..2000) return url
        val uri = try { URI(url) } catch (_: Exception) { return url }
        if (uri.scheme != "https" || uri.host != "res.cloudinary.com") return url
        val prefijo = "/image/upload/"
        val ruta = uri.rawPath ?: return url
        val inicio = ruta.indexOf(prefijo)
        if (inicio < 0 || ruta.substring(inicio + prefijo.length).isBlank()) return url
        val transformacion = "c_fill,w_$ancho,h_$alto,q_auto,f_auto/"
        // Se inserta la transformación sin re-escapar caracteres válidos del public_id.
        val inicioAbsoluto = url.indexOf(prefijo)
        return if (inicioAbsoluto >= 0) {
            url.substring(0, inicioAbsoluto + prefijo.length) + transformacion +
                url.substring(inicioAbsoluto + prefijo.length)
        } else url
    }
}
