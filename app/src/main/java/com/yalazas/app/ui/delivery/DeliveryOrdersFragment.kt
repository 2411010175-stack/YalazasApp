package com.yalazas.app.ui.delivery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentDeliveryOrdersBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioEstadosPedido
import com.yalazas.app.repositorio.ParadaPedido
import com.yalazas.app.repositorio.ServiciosFirebase
import com.yalazas.app.ui.dialogos.DialogoTomarVariosPedidos
import com.yalazas.app.ui.dialogos.DialogoMarcarEntregado
import com.yalazas.app.ui.dialogos.DialogoTomarPedido
import com.yalazas.app.utilidades.OptimizadorRutasEntrega

/** Escucha pedidos y permite tomarlos o marcarlos como entregados. */
class DeliveryOrdersFragment : Fragment(R.layout.fragment_delivery_orders) {

    private var _binding: FragmentDeliveryOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AdaptadorPedidosReparto
    private var firestoreListener: ListenerRegistration? = null

    private var allOrders: List<Order> = emptyList()
    private var pedidosCargados = false
    private var currentTab: Int = 0 // 0 = Por Recoger, 1 = En Ruta
    private var permisoSolicitado = false
    private val prefs by lazy {
        requireContext().getSharedPreferences("delivery_prefs", Context.MODE_PRIVATE)
    }

    private fun disponible(): Boolean = prefs.getBoolean("driver_available", true)

    override fun onResume() {
        super.onResume()
        if (_binding != null && ::adapter.isInitialized) {
            actualizarEstadoCabecera()
            if (pedidosCargados) filtrarYMostrarPedidos()
        }
    }

    private val solicitarUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        if (permisos.values.any { it }) {
            iniciarServicioUbicacion()
        } else if (isAdded) {
            _binding?.tvDeliveryDriverStatus?.text = "🟠 Ubicación desactivada para tus clientes"
            Toast.makeText(requireContext(), "Activa la ubicación para que tus clientes puedan seguir el reparto.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryOrdersBinding.bind(view)

        setupUI()
        configurarLista()
        configurarEventos()
        escucharPedidosDeFirestore()
    }

    private fun setupUI() {
        val driverName = RepositorioAutenticacion.currentProfile?.name?.ifBlank { "Repartidor" } ?: "Repartidor"
        binding.tvDeliveryDriverGreeting.text = "¡Hola $driverName! 👋"
        actualizarEstadoCabecera()
    }

    private fun actualizarEstadoCabecera() {
        if (_binding == null) return
        val uid = ServiciosFirebase.auth.currentUser?.uid.orEmpty()
        val enRuta = allOrders.any {
            it.status == OrderStatus.EN_CAMINO.name && it.deliveryDriverId == uid
        }
        binding.tvDeliveryDriverStatus.text = when {
            enRuta -> "🛵 En ruta · Entregas en curso"
            disponible() -> "🟢 Disponible para entregas"
            else -> "🟠 En descanso · No tomas pedidos"
        }
        binding.tvDeliveryDriverStatus.setTextColor(
            ContextCompat.getColor(requireContext(), if (disponible() || enRuta)
                R.color.whatsapp_green else R.color.yalaza_orange)
        )
    }

    private fun configurarLista() {
        adapter = AdaptadorPedidosReparto(
            onTakeOrderClick = { order ->
                confirmTakeOrder(order)
            },
            onMarkDeliveredClick = { order ->
                confirmMarkDelivered(order)
            },
            onSelectionChanged = { count, selectedOrders ->
                updateBatchSelectionUI(count, selectedOrders)
            }
        )
        binding.rvDeliveryOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDeliveryOrders.adapter = adapter
    }

    private fun configurarEventos() {
        binding.swipeRefreshDelivery.setOnRefreshListener {
            binding.swipeRefreshDelivery.isRefreshing = false
            escucharPedidosDeFirestore()
        }

        binding.btnDeliveryRefresh.setOnClickListener {
            binding.progressDelivery.visibility = View.VISIBLE
            escucharPedidosDeFirestore()
        }

        binding.tabDeliveryAvailable.setOnClickListener {
            switchTab(0)
        }

        binding.tabDeliveryInRoute.setOnClickListener {
            switchTab(1)
        }

        binding.cardMetricAvailable.setOnClickListener {
            switchTab(0)
        }

        binding.cardMetricInRoute.setOnClickListener {
            switchTab(1)
        }

        binding.btnBatchOptimizeAndTake.setOnClickListener {
            val selected = adapter.getSelectedOrders()
            if (selected.isNotEmpty()) {
                confirmBatchTakeOrders(selected)
            }
        }
    }

    private fun switchTab(tabIndex: Int) {
        currentTab = tabIndex
        adapter.clearSelection()
        binding.cardBatchAction.visibility = View.GONE

        if (tabIndex == 0) {
            binding.tabDeliveryAvailable.setBackgroundResource(R.drawable.bg_delivery_tab_selected)
            binding.tabDeliveryAvailable.setTextColor(requireContext().getColor(R.color.yalaza_primary_green))

            binding.tabDeliveryInRoute.setBackgroundResource(R.drawable.bg_delivery_tab_unselected)
            binding.tabDeliveryInRoute.setTextColor(0xFF8AA394.toInt())
        } else {
            binding.tabDeliveryAvailable.setBackgroundResource(R.drawable.bg_delivery_tab_unselected)
            binding.tabDeliveryAvailable.setTextColor(0xFF8AA394.toInt())

            binding.tabDeliveryInRoute.setBackgroundResource(R.drawable.bg_delivery_tab_selected)
            binding.tabDeliveryInRoute.setTextColor(requireContext().getColor(R.color.yalaza_primary_green))
        }
        filtrarYMostrarPedidos()
    }

    private fun updateBatchSelectionUI(count: Int, selectedOrders: List<Order>) {
        if (currentTab == 0 && count > 0) {
            binding.cardBatchAction.visibility = View.VISIBLE
            val label = if (count == 1) "1 pedido seleccionado" else "$count pedidos seleccionados"
            binding.tvBatchSelectedCount.text = label
        } else {
            binding.cardBatchAction.visibility = View.GONE
        }
    }

    /** Mantiene la lista de pedidos a domicilio sincronizada con Firestore. */
    private fun escucharPedidosDeFirestore() {
        firestoreListener?.remove()
        binding.progressDelivery.visibility = View.VISIBLE

        val db = ServiciosFirebase.firestore
        firestoreListener = db.collection("orders")
            .whereEqualTo("isDelivery", true)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded || _binding == null) return@addSnapshotListener
                binding.progressDelivery.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(requireContext(), "Error al cargar pedidos: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // Excluimos pedidos de recojo en local (isDelivery == false)
                    allOrders = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Order::class.java)?.copy(id = doc.id)
                    }.filter { it.isDelivery } // Solo pedidos para delivery a domicilio
                     .sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }

                    val uid = ServiciosFirebase.auth.currentUser?.uid.orEmpty()
                    if (!disponible() && allOrders.any {
                            it.status == OrderStatus.EN_CAMINO.name && it.deliveryDriverId == uid
                        }) {
                        prefs.edit().putBoolean("driver_available", true).apply()
                    }
                    pedidosCargados = true

                    actualizarMetricasYPestanas()
                    filtrarYMostrarPedidos()
                    verificarSeguimientoUbicacion()
                }
            }
    }

    /** El servicio permanece activo al abrir otra app mientras haya pedidos en ruta. */
    private fun verificarSeguimientoUbicacion() {
        val uid = ServiciosFirebase.auth.currentUser?.uid.orEmpty()
        val hayEntrega = allOrders.any {
            it.status == OrderStatus.EN_CAMINO.name && it.deliveryDriverId == uid
        }
        if (!hayEntrega) {
            requireContext().stopService(Intent(requireContext(), ServicioUbicacionRepartidor::class.java))
            actualizarEstadoCabecera()
            return
        }
        actualizarEstadoCabecera()
        solicitarOIniciarSeguimiento()
    }

    private fun solicitarOIniciarSeguimiento() {
        val tienePermiso = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
        if (tienePermiso) {
            iniciarServicioUbicacion()
        } else if (!permisoSolicitado) {
            permisoSolicitado = true
            binding.tvDeliveryDriverStatus.text = "🟠 Activa ubicación para compartir tu ruta"
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ubicación durante el reparto")
                .setMessage("El cliente podrá ver tu posición mientras llevas su pedido. El seguimiento termina al completar tus entregas o cerrar sesión.")
                .setPositiveButton("Compartir ubicación") { _, _ ->
                    solicitarUbicacion.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
                .setNegativeButton("Ahora no", null)
                .show()
        }
    }

    private fun iniciarServicioUbicacion() {
        if (!isAdded || _binding == null) return
        try {
            ContextCompat.startForegroundService(
                requireContext(), Intent(requireContext(), ServicioUbicacionRepartidor::class.java)
            )
            binding.tvDeliveryDriverStatus.text = "🟢 En ruta • seguimiento habilitado"
        } catch (error: RuntimeException) {
            binding.tvDeliveryDriverStatus.text = "🟠 No se pudo activar la ubicación"
            Toast.makeText(requireContext(), "No se pudo iniciar la ubicación del reparto.", Toast.LENGTH_LONG).show()
        }
    }

    /** Cuenta los pedidos disponibles y los que ya están en ruta. */
    private fun actualizarMetricasYPestanas() {
        val currentUid = ServiciosFirebase.auth.currentUser?.uid ?: ""

        val availableCount = allOrders.count {
            it.status == OrderStatus.LISTO_PARA_ENTREGA.name && it.deliveryDriverId.isNullOrEmpty()
        }

        val inRouteCount = allOrders.count {
            it.status == OrderStatus.EN_CAMINO.name &&
                    it.deliveryDriverId == currentUid
        }

        binding.tvMetricAvailableCount.text = availableCount.toString()
        binding.tvMetricInRouteCount.text = inRouteCount.toString()

        binding.tabDeliveryAvailable.text = "Por Recoger ($availableCount) 📦"
        binding.tabDeliveryInRoute.text = "En Ruta ($inRouteCount) 🛵"
    }

    /** Muestra la pestaña elegida y respeta el orden de las paradas asignadas. */
    private fun filtrarYMostrarPedidos() {
        val currentUid = ServiciosFirebase.auth.currentUser?.uid ?: ""

        val filteredList = if (currentTab == 0) {
            // Disponibles para recoger en cocina
            allOrders.filter {
                it.status == OrderStatus.LISTO_PARA_ENTREGA.name && it.deliveryDriverId.isNullOrEmpty()
            }
        } else {
            // Pedidos en camino del repartidor actual, ordenados por secuencia optimizada (routeOrderIndex)
            allOrders.filter {
                it.status == OrderStatus.EN_CAMINO.name &&
                        it.deliveryDriverId == currentUid
            }.sortedWith(
                compareBy<Order> { if (it.routeOrderIndex > 0) it.routeOrderIndex else Int.MAX_VALUE }
                    .thenBy { it.timestamp?.toDate()?.time ?: 0L }
            )
        }

        val estaDisponible = disponible()
        adapter.updateOrders(filteredList, enableSelection = (currentTab == 0), disponible = estaDisponible)
        if (!estaDisponible) binding.cardBatchAction.visibility = View.GONE
        actualizarEstadoCabecera()

        if (filteredList.isEmpty()) {
            binding.layoutDeliveryEmptyState.visibility = View.VISIBLE
            binding.rvDeliveryOrders.visibility = View.GONE

            if (currentTab == 0) {
                binding.tvEmptyIcon.text = "🍗✨"
                binding.tvEmptyTitle.text = "¡Sin pedidos por recoger!"
                binding.tvEmptySubtitle.text = "Todos los pedidos para delivery están atendidos. Te avisaremos cuando cocina tenga más listos."
            } else {
                binding.tvEmptyIcon.text = "🛵🏁"
                binding.tvEmptyTitle.text = "No tienes entregas en curso"
                binding.tvEmptySubtitle.text = "Selecciona uno o más pedidos de la pestaña 'Por Recoger' para iniciar tu ruta de entrega."
            }
        } else {
            binding.layoutDeliveryEmptyState.visibility = View.GONE
            binding.rvDeliveryOrders.visibility = View.VISIBLE
        }
    }

    private fun confirmTakeOrder(order: Order) {
        if (!comprobarDisponibilidad()) return
        DialogoTomarPedido.crear(order) {
            takeSingleOrder(order)
        }.show(parentFragmentManager, DialogoTomarPedido.TAG)
    }

    /** Asigna el pedido al repartidor actual y lo pasa a EN_CAMINO. */
    private fun takeSingleOrder(order: Order) {
        if (!comprobarDisponibilidad()) return
        RepositorioEstadosPedido.tomarParaReparto(listOf(ParadaPedido(order.id, 1))) { resultado ->
            if (!isAdded || _binding == null) return@tomarParaReparto
            resultado.onSuccess {
                    Toast.makeText(requireContext(), "🛵 ¡Pedido tomado con éxito! Buen viaje.", Toast.LENGTH_SHORT).show()
                    switchTab(1)
                    solicitarOIniciarSeguimiento()
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Error al tomar pedido: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmBatchTakeOrders(ordersToTake: List<Order>) {
        if (!comprobarDisponibilidad()) return
        DialogoTomarVariosPedidos.crear(ordersToTake) {
            executeBatchRouteOptimization(ordersToTake)
        }.show(parentFragmentManager, DialogoTomarVariosPedidos.TAG)
    }

    /** Calcula la secuencia y asigna los pedidos en una sola operación de Firestore. */
    private fun executeBatchRouteOptimization(ordersToTake: List<Order>) {
        if (!comprobarDisponibilidad()) return
        binding.progressDelivery.visibility = View.VISIBLE
        // La optimización solo decide la secuencia; State valida todos los pedidos antes de asignarlos.
        val sequencedOrders = OptimizadorRutasEntrega.optimizarSecuenciaEntregas(ordersToTake)
        val paradas = sequencedOrders.map { ParadaPedido(it.id, it.routeOrderIndex) }
        RepositorioEstadosPedido.tomarParaReparto(paradas) { resultado ->
            if (!isAdded || _binding == null) return@tomarParaReparto
            binding.progressDelivery.visibility = View.GONE
            resultado.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "🚀 ¡Ruta de ${sequencedOrders.size} entregas optimizada con éxito!",
                        Toast.LENGTH_LONG
                    ).show()
                    switchTab(1) // Ir a Mis Entregas en Ruta
                    solicitarOIniciarSeguimiento()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Error al optimizar ruta: ${error.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun comprobarDisponibilidad(): Boolean {
        if (disponible()) return true
        Toast.makeText(requireContext(), "Activa tu disponibilidad en Perfil para tomar pedidos", Toast.LENGTH_LONG).show()
        actualizarEstadoCabecera()
        return false
    }

    private fun confirmMarkDelivered(order: Order) {
        DialogoMarcarEntregado.crear(order) {
            markOrderDelivered(order)
        }.show(parentFragmentManager, DialogoMarcarEntregado.TAG)
    }

    /** Marca la entrega y el pago en el documento del pedido. */
    private fun markOrderDelivered(order: Order) {
        RepositorioEstadosPedido.marcarEntregado(order.id) { resultado ->
            if (!isAdded || _binding == null) return@marcarEntregado
            resultado.onSuccess {
                    Toast.makeText(requireContext(), "🎉 ¡Pedido entregado con éxito! Registrado en tu historial.", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Error al actualizar estado: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
        firestoreListener = null
        _binding = null
    }
}
