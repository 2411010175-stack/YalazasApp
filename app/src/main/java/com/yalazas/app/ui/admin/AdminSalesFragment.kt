package com.yalazas.app.ui.admin

import android.content.res.ColorStateList
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
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.yalazas.app.R
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repositorio.RepositorioEstadosPedido
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

        configurarColoresFiltros(chipGroup)

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

    /** Cada filtro conserva el color de su estado; la selección usa un fondo más visible. */
    private fun configurarColoresFiltros(chipGroup: ChipGroup) {
        val filtros = listOf(
            R.id.chipPending to OrderStatus.PENDIENTE,
            R.id.chipKitchen to OrderStatus.EN_PROCESO,
            R.id.chipDelivery to OrderStatus.EN_CAMINO,
            R.id.chipDelivered to OrderStatus.ENTREGADO,
            R.id.chipCancelled to OrderStatus.CANCELADO
        )

        fun colores(seleccionado: Int, normal: Int) = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(ContextCompat.getColor(requireContext(), seleccionado), ContextCompat.getColor(requireContext(), normal))
        )

        chipGroup.findViewById<Chip>(R.id.chipAll)?.apply {
            chipBackgroundColor = colores(R.color.admin_filter_all_selected, R.color.admin_ui_surface)
            chipStrokeColor = colores(R.color.admin_ui_amount, R.color.admin_ui_border)
            setTextColor(colores(R.color.white, R.color.admin_ui_text_secondary))
            checkedIconTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
        }

        filtros.forEach { (id, estado) ->
            val estilo = PaletaEstadosVenta.para(estado)
            chipGroup.findViewById<Chip>(id)?.apply {
                chipBackgroundColor = colores(estilo.boton, estilo.tarjeta)
                chipStrokeColor = colores(estilo.textoEstado, estilo.borde)
                setTextColor(colores(R.color.white, estilo.textoEstado))
                checkedIconTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            }
        }
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
                "EN_PROCESO" -> OrderStatus.desdeTexto(order.status) in listOf(OrderStatus.EN_PROCESO, OrderStatus.LISTO_PARA_ENTREGA)
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
        val saleNumber = allOrdersWithNumbers.find { it.first.id == order.id }?.second ?: ""

        val sheet = HojaCambioEstadoPedido.desdePedido(order, saleNumber)
        sheet.onStatusSelected = { newStatus ->
            actualizarEstadoPedido(order, newStatus)
        }
        sheet.show(parentFragmentManager, HojaCambioEstadoPedido.TAG)
    }

    /** State valida el estado vigente y guarda la auditoría junto al pedido. */
    private fun actualizarEstadoPedido(order: Order, newStatus: String) {
        progressBar.visibility = View.VISIBLE
        val destino = OrderStatus.values().firstOrNull { it.key == newStatus }
        if (destino == null) {
            progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Estado de destino inválido", Toast.LENGTH_SHORT).show()
            return
        }
        RepositorioEstadosPedido.cambiarAdministrador(order.id, destino) { resultado ->
            if (!isAdded || view == null) return@cambiarAdministrador
            progressBar.visibility = View.GONE
            resultado.onSuccess {
                Toast.makeText(requireContext(), "Estado actualizado a ${destino.label}", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Error al actualizar estado: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
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
