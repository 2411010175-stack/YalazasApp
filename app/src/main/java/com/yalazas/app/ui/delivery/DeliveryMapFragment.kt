package com.yalazas.app.ui.delivery

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentDeliveryMapBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.utils.DeliveryPricingHelper
import com.yalazas.app.utils.DeliveryRouteOptimizer
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class DeliveryMapFragment : Fragment(R.layout.fragment_delivery_map) {

    private var _binding: FragmentDeliveryMapBinding? = null
    private val binding get() = _binding!!

    private var firestoreListener: ListenerRegistration? = null
    private var activeOrders: List<Order> = emptyList()
    private var selectedOrder: Order? = null

    // Ubicación Central de Yalazas Fast Food (Imperial, Cañete, Perú)
    private val storeGeoPoint = GeoPoint(DeliveryPricingHelper.STORE_LATITUDE, DeliveryPricingHelper.STORE_LONGITUDE)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryMapBinding.bind(view)

        setupOpenStreetMap()
        setupListeners()
        listenActiveDeliveries()
    }

    private fun setupOpenStreetMap() {
        val ctx = requireContext().applicationContext
        val osmPrefs = ctx.getSharedPreferences("yalazas_osm_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(ctx, osmPrefs)
        Configuration.getInstance().userAgentValue = "YalazasDeliveryApp/1.0"

        binding.deliveryMapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(storeGeoPoint)
        }

        // Tap en el mapa para deseleccionar pedido
        binding.deliveryMapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: org.osmdroid.views.MapView?): Boolean {
                hideOrderPreview()
                return false
            }
        })
    }

    private fun setupListeners() {
        binding.fabCenterStore.setOnClickListener {
            binding.deliveryMapView.controller.animateTo(storeGeoPoint, 16.5, 1000L)
        }

        binding.fabZoomIn.setOnClickListener {
            binding.deliveryMapView.controller.zoomIn()
        }

        binding.fabZoomOut.setOnClickListener {
            binding.deliveryMapView.controller.zoomOut()
        }

        binding.btnMapRefresh.setOnClickListener {
            listenActiveDeliveries()
            Toast.makeText(requireContext(), "Actualizando mapa...", Toast.LENGTH_SHORT).show()
        }

        binding.btnPreviewCall.setOnClickListener {
            selectedOrder?.let { order ->
                if (order.phone.isNotBlank()) {
                    val dialIntent = Intent(Intent.ACTION_DIAL, "tel:${order.phone}".toUri())
                    startActivity(dialIntent)
                } else {
                    Toast.makeText(requireContext(), "No hay teléfono registrado para este cliente", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnPreviewWhatsApp.setOnClickListener {
            selectedOrder?.let { order ->
                if (order.phone.isNotBlank()) {
                    val customerName = if (order.customerName.isNotBlank()) order.customerName else "Cliente Yalazas"
                    val numStr = "Pedido #${order.id.takeLast(6).uppercase()}"
                    openWhatsApp(requireContext(), order.phone, "¡Hola $customerName! Soy el repartidor de Yalazas. Estoy en camino con tu pedido ($numStr) 🛵🍗")
                } else {
                    Toast.makeText(requireContext(), "No hay teléfono registrado para este cliente", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnPreviewGps.setOnClickListener {
            selectedOrder?.let { order ->
                openGpsNavigation(requireContext(), order)
            }
        }

        binding.btnPreviewGoToOrders.setOnClickListener {
            try {
                findNavController().navigate(R.id.nav_delivery_orders)
            } catch (_: Exception) {
                // Si la navegación ya está en la misma pantalla
            }
        }
    }

    private fun listenActiveDeliveries() {
        firestoreListener?.remove()
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val db = FirebaseFirestore.getInstance()
        firestoreListener = db.collection("orders")
            .addSnapshotListener { snapshot, error ->
                if (!isAdded || _binding == null) return@addSnapshotListener

                if (error != null) {
                    Toast.makeText(requireContext(), "Error al cargar rutas: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val all = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Order::class.java)?.copy(id = doc.id)
                    }

                    // Filtrar solo entregas a domicilio (excluir recojo en tienda)
                    // Mostrar pedidos listos en cocina (por recoger) o en camino del repartidor actual
                    activeOrders = all.filter { order ->
                        order.isDelivery && (
                            (order.status == OrderStatus.EN_PROCESO.name && order.deliveryDriverId.isNullOrEmpty()) ||
                            (order.status == OrderStatus.EN_CAMINO.name && (order.deliveryDriverId == currentUid || order.deliveryDriverId.isNullOrEmpty()))
                        )
                    }

                    updateMapOverlays()
                }
            }
    }

    private fun updateMapOverlays() {
        if (_binding == null) return

        // Limpiar marcadores y polilíneas anteriores
        binding.deliveryMapView.overlays.removeAll { it is Marker || it is Polyline }

        // 1. Agregar Pin del Local Central Yalazas
        val storeMarker = Marker(binding.deliveryMapView).apply {
            position = storeGeoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "🍗 Yalazas Fast Food"
            snippet = "Local Central • Imperial, Cañete"
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delivery_pin_yalaza)
            setOnMarkerClickListener { _, _ ->
                hideOrderPreview()
                showInfoWindow()
                true
            }
        }
        binding.deliveryMapView.overlays.add(storeMarker)

        // 2. Separar pedidos en ruta (con orden optimizado) vs pedidos por recoger
        val inRouteOrders = activeOrders.filter {
            it.status == OrderStatus.EN_CAMINO.name && it.latitude != 0.0 && it.longitude != 0.0
        }.sortedWith(
            compareBy<Order> { if (it.routeOrderIndex > 0) it.routeOrderIndex else Int.MAX_VALUE }
                .thenBy { it.timestamp?.toDate()?.time ?: 0L }
        )

        val pendingPickupOrders = activeOrders.filter {
            it.status == OrderStatus.EN_PROCESO.name && it.latitude != 0.0 && it.longitude != 0.0
        }

        // 3. Trazar Polilínea de la Ruta Optimizada (Local -> Parada 1 -> Parada 2 -> ...)
        if (inRouteOrders.isNotEmpty()) {
            val routeGeoPoints = mutableListOf<GeoPoint>()
            routeGeoPoints.add(storeGeoPoint)
            inRouteOrders.forEach { order ->
                routeGeoPoints.add(GeoPoint(order.latitude, order.longitude))
            }

            val routePolyline = Polyline(binding.deliveryMapView).apply {
                setPoints(routeGeoPoints)
                outlinePaint.color = Color.parseColor("#00E676")
                outlinePaint.strokeWidth = 10f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                title = "Ruta Optimizada Yalazas (${inRouteOrders.size} entregas)"
            }
            binding.deliveryMapView.overlays.add(routePolyline)
        }

        // 4. Agregar Pines de Clientes
        var validGpsCount = 0

        // Clientes En Ruta
        inRouteOrders.forEach { order ->
            validGpsCount++
            val customerPoint = GeoPoint(order.latitude, order.longitude)
            val stopText = if (order.routeOrderIndex > 0) DeliveryRouteOptimizer.getStopBadgeText(order.routeOrderIndex) else "En Ruta"
            val customerMarker = Marker(binding.deliveryMapView).apply {
                position = customerPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val numStr = "Pedido #${order.id.takeLast(6).uppercase()}"
                title = "$stopText • $numStr"
                snippet = "${order.customerName} • ${order.address}"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delivery_pin_customer)

                setOnMarkerClickListener { _, _ ->
                    showOrderPreview(order)
                    binding.deliveryMapView.controller.animateTo(customerPoint)
                    true
                }
            }
            binding.deliveryMapView.overlays.add(customerMarker)
        }

        // Clientes Por Recoger (Listos en cocina)
        pendingPickupOrders.forEach { order ->
            validGpsCount++
            val customerPoint = GeoPoint(order.latitude, order.longitude)
            val customerMarker = Marker(binding.deliveryMapView).apply {
                position = customerPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val numStr = "Pedido #${order.id.takeLast(6).uppercase()}"
                title = "📦 Listo por recoger • $numStr"
                snippet = "${order.customerName} • ${order.address}"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delivery_pin_customer)

                setOnMarkerClickListener { _, _ ->
                    showOrderPreview(order)
                    binding.deliveryMapView.controller.animateTo(customerPoint)
                    true
                }
            }
            binding.deliveryMapView.overlays.add(customerMarker)
        }

        // Actualizar contador del Header
        if (inRouteOrders.isNotEmpty()) {
            binding.tvMapActiveDeliveriesCount.text = "🛵 ${inRouteOrders.size} entregas en ruta optimizada (${pendingPickupOrders.size} por recoger)"
        } else if (activeOrders.isNotEmpty()) {
            binding.tvMapActiveDeliveriesCount.text = "📦 ${activeOrders.size} pedidos disponibles en cocina"
        } else {
            binding.tvMapActiveDeliveriesCount.text = "🏁 Sin entregas pendientes"
        }

        binding.deliveryMapView.invalidate()
    }

    private fun showOrderPreview(order: Order) {
        selectedOrder = order
        val numStr = "Pedido #${order.id.takeLast(6).uppercase()}"
        binding.tvPreviewOrderNumber.text = numStr
        binding.tvPreviewOrderTotal.text = "S/ %.2f".format(Locale.getDefault(), order.total)

        if (order.status == OrderStatus.EN_CAMINO.name && order.routeOrderIndex > 0) {
            binding.tvPreviewStopBadge.visibility = View.VISIBLE
            binding.tvPreviewStopBadge.text = DeliveryRouteOptimizer.getStopBadgeText(order.routeOrderIndex)
        } else if (order.status == OrderStatus.EN_PROCESO.name) {
            binding.tvPreviewStopBadge.visibility = View.VISIBLE
            binding.tvPreviewStopBadge.text = "📦 Por Recoger"
        } else {
            binding.tvPreviewStopBadge.visibility = View.GONE
        }

        val customerName = if (order.customerName.isNotBlank()) order.customerName else "Cliente Yalazas"
        val customerPhone = if (order.phone.isNotBlank()) order.phone else "Sin tel."
        binding.tvPreviewCustomer.text = "👤 $customerName • 📞 $customerPhone"

        val addressText = if (order.address.isNotBlank()) order.address else "Dirección no registrada"
        val refText = if (order.reference.isNotBlank()) " (Ref: ${order.reference})" else ""
        binding.tvPreviewAddress.text = "📍 $addressText$refText"

        val distKm = if (order.deliveryDistanceKm > 0) {
            order.deliveryDistanceKm
        } else if (order.latitude != 0.0 && order.longitude != 0.0) {
            DeliveryPricingHelper.calculateDistanceKm(lat2 = order.latitude, lon2 = order.longitude)
        } else {
            0.0
        }
        val fee = if (order.deliveryFee > 0) order.deliveryFee else DeliveryPricingHelper.calculateDeliveryFee(distKm, order.isDelivery)
        if (distKm > 0) {
            val distStr = DeliveryPricingHelper.formatDistance(distKm)
            binding.tvPreviewDistanceFee.text = "📏 $distStr • Costo Envío: S/ %.2f".format(Locale.getDefault(), fee)
            binding.tvPreviewDistanceFee.visibility = View.VISIBLE
        } else {
            binding.tvPreviewDistanceFee.text = "🛵 Costo Envío: S/ %.2f".format(Locale.getDefault(), fee)
            binding.tvPreviewDistanceFee.visibility = View.VISIBLE
        }

        binding.cardSelectedOrderPreview.visibility = View.VISIBLE
    }

    private fun hideOrderPreview() {
        selectedOrder = null
        binding.cardSelectedOrderPreview.visibility = View.GONE
    }

    private fun openWhatsApp(context: Context, phone: String, message: String) {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        val internationalPhone = if (cleanPhone.startsWith("51")) cleanPhone else "51$cleanPhone"
        val url = "https://wa.me/$internationalPhone?text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGpsNavigation(context: Context, order: Order) {
        if (order.latitude != 0.0 && order.longitude != 0.0) {
            val label = Uri.encode("Cliente: ${order.customerName.ifBlank { "Entrega Yalazas" }}")
            val gmmIntentUri = "google.navigation:q=${order.latitude},${order.longitude}".toUri()
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            try {
                context.startActivity(mapIntent)
            } catch (_: Exception) {
                val geoUri = "geo:${order.latitude},${order.longitude}?q=${order.latitude},${order.longitude}($label)".toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
            }
        } else if (order.address.isNotBlank()) {
            val query = Uri.encode("${order.address}, Imperial, Cañete, Perú")
            val gmmIntentUri = "google.navigation:q=$query".toUri()
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            try {
                context.startActivity(mapIntent)
            } catch (_: Exception) {
                val webUri = "https://www.google.com/maps/search/?api=1&query=$query".toUri()
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            }
        } else {
            Toast.makeText(context, "No hay ubicación ni dirección registrada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.deliveryMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.deliveryMapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
        firestoreListener = null
        _binding = null
    }
}
