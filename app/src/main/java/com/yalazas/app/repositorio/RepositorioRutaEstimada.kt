package com.yalazas.app.repositorio

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/** Consulta una ruta aproximada por calles para el mapa del cliente. */
object RepositorioRutaEstimada {
    // Servidor público de demostración. Sustituir por un servicio propio antes de uso intensivo.
    private const val SERVIDOR = "https://router.project-osrm.org"
    private val ejecutor = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())

    fun consultar(origen: GeoPoint, destino: GeoPoint, alResultado: (List<GeoPoint>?) -> Unit) {
        ejecutor.execute {
            val ruta = try {
                obtenerRuta(origen, destino)
            } catch (_: Exception) {
                null
            }
            principal.post { alResultado(ruta) }
        }
    }

    private fun obtenerRuta(origen: GeoPoint, destino: GeoPoint): List<GeoPoint>? {
        val direccion = String.format(
            Locale.US,
            "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=simplified&geometries=geojson&steps=false",
            SERVIDOR, origen.longitude, origen.latitude, destino.longitude, destino.latitude
        )
        val conexion = (URL(direccion).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "YalazasApp/1.0 (ruta estimada)")
        }
        try {
            if (conexion.responseCode != HttpURLConnection.HTTP_OK) return null
            val respuesta = JSONObject(conexion.inputStream.bufferedReader().use { it.readText() })
            if (respuesta.optString("code") != "Ok") return null
            val rutas = respuesta.optJSONArray("routes") ?: return null
            val coordenadas = rutas.optJSONObject(0)?.optJSONObject("geometry")
                ?.optJSONArray("coordinates") ?: return null
            if (coordenadas.length() < 2 || coordenadas.length() > 2_000) return null
            return (0 until coordenadas.length()).map { indice ->
                val punto = coordenadas.getJSONArray(indice)
                GeoPoint(punto.getDouble(1), punto.getDouble(0))
            }
        } finally {
            conexion.disconnect()
        }
    }
}
