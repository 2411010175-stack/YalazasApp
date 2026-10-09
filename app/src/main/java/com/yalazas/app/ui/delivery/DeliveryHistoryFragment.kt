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
import com.yalazas.app.databinding.FragmentDeliveryHistoryBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import java.util.Calendar
import java.util.Locale

class DeliveryHistoryFragment : Fragment(R.layout.fragment_delivery_history) {

    private var _binding: FragmentDeliveryHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeliveryHistoryAdapter
    private var firestoreListener: ListenerRegistration? = null

    private var deliveredOrders: List<Order> = emptyList()
    private var selectedFilter: Int = 0 // 0 = Hoy, 1 = Últimos 7 días, 2 = Todos

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeliveryHistoryBinding.bind(view)

        setupRecyclerView()
        setupListeners()
        listenDeliveredOrders()
    }

    private fun setupRecyclerView() {
        adapter = DeliveryHistoryAdapter()
        binding.rvDeliveryHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDeliveryHistory.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefreshHistory.setOnRefreshListener {
            binding.swipeRefreshHistory.isRefreshing = false
            listenDeliveredOrders()
        }

        binding.chipFilterToday.setOnClickListener {
            switchFilter(0)
        }

        binding.chipFilterWeek.setOnClickListener {
            switchFilter(1)
        }

        binding.chipFilterAll.setOnClickListener {
            switchFilter(2)
        }
    }

    private fun switchFilter(filter: Int) {
        selectedFilter = filter

        val selectedBg = R.drawable.bg_delivery_tab_selected
        val unselectedBg = R.drawable.bg_delivery_tab_unselected
        val selectedColor = requireContext().getColor(R.color.yalaza_primary_green)
        val unselectedColor = 0xFF8AA394.toInt()

        binding.chipFilterToday.setBackgroundResource(if (filter == 0) selectedBg else unselectedBg)
        binding.chipFilterToday.setTextColor(if (filter == 0) selectedColor else unselectedColor)

        binding.chipFilterWeek.setBackgroundResource(if (filter == 1) selectedBg else unselectedBg)
        binding.chipFilterWeek.setTextColor(if (filter == 1) selectedColor else unselectedColor)

        binding.chipFilterAll.setBackgroundResource(if (filter == 2) selectedBg else unselectedBg)
        binding.chipFilterAll.setTextColor(if (filter == 2) selectedColor else unselectedColor)

        when (filter) {
            0 -> binding.tvHistoryDateSubtitle.text = "Resumen del turno de hoy"
            1 -> binding.tvHistoryDateSubtitle.text = "Resumen de los últimos 7 días"
            2 -> binding.tvHistoryDateSubtitle.text = "Historial completo de entregas"
        }

        filterAndComputeMetrics()
    }

    private fun listenDeliveredOrders() {
        firestoreListener?.remove()
        binding.progressHistory.visibility = View.VISIBLE

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val db = FirebaseFirestore.getInstance()

        firestoreListener = db.collection("orders")
            .whereEqualTo("status", OrderStatus.ENTREGADO.name)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded || _binding == null) return@addSnapshotListener
                binding.progressHistory.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(requireContext(), "Error al cargar historial: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val allDelivered = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Order::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }

                    // Filtrar por entregas a domicilio del repartidor actual (o generales entregadas si no tienen id asignado)
                    deliveredOrders = allDelivered.filter { order ->
                        order.isDelivery && (order.deliveryDriverId == currentUid || order.deliveryDriverId.isNullOrEmpty())
                    }

                    filterAndComputeMetrics()
                }
            }
    }

    private fun filterAndComputeMetrics() {
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

        val filtered = when (selectedFilter) {
            0 -> deliveredOrders.filter { (it.timestamp?.toDate()?.time ?: 0L) >= startOfToday }
            1 -> deliveredOrders.filter { (it.timestamp?.toDate()?.time ?: 0L) >= sevenDaysAgo }
            else -> deliveredOrders
        }

        // Cálculos de caja
        var cashTotal = 0.0
        var digitalTotal = 0.0
        var grandTotal = 0.0

        filtered.forEach { order ->
            grandTotal += order.total
            if (order.paymentMethod.contains("EFECTIVO", ignoreCase = true)) {
                cashTotal += order.total
            } else {
                digitalTotal += order.total
            }
        }

        // Actualizar KPIs
        binding.tvKpiCashToRender.text = String.format(Locale.getDefault(), "S/ %.2f", cashTotal)
        binding.tvKpiDeliveredCount.text = "${filtered.size} ${if (filtered.size == 1) "pedido" else "pedidos"}"
        binding.tvKpiDigitalTotal.text = String.format(Locale.getDefault(), "S/ %.2f", digitalTotal)
        binding.tvKpiGrandTotal.text = String.format(Locale.getDefault(), "S/ %.2f", grandTotal)

        adapter.updateOrders(filtered)

        if (filtered.isEmpty()) {
            binding.layoutHistoryEmptyState.visibility = View.VISIBLE
            binding.rvDeliveryHistory.visibility = View.GONE
        } else {
            binding.layoutHistoryEmptyState.visibility = View.GONE
            binding.rvDeliveryHistory.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
        firestoreListener = null
        _binding = null
    }
}
