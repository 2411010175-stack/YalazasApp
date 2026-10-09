package com.yalazas.app.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.yalazas.app.model.UserProfile
import kotlinx.coroutines.tasks.await

object AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _liveProfile = MutableLiveData<UserProfile?>()
    val liveProfile: LiveData<UserProfile?> = _liveProfile

    var currentProfile: UserProfile? = null
        private set(value) {
            field = value
            _liveProfile.postValue(value)
        }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun login(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user!!
            fetchUserProfile(user.uid)

            if (currentProfile?.active == false) {
                logout()
                return Result.failure(Exception("Cuenta desactivada. Comunícate con el administrador."))
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginAsGuest(): Result<FirebaseUser> {
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user!!
            currentProfile = UserProfile(uid = user.uid, role = "CLIENTE")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, pass: String, name: String): Result<FirebaseUser> {
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

    suspend fun updateProfile(
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

            db.collection("users").document(uid).set(updatedProfile).await()
            currentProfile = updatedProfile
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDeliveryLocation(address: String, reference: String, lat: Double, lng: Double): Result<Unit> {
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
            db.collection("users").document(uid).set(updated).await()
            currentProfile = updated
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(uid: String): Result<Unit> {
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

    fun logout() {
        auth.signOut()
        currentProfile = null
    }
}
