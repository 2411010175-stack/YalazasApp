package com.yalazas.app.repositorio

import com.yalazas.app.BuildConfig
import com.google.firebase.FirebaseApp
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.security.MessageDigest

/** Cliente HTTPS del servicio que reserva insumos y crea el pedido en una transacción. */
object ApiPedidosAtomicos {
    val configurada: Boolean get() = BuildConfig.ORDER_API_URL.isNotBlank()

    fun crear(solicitud: SolicitudPedido, terminado: (Result<String>) -> Unit) {
        val payload = JSONObject().apply {
            put("serviceType", solicitud.serviceType.ifBlank { if (solicitud.isDelivery) "DELIVERY" else "RETIRO" })
            put("pos", false)
            put("tableNumber", solicitud.tableNumber)
            put("customerName", solicitud.customerName.ifBlank { solicitud.profile.name })
            put("phone", solicitud.profile.phone)
            put("address", solicitud.address)
            put("reference", solicitud.reference)
            put("latitude", solicitud.latitude)
            put("longitude", solicitud.longitude)
            put("comment", solicitud.comment)
            put("isDelivery", solicitud.isDelivery)
            put("paymentMethod", solicitud.paymentMethod)
            put("paymentAmount", solicitud.paymentAmount)
            put("expectedTotal", solicitud.totalAmount)
            put("deliveryTariffVersion", solicitud.deliveryTariffVersion)
            put("items", JSONArray().apply {
                solicitud.items.forEach { item ->
                    put(JSONObject().apply {
                        put("productId", item.product.id)
                        put("quantity", item.quantity)
                        put("notes", item.notes)
                        put("selectedSauces", JSONArray(item.selectedSauces))
                    })
                }
            })
        }
        // Repetir la misma solicitud tras un corte de red reutiliza el mismo identificador.
        val huella = JSONArray().apply {
            put(solicitud.profile.uid)
            put(solicitud.serviceType)
            put(solicitud.tableNumber)
            put(solicitud.customerName)
            put(solicitud.address)
            put(solicitud.reference)
            put(solicitud.latitude)
            put(solicitud.longitude)
            put(solicitud.comment)
            put(solicitud.isDelivery)
            put(solicitud.paymentMethod)
            put(solicitud.paymentAmount)
            put(solicitud.totalAmount)
            put(solicitud.deliveryTariffVersion)
            put(JSONArray().apply {
                solicitud.items.forEach { item ->
                    put(JSONArray().put(item.product.id).put(item.quantity)
                        .put(item.notes).put(JSONArray(item.selectedSauces)))
                }
            })
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(huella.toString().toByteArray(Charsets.UTF_8))
        val clave = bytes.joinToString("") { "%02x".format(it) }
        val preferencias = FirebaseApp.getInstance().applicationContext
            .getSharedPreferences("solicitudes_pedido_pendientes", Context.MODE_PRIVATE)
        val identificador = preferencias.getString(clave, null) ?:
            UUID.randomUUID().toString().replace("-", "").also {
            if (!preferencias.edit().putString(clave, it).commit()) {
                terminado(Result.failure(IllegalStateException("No se pudo guardar la solicitud para reintentar.")))
                return
            }
        }
        payload.put("requestId", identificador)
        enviar(JSONObject().put("action", "create").put("order", payload)) { resultado ->
            resultado.onSuccess {
                preferencias.edit().remove(clave).apply()
                terminado(Result.success(it.getString("id")))
            }.onFailure { terminado(Result.failure(it)) }
        }
    }

    fun cancelar(pedidoId: String, terminado: (Result<Unit>) -> Unit) {
        enviar(JSONObject().put("action", "cancel").put("orderId", pedidoId)) { resultado ->
            terminado(resultado.map { Unit })
        }
    }

    private fun enviar(payload: JSONObject, terminado: (Result<JSONObject>) -> Unit) {
        if (!BuildConfig.ORDER_API_URL.startsWith("https://")) {
            terminado(Result.failure(IllegalStateException("La URL de la API de pedidos debe usar HTTPS.")))
            return
        }
        val usuario = ServiciosFirebase.auth.currentUser
        if (usuario == null) {
            terminado(Result.failure(IllegalStateException("Inicia sesión para continuar.")))
            return
        }
        usuario.getIdToken(false).addOnSuccessListener { token ->
            val idToken = token.token
            if (idToken.isNullOrBlank()) {
                terminado(Result.failure(IllegalStateException("No se pudo verificar la sesión.")))
                return@addOnSuccessListener
            }
            Thread {
                val resultado = runCatching {
                    val conexion = (URL(BuildConfig.ORDER_API_URL).openConnection() as HttpURLConnection)
                    try {
                        conexion.requestMethod = "POST"
                        conexion.connectTimeout = 15000
                        conexion.readTimeout = 30000
                        conexion.setRequestProperty("Content-Type", "application/json")
                        conexion.setRequestProperty("Authorization", "Bearer $idToken")
                        conexion.doOutput = true
                        conexion.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                        val stream = if (conexion.responseCode in 200..299) conexion.inputStream
                            else conexion.errorStream
                                ?: throw IllegalStateException("La API no respondió correctamente.")
                        val texto = stream.bufferedReader().use { it.readText() }
                        val respuesta = JSONObject(texto)
                        if (conexion.responseCode !in 200..299) {
                            throw IllegalStateException(respuesta.optString("error", "No se pudo procesar el pedido."))
                        }
                        respuesta
                    } finally { conexion.disconnect() }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post { terminado(resultado) }
            }.start()
        }.addOnFailureListener { terminado(Result.failure(it)) }
    }
}
