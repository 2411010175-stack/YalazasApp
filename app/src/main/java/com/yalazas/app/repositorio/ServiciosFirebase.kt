package com.yalazas.app.repositorio

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/** Punto único de acceso a las instancias administradas por los SDK de Firebase. */

object ServiciosFirebase {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
}
