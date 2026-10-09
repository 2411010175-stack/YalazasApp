package com.yalazas.app.ui.delivery

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentDeliveryOrdersBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repository.AuthRepository
import com.yalazas.app.ui.dialogs.BatchTakeConfirmationDialogFragment
import com.yalazas.app.ui.dialogs.MarkDeliveredConfirmationDialogFragment
import com.yalazas.app.ui.dialogs.TakeOrderConfirmationDialogFragment
import com.yalazas.app.utils.DeliveryRouteOptimizer
import java.util.Locale

class DeliveryOrdersFragment : Fragment(R.layout.fragment_delivery_orders) {

    private var _binding: FragmentDeliveryOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeliveryOrdersAdapter
    private var firestoreListener: ListenerRegistration? = null

    private var allOrders: List<Order> = emptyList()
    private var currentTab: Int = 0 // 0 = Por Recoger, 1 = En Ruta

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryOrdersBinding.bind(view)

        setupUI()
        setupRecyclerView()
        setupListeners()
        listenOrdersFromFirestore()
    }

    private fun setupUI() {
        val driverName = AuthRepository.currentProfile?.name?.ifBlank { "Repartidor" } ?: "Repartidor"
        binding.tvDeliveryDriverGreeting.text = "¡Hola $driverName! 👋"
        binding.tvDeliveryDriverStatus.text = "🟢 En Turno • Repartidor Oficial"
    }

    private fun setupRecyclerView() {
        adapter = DeliveryOrdersAdapter(
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

    private fun setupListeners() {
        binding.swipeRefreshDelivery.setOnRefreshListener {
            binding.swipeRefreshDelivery.isRefreshing = false
            listenOrdersFromFirestore()
        }

        binding.btnDeliveryRefresh.setOnClickListener {
            binding.progressDelivery.visibility = View.VISIBLE
            listenOrdersFromFirestore()
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
        filterAndDisplayOrders()
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

    private fun listenOrdersFromFirestore() {
        firestoreListener?.remove()
        binding.progressDelivery.visibility = View.VISIBLE

        val db = FirebaseFirestore.getInstance()
        firestoreListener = db.collection("orders")
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

                    updateMetricsAndTabs()
                    filterAndDisplayOrders()
                }
            }
    }

    private fun updateMetricsAndTabs() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val availableCount = allOrders.count {
            it.status == OrderStatus.EN_PROCESO.name && it.deliveryDriverId.isNullOrEmpty()
        }

        val inRouteCount = allOrders.count {
            it.status == OrderStatus.EN_CAMINO.name &&
                    (it.deliveryDriverId == currentUid || it.deliveryDriverId.isNullOrEmpty())
        }

        binding.tvMetricAvailableCount.text = availableCount.toString()
        binding.tvMetricInRouteCount.text = inRouteCount.toString()

        binding.tabDeliveryAvailable.text = "Por Recoger ($availableCount) 📦"
        binding.tabDeliveryInRoute.text = "En Ruta ($inRouteCount) 🛵"
    }

    private fun filterAndDisplayOrders() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val filteredList = if (currentTab == 0) {
            // Disponibles para recoger en cocina
            allOrders.filter {
                it.status == OrderStatus.EN_PROCESO.name && it.deliveryDriverId.isNullOrEmpty()
            }
        } else {
            // Pedidos en camino del repartidor actual, ordenados por secuencia optimizada (routeOrderIndex)
            allOrders.filter {
                it.status == OrderStatus.EN_CAMINO.name &&
                        (it.deliveryDriverId == currentUid || it.deliveryDriverId.isNullOrEmpty())
            }.sortedWith(
                compareBy<Order> { if (it.routeOrderIndex > 0) it.routeOrderIndex else Int.MAX_VALUE }
                    .thenBy { it.timestamp?.toDate()?.time ?: 0L }
            )
        }

        adapter.updateOrders(filteredList, enableSelection = (currentTab == 0))

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
        TakeOrderConfirmationDialogFragment.newInstance(order) {
            takeSingleOrder(order)
        }.show(parentFragmentManager, TakeOrderConfirmationDialogFragment.TAG)
    }

    private fun takeSingleOrder(order: Order) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val driverName = AuthRepository.currentProfile?.name ?: "Repartidor Yalazas"
        val driverPhone = AuthRepository.currentProfile?.phone ?: ""

        val db = FirebaseFirestore.getInstance()
        val updates = mapOf(
            "status" to OrderStatus.EN_CAMINO.name,
            "deliveryDriverId" to currentUid,
            "deliveryDriverName" to driverName,
            "deliveryDriverPhone" to driverPhone,
            "routeOrderIndex" to 1
        )

        db.collection("orders").document(order.id)
            .update(updates)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "🛵 ¡Pedido tomado con éxito! Buen viaje.", Toast.LENGTH_SHORT).show()
                    switchTab(1)
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error al tomar pedido: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun confirmBatchTakeOrders(ordersToTake: List<Order>) {
        BatchTakeConfirmationDialogFragment.newInstance(ordersToTake) {
            executeBatchRouteOptimization(ordersToTake)
        }.show(parentFragmentManager, BatchTakeConfirmationDialogFragment.TAG)
    }

    private fun executeBatchRouteOptimization(ordersToTake: List<Order>) {
        binding.progressDelivery.visibility = View.VISIBLE
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val driverName = AuthRepository.currentProfile?.name ?: "Repartidor Yalazas"
        val driverPhone = AuthRepository.currentProfile?.phone ?: ""

        // 1. Ejecutamos el algoritmo de optimización de ruta y urgencia
        val sequencedOrders = DeliveryRouteOptimizer.optimizeDeliverySequence(ordersToTake)

        // 2. Aplicamos un Batch Write en Firestore para actualizar todos los documentos atómicamente
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        sequencedOrders.forEach { order ->
            val docRef = db.collection("orders").document(order.id)
            val updates = mapOf(
                "status" to OrderStatus.EN_CAMINO.name,
                "deliveryDriverId" to currentUid,
                "deliveryDriverName" to driverName,
                "deliveryDriverPhone" to driverPhone,
                "routeOrderIndex" to order.routeOrderIndex
            )
            batch.update(docRef, updates)
        }

        batch.commit()
            .addOnSuccessListener {
                if (isAdded) {
                    binding.progressDelivery.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "🚀 ¡Ruta de ${sequencedOrders.size} entregas optimizada con éxito!",
                        Toast.LENGTH_LONG
                    ).show()
                    switchTab(1) // Ir a Mis Entregas en Ruta
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    binding.progressDelivery.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Error al optimizar ruta: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun confirmMarkDelivered(order: Order) {
        MarkDeliveredConfirmationDialogFragment.newInstance(order) {
            markOrderDelivered(order)
        }.show(parentFragmentManager, MarkDeliveredConfirmationDialogFragment.TAG)
    }

    private fun markOrderDelivered(order: Order) {
        val db = FirebaseFirestore.getInstance()
        val updates = mapOf(
            "status" to OrderStatus.ENTREGADO.name,
            "paymentStatus" to "PAGADO"
        )

        db.collection("orders").document(order.id)
            .update(updates)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "🎉 ¡Pedido entregado con éxito! Registrado en tu historial.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Error al actualizar estado: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
