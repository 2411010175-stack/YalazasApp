package com.yalazas.app.ui.tracking

import android.app.Dialog
import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.databinding.DialogoUbicacionRepartidorBinding
import com.yalazas.app.model.DistanciaSeguimiento
import com.yalazas.app.model.Order
import com.yalazas.app.model.UbicacionRepartidor
import com.yalazas.app.repositorio.RepositorioUbicacionRepartidor
import com.yalazas.app.repositorio.RepositorioRutaEstimada
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Locale

/** Mapa vivo del pedido abierto por el cliente desde Rastreo. */
class DialogoUbicacionRepartidor : DialogFragment() {
    private var _binding: DialogoUbicacionRepartidorBinding? = null
    private val binding get() = _binding!!
    private var escucha: ListenerRegistration? = null
    private var posicion: UbicacionRepartidor? = null
    private var focoInicial = false
    private var focoConRepartidor = false
    private var rutaEstimada: List<GeoPoint> = emptyList()
    private var origenUltimaRuta: GeoPoint? = null
    private var ultimaSolicitudRutaMs = 0L
    private var consultandoRuta = false
    private var errorRuta = false
    private val temporizador = Handler(Looper.getMainLooper())
    private val refrescarEdad = object : Runnable {
        override fun run() {
            if (_binding != null) {
                mostrarPosicion()
                temporizador.postDelayed(this, 30_000L)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogoUbicacionRepartidorBinding.inflate(LayoutInflater.from(requireContext()))
        val contexto = requireContext().applicationContext
        Configuration.getInstance().load(
            contexto, contexto.getSharedPreferences("yalazas_osm_prefs", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = "YalazasTrackingApp/1.0"
        binding.mapTrackingDriver.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }
        binding.tvMapDriverName.text = "Repartidor: ${requireArguments().getString(ARG_NOMBRE).orEmpty()}"
        binding.tvMapDestination.text = "📍 ${requireArguments().getString(ARG_DIRECCION).orEmpty()}"
        binding.btnCenterTrackingMap.setOnClickListener { centrarMapa() }
        mostrarPosicion()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton("Cerrar", null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT
        )
        escucha = RepositorioUbicacionRepartidor.observar(
            requireArguments().getString(ARG_PEDIDO).orEmpty(),
            alCambiar = {
                if (_binding == null) return@observar
                posicion = it
                mostrarPosicion()
            },
            alError = {
                if (_binding == null) return@observar
                binding.tvMapUpdate.text = "No se puede consultar la ubicación en este momento."
            }
        )
    }

    override fun onResume() {
        super.onResume()
        binding.mapTrackingDriver.onResume()
        temporizador.post(refrescarEdad)
    }

    override fun onPause() {
        temporizador.removeCallbacks(refrescarEdad)
        binding.mapTrackingDriver.onPause()
        super.onPause()
    }

    override fun onStop() {
        escucha?.remove()
        escucha = null
        super.onStop()
    }

    private fun mostrarPosicion() {
        if (_binding == null) return
        val destino = puntoDestino()
        val puntoRepartidor = posicion?.takeIf { it.esValida() }?.let { GeoPoint(it.latitude, it.longitude) }
        val reciente = posicion?.esReciente() == true
        consultarRutaSiCorresponde(puntoRepartidor, destino, reciente)

        binding.tvMapDistance.text = when {
            puntoRepartidor == null -> "Esperando la ubicación del repartidor"
            destino == null -> "Repartidor localizado · distancia no disponible"
            else -> {
                val metros = DistanciaSeguimiento.metros(
                    puntoRepartidor.latitude, puntoRepartidor.longitude,
                    destino.latitude, destino.longitude
                )
                "${if (reciente) "Aprox." else "Última distancia aprox."} ${DistanciaSeguimiento.formatear(metros)} de tu dirección"
            }
        }
        binding.tvMapUpdate.text = when {
            posicion == null -> "El mapa se actualizará cuando llegue la primera señal GPS."
            reciente -> {
                val hora = SimpleDateFormat("HH:mm", Locale("es", "PE"))
                    .format(posicion!!.updatedAt!!.toDate())
                "Ubicación reciente a las $hora · precisión aprox. ${posicion!!.accuracyMeters.toInt()} m"
            }
            else -> "Señal sin actualizar. Se muestra la última posición disponible."
        }
        binding.tvMapRouteStatus.text = when {
            destino == null -> "Ruta no disponible: la dirección no tiene coordenadas."
            puntoRepartidor == null -> "La ruta aparecerá cuando llegue el GPS del repartidor."
            consultandoRuta && rutaEstimada.isEmpty() -> "Calculando ruta estimada por calles..."
            rutaEstimada.isNotEmpty() && !reciente -> "Última ruta estimada · esperando señal reciente."
            rutaEstimada.isNotEmpty() && errorRuta -> "Última ruta estimada · no se pudo actualizar."
            rutaEstimada.isNotEmpty() -> "Ruta estimada por calles · puede cambiar durante el reparto."
            errorRuta -> "No se pudo calcular la ruta por calles en este momento."
            else -> "Preparando ruta estimada..."
        }

        val mapa = binding.mapTrackingDriver
        mapa.overlays.removeAll { it is Marker || it is Polyline }
        if (rutaEstimada.isNotEmpty()) {
            mapa.overlays.add(Polyline(mapa).apply {
                setPoints(rutaEstimada)
                outlinePaint.color = ContextCompat.getColor(requireContext(), R.color.yalaza_pink)
                outlinePaint.strokeWidth = 8f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.isAntiAlias = true
                title = "Ruta estimada hasta tu dirección"
            })
        }
        if (destino != null) {
            mapa.overlays.add(Marker(mapa).apply {
                position = destino
                title = "Tu dirección de entrega"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delivery_pin_customer)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
        }
        if (puntoRepartidor != null) {
            mapa.overlays.add(Marker(mapa).apply {
                position = puntoRepartidor
                title = requireArguments().getString(ARG_NOMBRE).orEmpty()
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_tracking_driver_pin)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
        }
        mapa.invalidate()
        if (puntoRepartidor != null && !focoConRepartidor) {
            centrarMapa()
            focoConRepartidor = true
        } else if (destino != null && !focoInicial) {
            centrarMapa()
            focoInicial = true
        }
    }

    /** La posición del marcador cambia en vivo; la ruta se consulta como máximo cada minuto. */
    private fun consultarRutaSiCorresponde(origen: GeoPoint?, destino: GeoPoint?, reciente: Boolean) {
        if (origen == null || destino == null || !reciente || consultandoRuta) return
        val ahora = SystemClock.elapsedRealtime()
        if (ultimaSolicitudRutaMs != 0L && ahora - ultimaSolicitudRutaMs < 60_000L) return
        if (rutaEstimada.isNotEmpty() && origenUltimaRuta?.distanceToAsDouble(origen)?.let { it < 50.0 } == true) return

        ultimaSolicitudRutaMs = ahora
        origenUltimaRuta = origen
        consultandoRuta = true
        errorRuta = false
        RepositorioRutaEstimada.consultar(origen, destino) { resultado ->
            if (_binding == null) return@consultar
            consultandoRuta = false
            if (resultado != null) {
                rutaEstimada = resultado
                errorRuta = false
            } else {
                errorRuta = true
            }
            mostrarPosicion()
        }
    }

    private fun puntoDestino(): GeoPoint? {
        val latitud = requireArguments().getDouble(ARG_LATITUD)
        val longitud = requireArguments().getDouble(ARG_LONGITUD)
        return if (latitud in -90.0..90.0 && longitud in -180.0..180.0 &&
            (latitud != 0.0 || longitud != 0.0)
        ) GeoPoint(latitud, longitud) else null
    }

    private fun centrarMapa() {
        if (_binding == null) return
        val mapa = binding.mapTrackingDriver
        val puntos = rutaEstimada.ifEmpty {
            listOfNotNull(
                puntoDestino(), posicion?.takeIf { it.esValida() }?.let { GeoPoint(it.latitude, it.longitude) }
            )
        }
        mapa.post {
            if (_binding?.mapTrackingDriver != mapa || puntos.isEmpty()) return@post
            if (puntos.size >= 2 && puntos.first().distanceToAsDouble(puntos.last()) > 100) {
                mapa.zoomToBoundingBox(BoundingBox.fromGeoPoints(puntos), true, 80)
            } else {
                mapa.controller.setZoom(17.0)
                mapa.controller.setCenter(puntos.last())
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_PEDIDO = "pedido"
        private const val ARG_NOMBRE = "nombre"
        private const val ARG_DIRECCION = "direccion"
        private const val ARG_LATITUD = "latitud"
        private const val ARG_LONGITUD = "longitud"

        fun etiqueta(pedidoId: String) = "ubicacion_$pedidoId"

        fun mostrar(fragmentManager: FragmentManager, pedido: Order) {
            if (fragmentManager.findFragmentByTag(etiqueta(pedido.id)) != null) return
            DialogoUbicacionRepartidor().apply {
                arguments = Bundle().apply {
                    putString(ARG_PEDIDO, pedido.id)
                    putString(ARG_NOMBRE, pedido.deliveryDriverName.ifBlank { "Repartidor Yalazas" })
                    putString(ARG_DIRECCION, pedido.address.ifBlank { "Dirección de entrega" })
                    putDouble(ARG_LATITUD, pedido.latitude)
                    putDouble(ARG_LONGITUD, pedido.longitude)
                }
            }.show(fragmentManager, etiqueta(pedido.id))
        }
    }
}
