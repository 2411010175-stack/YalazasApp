package com.yalazas.app.repositorio

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration

/** Lista de mesas del salón. Sin configuración remota se conservan las cinco mesas existentes. */
object RepositorioMesas {
    val mesasIniciales: List<Int> = (1..5).toList()
    private val referencia get() = ServiciosFirebase.firestore.collection("settings").document("salon")

    fun escuchar(alCambiar: (List<Int>) -> Unit, alError: (Exception) -> Unit): ListenerRegistration =
        referencia.addSnapshotListener { documento, error ->
            if (error != null) {
                alError(error)
            } else if (documento != null) {
                alCambiar(if (documento.exists()) numeros(documento.get("tableNumbers")) else mesasIniciales)
            }
        }

    fun agregar(numero: Int, terminado: (Result<Unit>) -> Unit) {
        if (numero !in 1..200) {
            terminado(Result.failure(IllegalArgumentException("El número de mesa debe estar entre 1 y 200.")))
            return
        }
        ServiciosFirebase.firestore.runTransaction { transaccion ->
            val actual = transaccion.get(referencia)
            val mesas = if (actual.exists()) numeros(actual.get("tableNumbers")) else mesasIniciales
            require(numero !in mesas) { "La mesa $numero ya existe." }
            require(mesas.size < 200) { "El salón llegó al máximo de 200 mesas." }
            transaccion.set(referencia, mapOf(
                "tableNumbers" to (mesas + numero).sorted(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            Unit
        }.addOnSuccessListener { terminado(Result.success(Unit)) }
            .addOnFailureListener { terminado(Result.failure(it)) }
    }

    /** Retira una mesa del selector; los pedidos históricos conservan su número. */
    fun quitar(numero: Int, terminado: (Result<Unit>) -> Unit) {
        ServiciosFirebase.firestore.runTransaction { transaccion ->
            val actual = transaccion.get(referencia)
            val mesas = if (actual.exists()) numeros(actual.get("tableNumbers")) else mesasIniciales
            require(numero in mesas) { "La mesa $numero ya no está disponible." }
            transaccion.set(referencia, mapOf(
                "tableNumbers" to mesas.filterNot { it == numero },
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            Unit
        }.addOnSuccessListener { terminado(Result.success(Unit)) }
            .addOnFailureListener { terminado(Result.failure(it)) }
    }

    private fun numeros(valor: Any?): List<Int> = (valor as? List<*>)
        .orEmpty().mapNotNull { (it as? Number)?.toInt() }.filter { it in 1..200 }.distinct().sorted()
}
