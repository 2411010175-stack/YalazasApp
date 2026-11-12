package com.yalazas.app.repositorio

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.yalazas.app.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Fotos públicas en Cloudinary; comprobantes privados y pequeños en Firestore (plan Spark). */
object RepositorioImagenes {
    private const val MAX_PRODUCTO_BYTES = 5 * 1024 * 1024
    private const val MAX_COMPROBANTE_BYTES = 700 * 1024
    private val ejecutor = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())
    private val db get() = ServiciosFirebase.firestore

    fun subirProducto(resolver: ContentResolver, uri: Uri, terminado: (Result<String>) -> Unit) {
        ejecutar(terminado) {
            val cloud = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
            val preset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
            require(cloud.isNotEmpty() && preset.isNotEmpty()) {
                "Configura CLOUDINARY_CLOUD_NAME y CLOUDINARY_UPLOAD_PRESET en local.properties."
            }
            require(cloud.matches(Regex("[A-Za-z0-9_-]+")) && preset.matches(Regex("[A-Za-z0-9_-]+"))) {
                "La configuración de Cloudinary contiene caracteres inválidos."
            }
            val (bytes, mime) = leerImagen(resolver, uri, MAX_PRODUCTO_BYTES)
            val limite = "yalazas${System.currentTimeMillis()}"
            val conexion = (URL("https://api.cloudinary.com/v1_1/$cloud/image/upload")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 90_000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$limite")
            }
            try {
                conexion.outputStream.use { salida ->
                    salida.write("--$limite\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n$preset\r\n".toByteArray())
                    salida.write("--$limite\r\nContent-Disposition: form-data; name=\"file\"; filename=\"producto.jpg\"\r\nContent-Type: $mime\r\n\r\n".toByteArray())
                    salida.write(bytes)
                    salida.write("\r\n--$limite--\r\n".toByteArray())
                }
                if (conexion.responseCode !in 200..299) {
                    val detalle = conexion.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("Cloudinary rechazó la foto (${conexion.responseCode}): ${detalle.take(180)}")
                }
                val respuesta = JSONObject(conexion.inputStream.bufferedReader().use { it.readText() })
                val url = respuesta.getString("secure_url")
                require(url.startsWith("https://res.cloudinary.com/$cloud/")) { "Cloudinary devolvió una URL inesperada." }
                url
            } finally {
                conexion.disconnect()
            }
        }
    }

    fun subirComprobante(pedidoId: String, resolver: ContentResolver, uri: Uri, terminado: (Result<Unit>) -> Unit) {
        ejecutar(terminado) {
            val imagen = comprimirComprobante(resolver, uri)
            val pedido = db.collection("orders").document(pedidoId)
            val comprobante = pedido.collection("privado").document("comprobante")
            val datos = mapOf(
                "imagen" to Blob.fromBytes(imagen),
                "mime" to "image/jpeg",
                "subidoPor" to (ServiciosFirebase.auth.currentUser?.uid ?: ""),
                "subidoEn" to FieldValue.serverTimestamp()
            )
            val lote = db.batch()
            lote.set(comprobante, datos)
            lote.update(pedido, "paymentProofStatus", "EN_REVISION")
            Tasks.await(lote.commit())
            Unit
        }
    }

    fun obtenerComprobante(pedidoId: String, terminado: (Result<ByteArray>) -> Unit) {
        ejecutar(terminado) {
            val documento = Tasks.await(
                db.collection("orders").document(pedidoId).collection("privado").document("comprobante").get()
            )
            documento.getBlob("imagen")?.toBytes()
                ?: throw IllegalStateException("Este pedido aún no tiene un comprobante disponible.")
        }
    }

    fun revisarComprobante(pedidoId: String, aprobar: Boolean, terminado: (Result<Unit>) -> Unit) {
        ejecutar(terminado) {
            val pedidoRef = db.collection("orders").document(pedidoId)
            val comprobanteRef = pedidoRef.collection("privado").document("comprobante")
            Tasks.await(db.runTransaction { transaccion ->
                val pedido = transaccion.get(pedidoRef)
                val comprobante = transaccion.get(comprobanteRef)
                require(pedido.exists() && comprobante.getBlob("imagen") != null) { "Falta el comprobante." }
                require(pedido.getString("paymentProofStatus") == "EN_REVISION") { "Este comprobante ya fue revisado." }
                require(pedido.getString("paymentStatus") != "PAGADO" && pedido.getString("status") != "CANCELADO") {
                    "El pedido ya está pagado o cancelado."
                }
                val cambios = mutableMapOf<String, Any>(
                    "paymentProofStatus" to if (aprobar) "APROBADO" else "RECHAZADO"
                )
                if (aprobar) {
                    cambios["paymentStatus"] = "PAGADO"
                    cambios["paymentVerifiedBy"] = ServiciosFirebase.auth.currentUser?.uid.orEmpty()
                    cambios["paymentVerifiedAt"] = FieldValue.serverTimestamp()
                }
                transaccion.update(pedidoRef, cambios)
                Unit
            })
            Unit
        }
    }

    private fun comprimirComprobante(resolver: ContentResolver, uri: Uri): ByteArray {
        val (original, _) = leerImagen(resolver, uri, 12 * 1024 * 1024)
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, opciones)
        require(opciones.outWidth > 0 && opciones.outHeight > 0) { "La imagen no se pudo leer." }
        var escala = 1
        while (opciones.outWidth / escala > 1800 || opciones.outHeight / escala > 1800) escala *= 2
        val imagen = BitmapFactory.decodeByteArray(original, 0, original.size,
            BitmapFactory.Options().apply { inSampleSize = escala })
            ?: throw IllegalArgumentException("La imagen no se pudo leer.")
        try {
            for (calidad in listOf(85, 75, 65, 55)) {
                val salida = ByteArrayOutputStream()
                imagen.compress(Bitmap.CompressFormat.JPEG, calidad, salida)
                if (salida.size() <= MAX_COMPROBANTE_BYTES) return salida.toByteArray()
            }
        } finally {
            imagen.recycle()
        }
        throw IllegalArgumentException("La foto sigue siendo demasiado grande. Toma otra imagen más cercana al comprobante.")
    }

    private fun leerImagen(resolver: ContentResolver, uri: Uri, maxBytes: Int): Pair<ByteArray, String> {
        val mime = resolver.getType(uri)?.lowercase()
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "Elige una imagen JPG, PNG o WebP." }
        val entrada = resolver.openInputStream(uri) ?: throw IllegalArgumentException("No se pudo abrir la imagen.")
        val salida = ByteArrayOutputStream()
        entrada.use { archivo ->
            val bloque = ByteArray(8192)
            while (true) {
                val leidos = archivo.read(bloque)
                if (leidos < 0) break
                if (salida.size() + leidos > maxBytes) throw IllegalArgumentException("La imagen supera el tamaño permitido.")
                salida.write(bloque, 0, leidos)
            }
        }
        require(salida.size() > 0) { "La imagen está vacía." }
        return salida.toByteArray() to mime!!
    }

    private fun <T> ejecutar(terminado: (Result<T>) -> Unit, accion: () -> T) {
        ejecutor.execute {
            val resultado = runCatching(accion)
            principal.post { terminado(resultado) }
        }
    }
}
