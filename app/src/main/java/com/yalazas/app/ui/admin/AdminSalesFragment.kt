package com.yalazas.app.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.yalazas.app.R
import com.yalazas.app.model.AuditLog
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.ServiciosFirebase
import java.text.SimpleDateFormat
import java.util.Locale

/** Lista, filtra y actualiza pedidos desde el panel administrativo. */
class AdminSalesFragment : Fragment(R.layout.fragment_admin_sales) {

    private val db = ServiciosFirebase.firestore
    private var ordersListener: ListenerRegistration? = null
    private lateinit var adapter: AdaptadorVentas

    private var allOrdersWithNumbers = listOf<Pair<Order, String>>()
    private var currentSearchQuery = ""
    private var currentStatusFilter = "ALL"

    private lateinit var tvOrdersCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvOrdersCount = view.findViewById(R.id.tvOrdersCount)
        progressBar = view.findViewById(R.id.progressBarSales)
        layoutEmpty = view.findViewById(R.id.layoutEmptySales)
        val recyclerView = view.findViewById<RecyclerView>(R.id.ordersRecyclerView)
        val inputSearch = view.findViewById<TextInputEditText>(R.id.inputSearchOrders)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupStatus)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBackSales)

        // Navegación de regreso a la sección de Resumen
        fun navigateBackToSummary() {
            val adminBottomNav = activity?.findViewById<BottomNavigationView>(R.id.adminBottomNav)
            if (adminBottomNav != null && adminBottomNav.selectedItemId != R.id.nav_admin_dashboard) {
                adminBottomNav.selectedItemId = R.id.nav_admin_dashboard
            } else {
                if (!findNavController().popBackStack()) {
                    findNavController().navigate(R.id.nav_admin_dashboard)
                }
            }
        }

        btnBack?.setOnClickListener {
            navigateBackToSummary()
        }

        // Manejar botón de atrás del sistema
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val adminBottomNav = activity?.findViewById<BottomNavigationView>(R.id.adminBottomNav)
                if (adminBottomNav != null && adminBottomNav.selectedItemId != R.id.nav_admin_dashboard) {
                    adminBottomNav.selectedItemId = R.id.nav_admin_dashboard
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        adapter = AdaptadorVentas(
            onDetailsClick = { order, saleNumber ->
                mostrarDetallePedido(order, saleNumber)
            },
            onChangeStatusClick = { order ->
                mostrarDialogoCambioEstado(order)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Configurar Filtros por Chips
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentStatusFilter = when (checkedIds.firstOrNull()) {
                R.id.chipPending -> "PENDIENTE"
                R.id.chipKitchen -> "EN_PROCESO"
                R.id.chipDelivery -> "EN_CAMINO"
                R.id.chipDelivered -> "ENTREGADO"
                R.id.chipCancelled -> "CANCELADO"
                else -> "ALL"
            }
            aplicarFiltros()
        }

        // Configurar Barra de Búsqueda
        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                aplicarFiltros()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        escucharPedidosEnTiempoReal()
    }

    /** Escucha las ventas y conserva los datos necesarios para aplicar filtros locales. */
    private fun escucharPedidosEnTiempoReal() {
        progressBar.visibility = View.VISIBLE
        ordersListener?.remove()
        ordersListener = db.collection("orders")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE
                if (error != null) {
                    Toast.makeText(requireContext(), "Error al cargar pedidos: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val totalDocs = snapshot.size()
                    val orderList = mutableListOf<Pair<Order, String>>()

                    for ((index, doc) in snapshot.documents.withIndex()) {
                        val order = doc.toObject(Order::class.java)
                        if (order != null) {
                            order.id = doc.id
                            val saleNumber = String.format(Locale.US, "Venta N° %05d", totalDocs - index)
                            orderList.add(order to saleNumber)
                        }
                    }

                    allOrdersWithNumbers = orderList
                    tvOrdersCount.text = "${allOrdersWithNumbers.size} pedidos"
                    aplicarFiltros()
                }
            }
    }

    /** Combina los filtros seleccionados antes de actualizar la lista. */
    private fun aplicarFiltros() {
        val queryLower = currentSearchQuery.lowercase(Locale.getDefault())

        val filtered = allOrdersWithNumbers.filter { (order, saleNumber) ->
            // 1. Filtro por Estado
            val statusMatches = when (currentStatusFilter) {
                "ALL" -> true
                "PENDIENTE" -> order.status.contains("PENDIENTE", ignoreCase = true)
                "EN_PROCESO" -> order.status.contains("PROCESO", ignoreCase = true) || order.status.contains("COCIN", ignoreCase = true)
                "EN_CAMINO" -> order.status.contains("CAMINO", ignoreCase = true) || order.status.contains("REPART", ignoreCase = true)
                "ENTREGADO" -> order.status.contains("ENTREG", ignoreCase = true)
                "CANCELADO" -> order.status.contains("CANCEL", ignoreCase = true)
                else -> true
            }

            // 2. Filtro por Búsqueda de Texto
            val searchMatches = if (queryLower.isEmpty()) {
                true
            } else {
                saleNumber.lowercase(Locale.getDefault()).contains(queryLower) ||
                order.customerName.lowercase(Locale.getDefault()).contains(queryLower) ||
                order.phone.lowercase(Locale.getDefault()).contains(queryLower) ||
                order.address.lowercase(Locale.getDefault()).contains(queryLower) ||
                order.items.any { it.name.lowercase(Locale.getDefault()).contains(queryLower) }
            }

            statusMatches && searchMatches
        }

        adapter.submitList(filtered)
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun mostrarDialogoCambioEstado(order: Order) {
        val currentAdminId = RepositorioAutenticacion.currentProfile?.uid ?: "ADMIN"
        val saleNumber = allOrdersWithNumbers.find { it.first.id == order.id }?.second ?: ""

        val sheet = HojaCambioEstadoPedido.desdePedido(order, saleNumber)
        sheet.onStatusSelected = { newStatus ->
            actualizarEstadoPedido(order, newStatus, currentAdminId)
        }
        sheet.show(parentFragmentManager, HojaCambioEstadoPedido.TAG)
    }

    /** Registra el nuevo estado y la auditoría de la acción administrativa. */
    private fun actualizarEstadoPedido(order: Order, newStatus: String, adminId: String) {
        progressBar.visibility = View.VISIBLE

        val updates = mapOf(
            "status" to newStatus,
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection("orders").document(order.id)
            .update(updates)
            .addOnSuccessListener {
                // Registrar auditoría
                val log = AuditLog(
                    id = db.collection("auditoria").document().id,
                    usuarioAfectadoId = order.userId.ifBlank { order.id },
                    usuarioAdministradorId = adminId,
                    accion = "CAMBIO_ESTADO_PEDIDO",
                    rolAnterior = order.status,
                    rolNuevo = newStatus
                )
                db.collection("auditoria").document(log.id).set(log)

                Toast.makeText(requireContext(), "Estado actualizado a $newStatus", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al actualizar estado: ${e.message}", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun mostrarDetallePedido(order: Order, saleNumber: String) {
        val sheet = HojaDetalleVenta.desdePedido(order, saleNumber)
        sheet.show(parentFragmentManager, "HojaDetalleVenta")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ordersListener?.remove()
        ordersListener = null
    }
}
