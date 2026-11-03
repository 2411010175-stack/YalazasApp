package com.yalazas.app.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.yalazas.app.R
import com.yalazas.app.model.OrderItem
import com.yalazas.app.repositorio.ServiciosFirebase
import com.yalazas.app.ui.admin.view.SalesChartView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Resume ventas y pedidos con datos escuchados en tiempo real. */
class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {

    private val db = ServiciosFirebase.firestore
    private var ordersListener: ListenerRegistration? = null
    private lateinit var salesAdapter: AdaptadorVentasRecientes
    private lateinit var salesChartView: SalesChartView

    private enum class ChartFilter { DD, MM, AA }
    private var currentFilter = ChartFilter.MM

    // Cached raw orders data for quick filtering
    private var cachedOrders = listOf<RawOrderData>()
    private var datosConfirmados = false

    private data class RawOrderData(
        val id: String,
        val total: Double,
        val status: String,
        val customerName: String,
        val phone: String,
        val address: String,
        val reference: String,
        val comment: String,
        val items: List<OrderItem>,
        val date: Date
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        salesChartView = view.findViewById(R.id.salesChartView)
        cachedOrders = emptyList()
        datosConfirmados = false

        // 1. Configurar botón de Ajustes (Engranaje)
        view.findViewById<ImageButton>(R.id.btnAdminSettings).setOnClickListener {
            findNavController().navigate(R.id.nav_admin_settings)
        }

        // 2. Configurar botón "Ver todas" sincronizado con la barra de navegación
        view.findViewById<TextView>(R.id.btnVerTodasVentas).setOnClickListener {
            val adminBottomNav = activity?.findViewById<BottomNavigationView>(R.id.adminBottomNav)
            if (adminBottomNav != null) {
                adminBottomNav.selectedItemId = R.id.nav_admin_sales
            } else {
                findNavController().navigate(R.id.nav_admin_sales)
            }
        }

        // 3. Configurar Recycler de Últimas Ventas
        setupRecentSales(view)

        // 4. Configurar Filtros de Gráfica (DD | MM | AA)
        setupChartFilters(view)

        // 5. Iniciar escucha en tiempo real de Firestore
        escucharPedidosEnTiempoReal(view)
    }

    private fun setupRecentSales(view: View) {
        val rvSales = view.findViewById<RecyclerView>(R.id.rvRecentSales)
        salesAdapter = AdaptadorVentasRecientes { saleItem ->
            showSaleDetailsDialog(saleItem)
        }
        rvSales.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvSales.adapter = salesAdapter
    }

    private fun setupChartFilters(view: View) {
        val btnDD = view.findViewById<TextView>(R.id.btnFilterDD)
        val btnMM = view.findViewById<TextView>(R.id.btnFilterMM)
        val btnAA = view.findViewById<TextView>(R.id.btnFilterAA)

        val acento = ContextCompat.getColor(requireContext(), R.color.admin_ui_accent)
        val white = ContextCompat.getColor(requireContext(), R.color.white)

        fun updateButtons(active: ChartFilter) {
            currentFilter = active
            btnDD.setTextColor(if (active == ChartFilter.DD) acento else white)
            btnMM.setTextColor(if (active == ChartFilter.MM) acento else white)
            btnAA.setTextColor(if (active == ChartFilter.AA) acento else white)
            updateChartData()
        }

        btnDD.setOnClickListener { updateButtons(ChartFilter.DD) }
        btnMM.setOnClickListener { updateButtons(ChartFilter.MM) }
        btnAA.setOnClickListener { updateButtons(ChartFilter.AA) }
    }

    /** Recalcula los indicadores y la gráfica cuando llegan cambios de pedidos. */
    private fun escucharPedidosEnTiempoReal(view: View) {
        val tvTotalSales = view.findViewById<TextView>(R.id.tvTotalSalesCount)
        val tvDailySales = view.findViewById<TextView>(R.id.tvDailySalesCount)
        val tvPendingOrders = view.findViewById<TextView>(R.id.tvPendingOrdersCount)

        ordersListener?.remove()
        ordersListener = db.collection("orders")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!isAdded || this@AdminDashboardFragment.view !== view) return@addSnapshotListener
                if (error != null || snapshot == null) {
                    tvTotalSales.text = "—"
                    tvDailySales.text = "—"
                    tvPendingOrders.text = "—"
                    salesAdapter.submitList(emptyList())
                    cachedOrders = emptyList()
                    datosConfirmados = false
                    salesChartView.mostrarMensaje("No se pudieron cargar las ventas")
                    updateAdminBadge(0)
                    return@addSnapshotListener
                }

                // La caché local puede mostrar cifras antiguas antes de responder el servidor.
                if (snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                val parsedOrders = mutableListOf<RawOrderData>()
                val salesItemList = mutableListOf<AdminSaleItem>()

                for ((index, doc) in snapshot.documents.withIndex()) {
                    val status = doc.getString("status") ?: "PENDIENTE"

                    val total = doc.getDouble("total") ?: 0.0
                    val customer = doc.getString("customerName") ?: "Cliente"
                    val phone = doc.getString("phone") ?: ""
                    val address = doc.getString("address") ?: ""
                    val reference = doc.getString("reference") ?: ""
                    val comment = doc.getString("comment") ?: ""
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()
                    val fechaPedido = timestamp ?: Date(0)

                    // Parse items
                    val itemsRaw = (doc.get("items") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                    val orderItems = itemsRaw.map { itemMap ->
                        OrderItem(
                            productId = itemMap["productId"]?.toString() ?: "",
                            name = itemMap["name"]?.toString() ?: "",
                            description = itemMap["description"]?.toString() ?: "",
                            quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 1,
                            subtotal = (itemMap["subtotal"] as? Number)?.toDouble() ?: 0.0
                        )
                    }

                    val rawOrder = RawOrderData(
                        id = doc.id,
                        total = total,
                        status = status,
                        customerName = customer,
                        phone = phone,
                        address = address,
                        reference = reference,
                        comment = comment,
                        items = orderItems,
                        date = fechaPedido
                    )
                    parsedOrders.add(rawOrder)

                    val saleNumber = String.format(Locale.US, "%05d", snapshot.size() - index)
                    val itemsSummary = orderItems.joinToString(", ") { "${it.quantity}x ${it.name}" }

                    salesItemList.add(
                        AdminSaleItem(
                            id = doc.id,
                            saleNumber = saleNumber,
                            date = timestamp?.let(dateFormat::format) ?: "Sin fecha",
                            time = timestamp?.let(timeFormat::format).orEmpty(),
                            total = total,
                            deliveryFee = (doc.get("deliveryFee") as? Number)?.toDouble() ?: 0.0,
                            isDelivery = doc.getBoolean("isDelivery") == true,
                            customerName = customer,
                            phone = phone,
                            address = address,
                            reference = reference,
                            comment = comment,
                            status = status,
                            items = orderItems,
                            itemsSummary = itemsSummary
                        )
                    )
                }

                val indicadores = EstadisticasVentasAdmin.calcular(
                    parsedOrders.map { PedidoParaResumen(it.status, it.date) }
                )
                // El gráfico solo representa ingresos de pedidos completados.
                cachedOrders = parsedOrders.filter { EstadisticasVentasAdmin.esVentaRealizada(it.status) }
                datosConfirmados = true

                tvTotalSales.text = indicadores.ventasRealizadas.toString()
                tvDailySales.text = indicadores.ventasDelDia.toString()
                tvPendingOrders.text = indicadores.pedidosEnCurso.toString()
                salesAdapter.submitList(salesItemList)
                updateChartData()
                updateAdminBadge(indicadores.pedidosEnCurso)
            }
    }

    private fun updateAdminBadge(pendingCount: Int) {
        val adminBottomNav = activity?.findViewById<BottomNavigationView>(R.id.adminBottomNav)
        if (adminBottomNav != null) {
            val badge = adminBottomNav.getOrCreateBadge(R.id.nav_admin_sales)
            badge.backgroundColor = ContextCompat.getColor(requireContext(), R.color.admin_nav_badge)
            badge.badgeTextColor = ContextCompat.getColor(requireContext(), R.color.white)
            badge.number = pendingCount
            badge.isVisible = pendingCount > 0
        }
    }

    private fun updateChartData() {
        if (!datosConfirmados) return
        when (currentFilter) {
            ChartFilter.DD -> buildDailyChart()
            ChartFilter.MM -> buildMonthlyChart()
            ChartFilter.AA -> buildYearlyChart()
        }
    }

    private fun buildDailyChart() {
        val daysCount = 7
        val labels = mutableListOf<String>()
        val salesAmounts = FloatArray(daysCount)
        val dayFormatter = SimpleDateFormat("EEE dd", Locale("es", "PE"))

        // Generar los últimos 7 días terminando hoy
        val dates = mutableListOf<Calendar>()
        for (i in (daysCount - 1) downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            dates.add(c)
            labels.add(dayFormatter.format(c.time).uppercase(Locale.getDefault()))
        }

        val orderCal = Calendar.getInstance()
        for (order in cachedOrders) {
            orderCal.time = order.date
            for (i in 0 until daysCount) {
                val targetCal = dates[i]
                if (orderCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                    orderCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
                ) {
                    salesAmounts[i] += order.total.toFloat()
                }
            }
        }

        // Gráfica de ingresos de pedidos entregados, en soles.
        salesChartView.setChartData(
            labels = labels,
            period = "Días",
            sales = salesAmounts.toList()
        )
    }

    private fun buildMonthlyChart() {
        val allMonthLabels = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SET", "OCT", "NOV", "DIC")
        val monthlySales = FloatArray(12)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val orderCal = Calendar.getInstance()

        for (order in cachedOrders) {
            orderCal.time = order.date
            if (orderCal.get(Calendar.YEAR) == currentYear) {
                val month = orderCal.get(Calendar.MONTH) // 0 to 11
                monthlySales[month] += order.total.toFloat()
            }
        }

        salesChartView.setChartData(
            labels = allMonthLabels,
            period = "Meses",
            sales = monthlySales.toList()
        )
    }

    private fun buildYearlyChart() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = listOf(currentYear - 2, currentYear - 1, currentYear)
        val labels = years.map { it.toString() }
        val yearlySales = FloatArray(years.size)

        val orderCal = Calendar.getInstance()
        for (order in cachedOrders) {
            orderCal.time = order.date
            val orderYear = orderCal.get(Calendar.YEAR)
            val index = years.indexOf(orderYear)
            if (index != -1) {
                yearlySales[index] += order.total.toFloat()
            }
        }

        salesChartView.setChartData(
            labels = labels,
            period = "Años",
            sales = yearlySales.toList()
        )
    }

    private fun showSaleDetailsDialog(saleItem: AdminSaleItem) {
        val sheet = HojaDetalleVenta.fromSaleItem(saleItem)
        sheet.show(parentFragmentManager, "HojaDetalleVenta")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ordersListener?.remove()
        ordersListener = null
    }
}
