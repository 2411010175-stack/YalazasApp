package com.yalazas.app.ui.delivery

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestoreException
import com.yalazas.app.MainActivity
import com.yalazas.app.R
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repositorio.RepositorioUbicacionRepartidor
import com.yalazas.app.repositorio.ServiciosFirebase

/** Comparte GPS únicamente mientras este repartidor tiene entregas en camino. */
class ServicioUbicacionRepartidor : Service() {
    private val ubicacion by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var escuchaPedidos: ListenerRegistration? = null
    private var escuchandoGps = false
    private var destruido = false
    private var uid: String = ""
    private var pedidosEnRuta: Set<String> = emptySet()

    private val escuchaGps = object : LocationCallback() {
        override fun onLocationResult(resultado: LocationResult) {
            val posicion = resultado.lastLocation ?: return
            if (ServiciosFirebase.auth.currentUser?.uid != uid) {
                stopSelf()
                return
            }
            // La precisión informada por Android puede variar; una posición antigua no se publica.
            if (!posicion.hasAccuracy() || posicion.accuracy > 200f ||
                System.currentTimeMillis() - posicion.time > 30_000L ||
                posicion.latitude !in -90.0..90.0 || posicion.longitude !in -180.0..180.0
            ) return

            pedidosEnRuta.forEach { pedidoId ->
                RepositorioUbicacionRepartidor.publicar(pedidoId, posicion) { error ->
                    Log.w("UbicacionRepartidor", "No se pudo publicar la posición de $pedidoId", error)
                    val permisoDenegado = (error as? FirebaseFirestoreException)?.code ==
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                    mostrarAviso(if (permisoDenegado) {
                        "Firestore denegó el GPS. Revisa las reglas de rastreo."
                    } else {
                        "No se pudo compartir la ubicación. Revisa Internet."
                    })
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        uid = ServiciosFirebase.auth.currentUser?.uid.orEmpty()
        if (uid.isBlank() || !tienePermiso()) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            iniciarAvisoPermanente()
        } catch (error: RuntimeException) {
            Log.w("UbicacionRepartidor", "No se pudo iniciar el servicio de ubicación", error)
            stopSelf()
            return START_NOT_STICKY
        }
        if (escuchaPedidos == null) {
            escuchaPedidos = ServiciosFirebase.firestore.collection("orders")
                .whereEqualTo("isDelivery", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("UbicacionRepartidor", "No se pudieron consultar los pedidos", error)
                        stopSelf()
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    pedidosEnRuta = snapshot.documents.mapNotNull { documento ->
                        documento.toObject(Order::class.java)?.takeIf {
                            it.status == OrderStatus.EN_CAMINO.name && it.deliveryDriverId == uid
                        }?.let { documento.id }
                    }.toSet()
                    if (pedidosEnRuta.isEmpty() && !snapshot.metadata.isFromCache) {
                        stopSelf()
                    } else if (!escuchandoGps) {
                        iniciarGps()
                    }
                }
        }
        return START_NOT_STICKY
    }

    private fun tienePermiso(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun iniciarGps() {
        val solicitud = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 12_000L)
            .setMinUpdateIntervalMillis(6_000L)
            .build()
        try {
            escuchandoGps = true
            ubicacion.requestLocationUpdates(solicitud, escuchaGps, Looper.getMainLooper())
                .addOnSuccessListener {
                    if (destruido) ubicacion.removeLocationUpdates(escuchaGps)
                }
                .addOnFailureListener { error ->
                    escuchandoGps = false
                    Log.w("UbicacionRepartidor", "No se pudo iniciar el GPS", error)
                    stopSelf()
                }
        } catch (error: SecurityException) {
            escuchandoGps = false
            Log.w("UbicacionRepartidor", "Falta permiso de ubicación", error)
            stopSelf()
        }
    }

    private fun iniciarAvisoPermanente() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CANAL, "Ubicación durante entregas", NotificationManager.IMPORTANCE_LOW
            ))
        }
        val aviso = crearAviso("Seguimiento activo durante tus entregas")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_AVISO, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(ID_AVISO, aviso)
        }
    }

    private fun mostrarAviso(mensaje: String) {
        getSystemService(NotificationManager::class.java).notify(ID_AVISO, crearAviso(mensaje))
    }

    private fun crearAviso(mensaje: String): Notification {
        val abrirApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val constructor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CANAL)
        } else {
            Notification.Builder(this)
        }
        return constructor
            .setSmallIcon(R.drawable.ic_delivery_scooter)
            .setContentTitle("Yalazas · reparto en curso")
            .setContentText(mensaje)
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        destruido = true
        escuchaPedidos?.remove()
        escuchaPedidos = null
        if (escuchandoGps) ubicacion.removeLocationUpdates(escuchaGps)
        escuchandoGps = false
        super.onDestroy()
    }

    companion object {
        private const val CANAL = "yalazas_reparto_ubicacion"
        private const val ID_AVISO = 1201
    }
}
