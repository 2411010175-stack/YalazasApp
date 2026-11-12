package com.yalazas.app.ui.admin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yalazas.app.R
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderItem
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repositorio.RepositorioEstadosPedido
import com.yalazas.app.repositorio.RepositorioImagenes
import com.yalazas.app.repositorio.ServiciosFirebase
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

/** Muestra el detalle de una venta y sus acciones administrativas. */
class HojaDetalleVenta : BottomSheetDialogFragment() {

    private var orderId: String = ""
    private var saleNumber: String = ""
    private var status: String = "PENDIENTE"
    private var date: String = ""
    private var time: String = ""
    private var customerName: String = ""
    private var phone: String = ""
    private var address: String = ""
    private var reference: String = ""
    private var comment: String = ""
    private var total: Double = 0.0
    private var deliveryFee: Double = 0.0
    private var isDelivery: Boolean = false
    private var serviceType: String = ""
    private var tableNumber: Int = 0
    private var items: ArrayList<OrderItem> = arrayListOf()

    var onStatusUpdated: ((String) -> Unit)? = null

    private lateinit var tvStatusBadge: TextView
    private var escuchaPago: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            orderId = it.getString(ARG_ORDER_ID, "")
            saleNumber = it.getString(ARG_SALE_NUMBER, "Venta")
            status = it.getString(ARG_STATUS, "PENDIENTE")
            date = it.getString(ARG_DATE, "")
            time = it.getString(ARG_TIME, "")
            customerName = it.getString(ARG_CUSTOMER, "")
            phone = it.getString(ARG_PHONE, "")
            address = it.getString(ARG_ADDRESS, "")
            reference = it.getString(ARG_REFERENCE, "")
            comment = it.getString(ARG_COMMENT, "")
            total = it.getDouble(ARG_TOTAL, 0.0)
            deliveryFee = it.getDouble(ARG_DELIVERY_FEE, 0.0)
            isDelivery = it.getBoolean(ARG_IS_DELIVERY, false)
            serviceType = it.getString(ARG_SERVICE_TYPE, "")
            tableNumber = it.getInt(ARG_TABLE_NUMBER, 0)
            @Suppress("DEPRECATION")
            val parcelableList = it.getParcelableArrayList<OrderItem>(ARG_ITEMS)
            if (parcelableList != null) {
                items = parcelableList
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_admin_sale_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSaleNumber = view.findViewById<TextView>(R.id.tvSheetSaleNumber)
        val tvDateTime = view.findViewById<TextView>(R.id.tvSheetDateTime)
        tvStatusBadge = view.findViewById(R.id.tvSheetStatus)
        val tvCustomer = view.findViewById<TextView>(R.id.tvSheetCustomerName)
        val tvPhone = view.findViewById<TextView>(R.id.tvSheetCustomerPhone)
        val btnCall = view.findViewById<MaterialButton>(R.id.btnSheetCall)
        val btnWhatsApp = view.findViewById<MaterialButton>(R.id.btnSheetWhatsApp)
        val tvAddress = view.findViewById<TextView>(R.id.tvSheetCustomerAddress)
        val tvRef = view.findViewById<TextView>(R.id.tvSheetCustomerReference)
        val layoutComment = view.findViewById<View>(R.id.layoutSheetComment)
        val tvComment = view.findViewById<TextView>(R.id.tvSheetComment)
        val tvItemsCount = view.findViewById<TextView>(R.id.tvSheetItemsCount)
        val containerProducts = view.findViewById<LinearLayout>(R.id.containerSheetProducts)
        val tvTotalAmount = view.findViewById<TextView>(R.id.tvSheetTotalAmount)
        val tvProductsAmount = view.findViewById<TextView>(R.id.tvSheetProductsAmount)
        val tvDeliveryFee = view.findViewById<TextView>(R.id.tvSheetDeliveryFee)
        val tvDeliveryLabel = view.findViewById<TextView>(R.id.tvSheetDeliveryLabel)
        val btnChangeStatus = view.findViewById<MaterialButton>(R.id.btnSheetChangeStatus)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnSheetClose)
        configurarRevisionPago(view)

        // 1. Datos de Cabecera
        tvSaleNumber.text = if (saleNumber.startsWith("Venta")) saleNumber else "Venta N° $saleNumber"
        val codigoPedido = if (orderId.isNotBlank()) "#${orderId.takeLast(5).uppercase(Locale.getDefault())}" else "#ORDEN"
        tvDateTime.text = if (time.isNotBlank()) "Pedido $codigoPedido · 📅 $date a las $time hrs"
            else "Pedido $codigoPedido · 📅 $date"
        updateStatusBadge(status)
        btnChangeStatus.backgroundTintList = ColorStateList.valueOf(
            color(PaletaEstadosVenta.para(OrderStatus.desdeTexto(status)).boton)
        )

        // 2. Datos del Cliente
        tvCustomer.text = customerName.ifBlank { "Cliente No Identificado" }

        val cleanPhone = phone.trim()
        if (cleanPhone.isNotBlank()) {
            tvPhone.text = "📞 $cleanPhone"
            btnCall.visibility = View.VISIBLE
            btnWhatsApp.visibility = View.VISIBLE

            btnCall.setOnClickListener {
                val callIntent = Intent(Intent.ACTION_DIAL, "tel:$cleanPhone".toUri())
                try {
                    startActivity(callIntent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(requireContext(), "No se pudo abrir la app de llamadas", Toast.LENGTH_SHORT).show()
                }
            }

            btnWhatsApp.setOnClickListener {
                openCustomerWhatsApp(cleanPhone, customerName, saleNumber)
            }
        } else {
            tvPhone.text = "📞 Teléfono no registrado"
            btnCall.visibility = View.GONE
            btnWhatsApp.visibility = View.GONE
        }

        // Dirección y Referencia
        if (address.isNotBlank()) {
            tvAddress.text = address
            tvAddress.visibility = View.VISIBLE
        } else {
            tvAddress.text = if (serviceType == "MESA") "Mesa $tableNumber"
                else "Recojo en local / Sin dirección registrada"
        }

        if (reference.isNotBlank()) {
            tvRef.text = "🏢 Ref: $reference"
            tvRef.visibility = View.VISIBLE
        } else {
            tvRef.visibility = View.GONE
        }

        // Nota especial
        if (comment.isNotBlank()) {
            layoutComment.visibility = View.VISIBLE
            tvComment.text = "“$comment”"
        } else {
            layoutComment.visibility = View.GONE
        }

        // 3. Desglose de Productos
        tvItemsCount.text = "${items.size} producto${if (items.size != 1) "s" else ""}"
        containerProducts.removeAllViews()

        if (items.isNotEmpty()) {
            val inflater = LayoutInflater.from(requireContext())
            for (item in items) {
                val rowView = inflater.inflate(R.layout.item_admin_sheet_product_row, containerProducts, false)
                val tvQty = rowView.findViewById<TextView>(R.id.tvRowQty)
                val tvName = rowView.findViewById<TextView>(R.id.tvRowName)
                val tvSauces = rowView.findViewById<TextView>(R.id.tvRowSauces)
                val tvNotes = rowView.findViewById<TextView>(R.id.tvRowNotes)
                val tvSubtotal = rowView.findViewById<TextView>(R.id.tvRowSubtotal)

                tvQty.text = "${item.quantity}x"
                tvName.text = item.description.ifBlank { item.name }

                if (item.description.isBlank() && item.selectedSauces.isNotEmpty()) {
                    tvSauces.visibility = View.VISIBLE
                    tvSauces.text = "🍗 Salsas: ${item.selectedSauces.joinToString(", ")}"
                } else {
                    tvSauces.visibility = View.GONE
                }

                if (item.notes.isNotBlank()) {
                    tvNotes.visibility = View.VISIBLE
                    tvNotes.text = "📝 Nota: ${item.notes}"
                } else {
                    tvNotes.visibility = View.GONE
                }

                tvSubtotal.text = "S/ ${String.format(Locale.US, "%.2f", item.subtotal)}"

                containerProducts.addView(rowView)
            }
        } else {
            val emptyTv = TextView(requireContext()).apply {
                text = "• Sin productos desglosados en este registro"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_ui_text_secondary))
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            containerProducts.addView(emptyTv)
        }

        // 4. El total guardado ya incluye envío; solo se desglosa para explicarlo.
        tvProductsAmount.text = "S/ ${String.format(Locale.US, "%.2f", (total - deliveryFee).coerceAtLeast(0.0))}"
        tvDeliveryLabel.text = when {
            serviceType == "MESA" -> "Servicio en mesa"
            isDelivery -> "Delivery"
            else -> "Envío (recojo en local)"
        }
        tvDeliveryFee.text = "S/ ${String.format(Locale.US, "%.2f", deliveryFee)}"
        tvTotalAmount.text = "S/ ${String.format(Locale.US, "%.2f", total)}"

        // 5. Botones de Acción
        btnChangeStatus.setOnClickListener {
            showChangeStatusDialog()
        }

        btnClose.setOnClickListener {
            dismiss()
        }
    }

    /** Solo administración ve esta sección; el cliente conserva el estado visible del pedido. */
    private fun configurarRevisionPago(raiz: View) {
        val contenedor = raiz.findViewById<View>(R.id.layoutAdminPaymentReview)
        val texto = raiz.findViewById<TextView>(R.id.tvAdminPaymentReview)
        val ver = raiz.findViewById<MaterialButton>(R.id.btnAdminViewProof)
        val acciones = raiz.findViewById<View>(R.id.layoutAdminReviewActions)
        val aprobar = raiz.findViewById<MaterialButton>(R.id.btnAdminApproveProof)
        val rechazar = raiz.findViewById<MaterialButton>(R.id.btnAdminRejectProof)
        if (orderId.isBlank()) return
        escuchaPago = ServiciosFirebase.firestore.collection("orders").document(orderId)
            .addSnapshotListener { pedido, error ->
                if (!isAdded || view !== raiz) return@addSnapshotListener
                if (error != null || pedido == null || !pedido.exists()) {
                    contenedor.visibility = View.GONE
                    return@addSnapshotListener
                }
                val metodo = pedido.getString("paymentMethod").orEmpty()
                val digital = metodo in setOf("YAPE", "PLIN", "TRANSFERENCIA") &&
                    pedido.getString("serviceType") != "MESA"
                contenedor.visibility = if (digital) View.VISIBLE else View.GONE
                if (!digital) return@addSnapshotListener
                val estado = pedido.getString("paymentProofStatus").orEmpty()
                val pagado = pedido.getString("paymentStatus") == "PAGADO"
                texto.text = when {
                    pagado -> "$metodo · Pago validado por administración"
                    estado == "EN_REVISION" -> "$metodo · Comprobante pendiente de revisión"
                    estado == "RECHAZADO" -> "$metodo · Comprobante rechazado; espera uno nuevo"
                    else -> "$metodo · Esperando comprobante del cliente"
                }
                ver.visibility = if (estado.isNotBlank()) View.VISIBLE else View.GONE
                acciones.visibility = if (estado == "EN_REVISION" && !pagado) View.VISIBLE else View.GONE
            }
        ver.setOnClickListener {
            RepositorioImagenes.obtenerComprobante(orderId) { resultado ->
                if (!isAdded || view !== raiz) return@obtenerComprobante
                resultado.onSuccess { bytes ->
                    val imagen = ImageView(requireContext()).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(16, 16, 16, 16)
                        setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    }
                    val dialogo = MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Comprobante de pago")
                        .setView(imagen)
                        .setPositiveButton("Cerrar", null)
                        .show()
                    dialogo.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }.onFailure { error ->
                    Toast.makeText(requireContext(), error.localizedMessage ?: "No se pudo abrir el comprobante", Toast.LENGTH_LONG).show()
                }
            }
        }
        fun revisar(aprobado: Boolean) {
            aprobar.isEnabled = false
            rechazar.isEnabled = false
            RepositorioImagenes.revisarComprobante(orderId, aprobado) { resultado ->
                if (!isAdded || view !== raiz) return@revisarComprobante
                aprobar.isEnabled = true
                rechazar.isEnabled = true
                resultado.onSuccess {
                    Toast.makeText(requireContext(), if (aprobado) "Pago validado" else "Comprobante rechazado", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(requireContext(), error.localizedMessage ?: "No se pudo revisar el pago", Toast.LENGTH_LONG).show()
                }
            }
        }
        aprobar.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Validar pago")
                .setMessage("Confirma que el dinero llegó a la cuenta del restaurante.")
                .setNegativeButton("Volver", null).setPositiveButton("Validar") { _, _ -> revisar(true) }.show()
        }
        rechazar.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Rechazar comprobante")
                .setMessage("El cliente podrá enviar una nueva imagen.")
                .setNegativeButton("Volver", null).setPositiveButton("Rechazar") { _, _ -> revisar(false) }.show()
        }
    }

    override fun onDestroyView() {
        escuchaPago?.remove()
        escuchaPago = null
        super.onDestroyView()
    }

    private fun updateStatusBadge(currentStatus: String) {
        val upper = currentStatus.uppercase(Locale.getDefault())
        when {
            upper.contains("LISTO") -> {
                tvStatusBadge.text = "📦 LISTO PARA ENTREGA"
                tvStatusBadge.setTextColor(color(R.color.admin_status_ready))
                tvStatusBadge.background = createPillDrawable(color(R.color.admin_status_ready_bg))
            }
            upper.contains("PENDIENTE") -> {
                tvStatusBadge.text = "🟡 PENDIENTE"
                tvStatusBadge.setTextColor(color(R.color.admin_status_pending))
                tvStatusBadge.background = createPillDrawable(color(R.color.admin_status_pending_bg))
            }
            upper.contains("PROCESO") || upper.contains("COCIN") -> {
                tvStatusBadge.text = "👨‍🍳 EN COCINA"
                tvStatusBadge.setTextColor(color(R.color.admin_status_cooking))
                tvStatusBadge.background = createPillDrawable(color(R.color.admin_status_cooking_bg))
            }
            upper.contains("CAMINO") || upper.contains("REPART") -> {
                tvStatusBadge.text = "🛵 EN CAMINO"
                tvStatusBadge.setTextColor(color(R.color.admin_status_road))
                tvStatusBadge.background = createPillDrawable(color(R.color.admin_status_road_bg))
            }
            upper.contains("ENTREG") -> {
                tvStatusBadge.text = "✅ ENTREGADO"
                tvStatusBadge.setTextColor(color(R.color.admin_status_delivered))
                tvStatusBadge.background = createPillDrawable(color(R.color.admin_status_delivered_bg))
            }
            upper.contains("CANCEL") -> {
                tvStatusBadge.text = "❌ CANCELADO"
                tvStatusBadge.setTextColor(color(R.color.admin_status_cancelled))
                tvStatusBadge.background = createPillDrawable(color(R.color.admin_status_cancelled_bg))
            }
            else -> {
                tvStatusBadge.text = "• $upper"
                tvStatusBadge.setTextColor(color(R.color.admin_ui_text_secondary))
                tvStatusBadge.background = createPillDrawable(color(R.color.admin_ui_divider))
            }
        }
    }

    private fun color(id: Int): Int = ContextCompat.getColor(requireContext(), id)

    private fun createPillDrawable(bgColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(bgColor)
        }
    }

    private fun openCustomerWhatsApp(phoneRaw: String, name: String, saleNumber: String) {
        var digits = phoneRaw.replace(Regex("[^0-9]"), "")
        if (digits.length == 9 && digits.startsWith("9")) {
            digits = "51$digits"
        }
        val clientGreeting = name.trim().ifEmpty { "Cliente" }
        val saleLabel = if (saleNumber.startsWith("Venta")) saleNumber else "N° $saleNumber"
        val msg = "¡Hola $clientGreeting! Te saludamos del equipo de Yalazas sobre tu orden $saleLabel 🍗🍟."
        val url = "https://wa.me/$digits?text=${Uri.encode(msg)}"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No tienes WhatsApp instalado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChangeStatusDialog() {
        val sheet = HojaCambioEstadoPedido.crear(
            orderId = orderId,
            saleNumber = saleNumber,
            currentStatus = status,
            customerName = customerName,
            totalAmount = total,
            serviceType = serviceType
        )
        sheet.onStatusSelected = { newStatus ->
            applyStatusUpdate(newStatus)
        }
        sheet.show(parentFragmentManager, HojaCambioEstadoPedido.TAG)
    }

    private fun applyStatusUpdate(newStatus: String) {
        if (orderId.isBlank()) {
            Toast.makeText(requireContext(), "Esta venta no tiene un pedido asociado.", Toast.LENGTH_SHORT).show()
            return
        }
        val destino = OrderStatus.values().firstOrNull { it.key == newStatus }
        if (destino == null) {
            Toast.makeText(requireContext(), "Estado de destino inválido", Toast.LENGTH_SHORT).show()
            return
        }
        RepositorioEstadosPedido.cambiarAdministrador(orderId, destino) { resultado ->
            if (!isAdded || view == null) return@cambiarAdministrador
            resultado.onSuccess {
                status = newStatus
                updateStatusBadge(newStatus)
                onStatusUpdated?.invoke(newStatus)
                Toast.makeText(requireContext(), "Estado actualizado a ${destino.label}", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Error al actualizar estado: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val ARG_ORDER_ID = "arg_order_id"
        private const val ARG_SALE_NUMBER = "arg_sale_number"
        private const val ARG_STATUS = "arg_status"
        private const val ARG_DATE = "arg_date"
        private const val ARG_TIME = "arg_time"
        private const val ARG_CUSTOMER = "arg_customer"
        private const val ARG_PHONE = "arg_phone"
        private const val ARG_ADDRESS = "arg_address"
        private const val ARG_REFERENCE = "arg_reference"
        private const val ARG_COMMENT = "arg_comment"
        private const val ARG_TOTAL = "arg_total"
        private const val ARG_DELIVERY_FEE = "arg_delivery_fee"
        private const val ARG_IS_DELIVERY = "arg_is_delivery"
        private const val ARG_SERVICE_TYPE = "arg_service_type"
        private const val ARG_TABLE_NUMBER = "arg_table_number"
        private const val ARG_ITEMS = "arg_items"

        fun crear(
            orderId: String,
            saleNumber: String,
            status: String,
            date: String,
            time: String,
            customerName: String,
            phone: String,
            address: String,
            reference: String,
            comment: String,
            total: Double,
            deliveryFee: Double,
            isDelivery: Boolean,
            items: List<OrderItem>,
            serviceType: String = "",
            tableNumber: Int = 0
        ): HojaDetalleVenta {
            val fragment = HojaDetalleVenta()
            val args = Bundle().apply {
                putString(ARG_ORDER_ID, orderId)
                putString(ARG_SALE_NUMBER, saleNumber)
                putString(ARG_STATUS, status)
                putString(ARG_DATE, date)
                putString(ARG_TIME, time)
                putString(ARG_CUSTOMER, customerName)
                putString(ARG_PHONE, phone)
                putString(ARG_ADDRESS, address)
                putString(ARG_REFERENCE, reference)
                putString(ARG_COMMENT, comment)
                putDouble(ARG_TOTAL, total)
                putDouble(ARG_DELIVERY_FEE, deliveryFee)
                putBoolean(ARG_IS_DELIVERY, isDelivery)
                putString(ARG_SERVICE_TYPE, serviceType)
                putInt(ARG_TABLE_NUMBER, tableNumber)
                putParcelableArrayList(ARG_ITEMS, ArrayList(items))
            }
            fragment.arguments = args
            return fragment
        }

        fun desdePedido(order: Order, saleNumber: String): HojaDetalleVenta {
            val dateObj = order.timestamp?.toDate()
            val dateStr = if (dateObj != null) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(dateObj) else ""
            val timeStr = if (dateObj != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(dateObj) else ""

            return crear(
                orderId = order.id,
                saleNumber = saleNumber,
                status = order.status,
                date = dateStr,
                time = timeStr,
                customerName = order.customerName,
                phone = order.phone,
                address = order.address,
                reference = order.reference,
                comment = order.comment,
                total = order.total,
                deliveryFee = order.deliveryFee,
                isDelivery = order.isDelivery,
                items = order.items,
                serviceType = order.serviceType,
                tableNumber = order.tableNumber
            )
        }

        fun fromSaleItem(saleItem: AdminSaleItem): HojaDetalleVenta {
            return crear(
                orderId = saleItem.id,
                saleNumber = saleItem.saleNumber,
                status = saleItem.status,
                date = saleItem.date,
                time = saleItem.time,
                customerName = saleItem.customerName,
                phone = saleItem.phone,
                address = saleItem.address,
                reference = saleItem.reference,
                comment = saleItem.comment,
                total = saleItem.total,
                deliveryFee = saleItem.deliveryFee,
                isDelivery = saleItem.isDelivery,
                items = saleItem.items,
                serviceType = saleItem.serviceType,
                tableNumber = saleItem.tableNumber
            )
        }
    }
}
