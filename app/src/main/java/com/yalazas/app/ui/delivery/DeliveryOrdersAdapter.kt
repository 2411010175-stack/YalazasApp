package com.yalazas.app.ui.delivery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.yalazas.app.R
import com.yalazas.app.databinding.ItemDeliveryOrderBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.utils.DeliveryPricingHelper
import com.yalazas.app.utils.DeliveryRouteOptimizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeliveryOrdersAdapter(
    private var orders: List<Order> = emptyList(),
    private var isSelectionMode: Boolean = true,
    private val onTakeOrderClick: (Order) -> Unit,
    private val onMarkDeliveredClick: (Order) -> Unit,
    private val onSelectionChanged: (selectedCount: Int, selectedOrders: List<Order>) -> Unit
) : RecyclerView.Adapter<DeliveryOrdersAdapter.DeliveryOrderViewHolder>() {

    private val selectedOrderIds = mutableSetOf<String>()

    fun updateOrders(newOrders: List<Order>, enableSelection: Boolean = true) {
        orders = newOrders
        isSelectionMode = enableSelection
        // Limpiar selecciones que ya no existen en la lista
        val currentIds = newOrders.map { it.id }.toSet()
        selectedOrderIds.retainAll(currentIds)
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun selectAll() {
        selectedOrderIds.clear()
        selectedOrderIds.addAll(orders.map { it.id })
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun clearSelection() {
        selectedOrderIds.clear()
        notifyDataSetChanged()
        notifySelectionUpdate()
    }

    fun getSelectedOrders(): List<Order> {
        return orders.filter { selectedOrderIds.contains(it.id) }
    }

    private fun notifySelectionUpdate() {
        val selected = getSelectedOrders()
        onSelectionChanged(selected.size, selected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeliveryOrderViewHolder {
        val binding = ItemDeliveryOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeliveryOrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeliveryOrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class DeliveryOrderViewHolder(
        private val binding: ItemDeliveryOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val context = binding.root.context
            val isSelected = selectedOrderIds.contains(order.id)

            // Número de Pedido
            val numStr = "Pedido #${order.id.takeLast(6).uppercase()}"
            binding.tvDeliveryOrderNumber.text = numStr

            // Checkbox para selección múltiple (solo visible en modo selección / pedidos por recoger)
            if (isSelectionMode && order.status == OrderStatus.EN_PROCESO.name) {
                binding.cbSelectOrder.visibility = View.VISIBLE
                binding.cbSelectOrder.setOnCheckedChangeListener(null)
                binding.cbSelectOrder.isChecked = isSelected

                binding.cbSelectOrder.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedOrderIds.add(order.id)
                    } else {
                        selectedOrderIds.remove(order.id)
                    }
                    updateCardStroke(isSelected = isChecked)
                    notifySelectionUpdate()
                }

                binding.cardDeliveryOrder.setOnClickListener {
                    binding.cbSelectOrder.toggle()
                }
            } else {
                binding.cbSelectOrder.visibility = View.GONE
                binding.cardDeliveryOrder.setOnClickListener(null)
            }

            updateCardStroke(isSelected)

            // Estado y Botón de Acción Principal
            val status = OrderStatus.fromString(order.status)
            when (status) {
                OrderStatus.EN_PROCESO -> {
                    binding.tvDeliveryStatusBadge.text = "📦 LISTO"
                    binding.tvDeliveryStatusBadge.setBackgroundResource(R.drawable.bg_delivery_tab_selected)
                    binding.tvDeliveryStatusBadge.setTextColor(context.getColor(R.color.yalaza_primary_green))
                    binding.btnDeliveryMainAction.text = "Tomar Pedido y Salir 🛵"
                    binding.btnDeliveryMainAction.setBackgroundColor(context.getColor(R.color.yalaza_primary_green))
                    binding.btnDeliveryMainAction.setOnClickListener { onTakeOrderClick(order) }
                    binding.tvDeliveryRouteStopBadge.visibility = View.GONE
                }
                OrderStatus.EN_CAMINO -> {
                    binding.tvDeliveryStatusBadge.text = "🛵 EN RUTA"
                    binding.tvDeliveryStatusBadge.setBackgroundResource(R.drawable.bg_delivery_tab_selected)
                    binding.tvDeliveryStatusBadge.setTextColor(0xFFFFD54F.toInt())
                    binding.btnDeliveryMainAction.text = "Marcar como Entregado ✅"
                    binding.btnDeliveryMainAction.setBackgroundColor(0xFF00C853.toInt())
                    binding.btnDeliveryMainAction.setOnClickListener { onMarkDeliveredClick(order) }

                    // Mostrar Medalla / Número de Parada si está ordenado
                    if (order.routeOrderIndex > 0) {
                        binding.tvDeliveryRouteStopBadge.visibility = View.VISIBLE
                        binding.tvDeliveryRouteStopBadge.text = DeliveryRouteOptimizer.getStopBadgeText(order.routeOrderIndex)
                    } else {
                        binding.tvDeliveryRouteStopBadge.visibility = View.GONE
                    }
                }
                else -> {
                    binding.tvDeliveryStatusBadge.text = "${status.emoji} ${status.label.uppercase()}"
                    binding.btnDeliveryMainAction.visibility = View.GONE
                    binding.tvDeliveryRouteStopBadge.visibility = View.GONE
                }
            }

            // Fecha / Tiempo transcurrido
            val timeMillis = order.timestamp?.toDate()?.time ?: 0L
            val timeFormatted = formatTime(timeMillis)
            val itemsCount = order.items.sumOf { it.quantity }
            binding.tvDeliveryOrderTime.text = "🕒 $timeFormatted • $itemsCount prod."

            // Distancia y Costo de Envío Badge
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
                binding.tvDeliveryDistanceFeeBadge.visibility = View.VISIBLE
                binding.tvDeliveryDistanceFeeBadge.text = "📏 $distStr • Envío S/ %.2f".format(Locale.getDefault(), fee)
            } else {
                binding.tvDeliveryDistanceFeeBadge.visibility = View.VISIBLE
                binding.tvDeliveryDistanceFeeBadge.text = "🛵 Envío S/ %.2f".format(Locale.getDefault(), fee)
            }

            // Datos del Cliente
            val customerName = if (order.customerName.isNotBlank()) order.customerName else "Cliente Yalazas"
            val customerPhone = if (order.phone.isNotBlank()) order.phone else "Sin teléfono"
            binding.tvDeliveryCustomerName.text = customerName
            binding.tvDeliveryCustomerPhone.text = "📞 $customerPhone"

            // Botones Modernos de Contacto Rápido (Llamar y WhatsApp)
            binding.btnDeliveryCall.setOnClickListener {
                if (order.phone.isNotBlank()) {
                    val dialIntent = Intent(Intent.ACTION_DIAL, "tel:${order.phone}".toUri())
                    context.startActivity(dialIntent)
                } else {
                    Toast.makeText(context, "No hay teléfono registrado para este cliente", Toast.LENGTH_SHORT).show()
                }
            }

            binding.btnDeliveryWhatsApp.setOnClickListener {
                if (order.phone.isNotBlank()) {
                    openWhatsApp(context, order.phone, "¡Hola $customerName! Soy el repartidor de Yalazas. Estoy en camino con tu pedido ($numStr) 🛵🍗")
                } else {
                    Toast.makeText(context, "No hay teléfono registrado para este cliente", Toast.LENGTH_SHORT).show()
                }
            }

            // Dirección y Referencia
            val addressText = if (order.address.isNotBlank()) order.address else "Dirección no especificada"
            binding.tvDeliveryAddress.text = addressText

            if (order.reference.isNotBlank()) {
                binding.tvDeliveryReference.visibility = View.VISIBLE
                binding.tvDeliveryReference.text = "Ref: ${order.reference}"
            } else {
                binding.tvDeliveryReference.visibility = View.GONE
            }

            if (order.comment.isNotBlank()) {
                binding.tvDeliveryKitchenNotes.visibility = View.VISIBLE
                binding.tvDeliveryKitchenNotes.text = "📝 Nota: ${order.comment}"
            } else {
                binding.tvDeliveryKitchenNotes.visibility = View.GONE
            }

            // Resumen de Productos
            val itemsSummary = order.items.joinToString(" + ") { item ->
                val sauces = if (item.selectedSauces.isNotEmpty()) " (${item.selectedSauces.joinToString(", ")})" else ""
                "${item.quantity}x ${item.name}$sauces"
            }
            binding.tvDeliveryItemsSummary.text = if (itemsSummary.isNotBlank()) "🍗 $itemsSummary" else "🍗 Pedido Yalazas"

            // Método de Pago y Total
            val method = order.paymentMethod.uppercase()
            when {
                method.contains("EFECTIVO") -> {
                    binding.tvDeliveryPaymentMethodIcon.text = "💵"
                    binding.tvDeliveryPaymentMethodTitle.text = "Pago en Efectivo"
                    if (order.paymentAmount > 0) {
                        val changeStr = String.format(Locale.getDefault(), "%.2f", order.paymentChange)
                        val amountStr = String.format(Locale.getDefault(), "%.2f", order.paymentAmount)
                        binding.tvDeliveryPaymentDetail.text = "Paga con: S/ $amountStr • Vuelto: S/ $changeStr"
                    } else {
                        binding.tvDeliveryPaymentDetail.text = "Cobrar en efectivo al entregar"
                    }
                    binding.tvDeliveryPaymentDetail.visibility = View.VISIBLE
                }
                method.contains("YAPE") -> {
                    binding.tvDeliveryPaymentMethodIcon.text = "📱"
                    binding.tvDeliveryPaymentMethodTitle.text = "Pago por Yape"
                    val payStatus = if (order.paymentStatus.isNotBlank()) order.paymentStatus else "PENDIENTE"
                    binding.tvDeliveryPaymentDetail.text = "Estado: $payStatus (Verificar en app)"
                    binding.tvDeliveryPaymentDetail.visibility = View.VISIBLE
                }
                method.contains("PLIN") -> {
                    binding.tvDeliveryPaymentMethodIcon.text = "🔵"
                    binding.tvDeliveryPaymentMethodTitle.text = "Pago por Plin"
                    val payStatus = if (order.paymentStatus.isNotBlank()) order.paymentStatus else "PENDIENTE"
                    binding.tvDeliveryPaymentDetail.text = "Estado: $payStatus (Verificar en app)"
                    binding.tvDeliveryPaymentDetail.visibility = View.VISIBLE
                }
                method.contains("TRANSF") -> {
                    binding.tvDeliveryPaymentMethodIcon.text = "🏦"
                    binding.tvDeliveryPaymentMethodTitle.text = "Transferencia Bancaria"
                    val payStatus = if (order.paymentStatus.isNotBlank()) order.paymentStatus else "PENDIENTE"
                    binding.tvDeliveryPaymentDetail.text = "Estado: $payStatus"
                    binding.tvDeliveryPaymentDetail.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvDeliveryPaymentMethodIcon.text = "💳"
                    binding.tvDeliveryPaymentMethodTitle.text = order.paymentMethod.ifBlank { "Pago Contra Entrega" }
                    binding.tvDeliveryPaymentDetail.visibility = View.GONE
                }
            }

            binding.tvDeliveryTotalAmount.text = String.format(Locale.getDefault(), "S/ %.2f", order.total)

            // Navegación GPS
            binding.btnDeliveryNavigateGps.setOnClickListener {
                openGpsNavigation(context, order)
            }
        }

        private fun updateCardStroke(isSelected: Boolean) {
            if (isSelected) {
                binding.cardDeliveryOrder.strokeColor = 0xFF00E676.toInt()
                binding.cardDeliveryOrder.strokeWidth = 4
                binding.cardDeliveryOrder.setCardBackgroundColor(0xFF193826.toInt())
            } else {
                binding.cardDeliveryOrder.strokeColor = 0xFF2A4838.toInt()
                binding.cardDeliveryOrder.strokeWidth = 2
                binding.cardDeliveryOrder.setCardBackgroundColor(0xFF14241C.toInt())
            }
        }

        private fun formatTime(timestamp: Long): String {
            if (timestamp <= 0) return "Reciente"
            val diff = System.currentTimeMillis() - timestamp
            val minutes = (diff / (1000 * 60)).toInt()
            return when {
                minutes < 1 -> "Hace un momento"
                minutes < 60 -> "Hace $minutes min"
                else -> {
                    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
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
    }
}
