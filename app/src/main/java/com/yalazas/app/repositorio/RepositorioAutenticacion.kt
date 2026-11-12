package com.yalazas.app.repositorio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseUser
import com.yalazas.app.model.UserProfile
import kotlinx.coroutines.tasks.await

/** Error propio que permite mostrar un aviso específico sin depender del texto de Firebase. */
class CuentaDesactivadaException : IllegalStateException("Cuenta desactivada")

/** Centraliza la sesión de Firebase Auth y el perfil correspondiente en Firestore. */
object RepositorioAutenticacion {
    private val auth = ServiciosFirebase.auth
    private val db = ServiciosFirebase.firestore

    private val _liveProfile = MutableLiveData<UserProfile?>()
    val liveProfile: LiveData<UserProfile?> = _liveProfile

    // Al cambiar el perfil también se notifica a las pantallas que observan liveProfile.
    var currentProfile: UserProfile? = null
        private set(value) {
            field = value
            _liveProfile.postValue(value)
        }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** Inicia sesión y comprueba si la cuenta sigue activa. */
    suspend fun iniciarSesion(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user!!
            obtenerPerfilUsuario(user.uid)

            if (currentProfile?.active == false) {
                cerrarSesion()
                return Result.failure(CuentaDesactivadaException())
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Crea una sesión anónima con perfil local de cliente. */
    suspend fun iniciarComoInvitado(): Result<FirebaseUser> {
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user!!
            currentProfile = UserProfile(uid = user.uid, role = "CLIENTE")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Crea la cuenta de Auth y luego su documento en `users`. */
    suspend fun registrarCuenta(email: String, pass: String, name: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user!!

            val newProfile = UserProfile(
                uid = user.uid,
                email = email,
                role = "CLIENTE",
                name = name
            )
            db.collection("users").document(user.uid).set(newProfile).await()
            currentProfile = newProfile

            Result.success(user)
        } catch (e: Exception) {
            auth.currentUser?.delete()
            auth.signOut()
            Result.failure(e)
        }
    }

    /** Guarda los datos de contacto sin renombrar las propiedades persistidas. */
    suspend fun actualizarPerfil(
        name: String,
        phone: String,
        address: String,
        reference: String,
        latitude: Double = currentProfile?.latitude ?: 0.0,
        longitude: Double = currentProfile?.longitude ?: 0.0
    ): Result<Unit> {
        return try {
            val uid = currentUser?.uid ?: throw Exception("No user logged in")
            val updatedProfile = currentProfile?.copy(
                name = name,
                phone = phone,
                address = address,
                reference = reference,
                latitude = latitude,
                longitude = longitude,
                updatedAt = System.currentTimeMillis()
            ) ?: UserProfile(
                uid = uid,
                name = name,
                phone = phone,
                address = address,
                reference = reference,
                latitude = latitude,
                longitude = longitude
            )

            guardarCamposEditables(uid, mapOf(
                "name" to name, "phone" to phone, "address" to address,
                "reference" to reference, "latitude" to latitude,
                "longitude" to longitude, "updatedAt" to updatedProfile.updatedAt
            ), updatedProfile)
            currentProfile = updatedProfile
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Actualiza dirección y coordenadas que se usarán en pedidos futuros. */
    suspend fun actualizarUbicacionEntrega(address: String, reference: String, lat: Double, lng: Double): Result<Unit> {
        return try {
            val uid = currentUser?.uid ?: return Result.success(Unit)
            val updated = currentProfile?.copy(
                address = address,
                reference = reference,
                latitude = lat,
                longitude = lng,
                updatedAt = System.currentTimeMillis()
            ) ?: UserProfile(
                uid = uid,
                address = address,
                reference = reference,
                latitude = lat,
                longitude = lng
            )
            guardarCamposEditables(uid, mapOf(
                "address" to address, "reference" to reference,
                "latitude" to lat, "longitude" to lng,
                "updatedAt" to updated.updatedAt
            ), updated)
            currentProfile = updated
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** En perfiles existentes solo actualiza campos propios para conservar los datos del vehículo. */
    private suspend fun guardarCamposEditables(uid: String, campos: Map<String, Any>, perfilNuevo: UserProfile) {
        val referencia = db.collection("users").document(uid)
        if (referencia.get().await().exists()) {
            referencia.update(campos).await()
        } else {
            referencia.set(perfilNuevo).await()
        }
    }

    /** Recupera el perfil del usuario autenticado; crea un perfil local si el documento falta. */
    suspend fun obtenerPerfilUsuario(uid: String): Result<Unit> {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            currentProfile = if (doc.exists()) {
                doc.toObject(UserProfile::class.java) ?: UserProfile(uid = uid, role = "CLIENTE")
            } else {
                UserProfile(uid = uid, role = "CLIENTE")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            currentProfile = UserProfile(uid = uid, role = "CLIENTE")
            Result.failure(e)
        }
    }

    fun cerrarSesion() {
        auth.signOut()
        currentProfile = null
    }
}
