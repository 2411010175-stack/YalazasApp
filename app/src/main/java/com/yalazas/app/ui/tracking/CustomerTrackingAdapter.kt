package com.yalazas.app.ui.tracking

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yalazas.app.R
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import java.text.SimpleDateFormat
import java.util.Locale

class CustomerTrackingAdapter(
    private val onCancelOrderClick: (Order) -> Unit
) : ListAdapter<Order, CustomerTrackingAdapter.TrackingViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer_tracking_order, parent, false)
        return TrackingViewHolder(view, onCancelOrderClick)
    }

    override fun onBindViewHolder(holder: TrackingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TrackingViewHolder(
        itemView: View,
        private val onCancelOrderClick: (Order) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvCode: TextView = itemView.findViewById(R.id.tvCustomerOrderCode)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvCustomerOrderDateTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvCustomerOrderStatus)

        // Stepper
        private val layoutStepper: View = itemView.findViewById(R.id.layoutTrackingStepper)
        private val layoutCancelledBanner: View = itemView.findViewById(R.id.layoutCancelledBanner)

        private val iconStep1: TextView = itemView.findViewById(R.id.iconStep1)
        private val iconStep2: TextView = itemView.findViewById(R.id.iconStep2)
        private val iconStep3: TextView = itemView.findViewById(R.id.iconStep3)
        private val iconStep4: TextView = itemView.findViewById(R.id.iconStep4)

        private val line1to2: View = itemView.findViewById(R.id.lineStep1to2)
        private val line2to3: View = itemView.findViewById(R.id.lineStep2to3)
        private val line3to4: View = itemView.findViewById(R.id.lineStep3to4)

        private val tvLabelStep1: TextView = itemView.findViewById(R.id.tvLabelStep1)
        private val tvLabelStep2: TextView = itemView.findViewById(R.id.tvLabelStep2)
        private val tvLabelStep3: TextView = itemView.findViewById(R.id.tvLabelStep3)
        private val tvLabelStep4: TextView = itemView.findViewById(R.id.tvLabelStep4)

        // Info
        private val tvAddress: TextView = itemView.findViewById(R.id.tvCustomerOrderAddress)
        private val tvSummary: TextView = itemView.findViewById(R.id.tvCustomerOrderSummary)
        private val tvTotal: TextView = itemView.findViewById(R.id.tvCustomerOrderTotal)

        // Cancel / Notice Sections
        private val layoutCancelSection: View = itemView.findViewById(R.id.layoutCancelActionSection)
        private val btnCancel: MaterialButton = itemView.findViewById(R.id.btnCancelOrder)

        private val layoutInfoNotice: View = itemView.findViewById(R.id.layoutInfoNoticeSection)
        private val tvInfoIcon: TextView = itemView.findViewById(R.id.tvInfoNoticeIcon)
        private val tvInfoMessage: TextView = itemView.findViewById(R.id.tvInfoNoticeMessage)

        fun bind(order: Order) {
            val context = itemView.context
            val orderCode = if (order.id.isNotBlank()) "#${order.id.takeLast(5).uppercase()}" else "#ORDEN"
            tvCode.text = "Orden $orderCode"

            // 1. Fecha y Hora
            val dateObj = order.timestamp?.toDate()
            if (dateObj != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault())
                tvDateTime.text = "📅 ${sdf.format(dateObj)}"
            } else {
                tvDateTime.text = "📅 Recién solicitada"
            }

            // 2. Estado
            val statusEnum = OrderStatus.fromString(order.status)
            when (statusEnum) {
                OrderStatus.PENDIENTE -> {
                    tvStatus.text = "🟡 PENDIENTE"
                    tvStatus.setTextColor(Color.parseColor("#FFD54F"))
                }
                OrderStatus.EN_PROCESO -> {
                    tvStatus.text = "👨‍🍳 EN COCINA"
                    tvStatus.setTextColor(Color.parseColor("#29B6F6"))
                }
                OrderStatus.EN_CAMINO -> {
                    tvStatus.text = "🛵 EN CAMINO"
                    tvStatus.setTextColor(Color.parseColor("#AB47BC"))
                }
                OrderStatus.ENTREGADO -> {
                    tvStatus.text = "✅ ENTREGADO"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
                OrderStatus.CANCELADO -> {
                    tvStatus.text = "❌ CANCELADO"
                    tvStatus.setTextColor(Color.parseColor("#FF5252"))
                }
            }

            // 3. Progreso Visual del Stepper
            if (statusEnum == OrderStatus.CANCELADO) {
                layoutStepper.visibility = View.GONE
                layoutCancelledBanner.visibility = View.VISIBLE
            } else {
                layoutStepper.visibility = View.VISIBLE
                layoutCancelledBanner.visibility = View.GONE

                val activeColor = Color.parseColor("#C5D92D")
                val inactiveColor = Color.parseColor("#2A3830")
                val textActive = ContextCompat.getColor(context, R.color.white)
                val textInactive = Color.parseColor("#9EADA4")

                // Paso 1: Confirmado (Siempre activo si la orden existe y no está cancelada)
                iconStep1.setBackgroundResource(R.drawable.bg_tracking_step_active)
                tvLabelStep1.setTextColor(textActive)

                // Paso 2: Cocina (Activo en EN_PROCESO, EN_CAMINO, ENTREGADO)
                val isStep2Active = statusEnum in listOf(OrderStatus.EN_PROCESO, OrderStatus.EN_CAMINO, OrderStatus.ENTREGADO)
                iconStep2.setBackgroundResource(if (isStep2Active) R.drawable.bg_tracking_step_active else R.drawable.bg_tracking_step_inactive)
                line1to2.setBackgroundColor(if (isStep2Active) activeColor else inactiveColor)
                tvLabelStep2.setTextColor(if (isStep2Active) textActive else textInactive)

                // Paso 3: En Camino (Activo en EN_CAMINO, ENTREGADO)
                val isStep3Active = statusEnum in listOf(OrderStatus.EN_CAMINO, OrderStatus.ENTREGADO)
                iconStep3.setBackgroundResource(if (isStep3Active) R.drawable.bg_tracking_step_active else R.drawable.bg_tracking_step_inactive)
                line2to3.setBackgroundColor(if (isStep3Active) activeColor else inactiveColor)
                tvLabelStep3.setTextColor(if (isStep3Active) textActive else textInactive)

                // Paso 4: Entregado (Activo solo en ENTREGADO)
                val isStep4Active = statusEnum == OrderStatus.ENTREGADO
                iconStep4.setBackgroundResource(if (isStep4Active) R.drawable.bg_tracking_step_active else R.drawable.bg_tracking_step_inactive)
                line3to4.setBackgroundColor(if (isStep4Active) activeColor else inactiveColor)
                tvLabelStep4.setTextColor(if (isStep4Active) textActive else textInactive)
            }

            // 4. Dirección
            tvAddress.text = if (order.address.isNotBlank()) {
                if (order.reference.isNotBlank()) "${order.address} (Ref: ${order.reference})" else order.address
            } else {
                "Recojo en local / Sin dirección registrada"
            }

            // 5. Resumen de Productos
            if (order.items.isNotEmpty()) {
                tvSummary.visibility = View.VISIBLE
                tvSummary.text = order.items.joinToString("\n") { item ->
                    val sauceText = if (item.selectedSauces.isNotEmpty()) " (${item.selectedSauces.joinToString(", ")})" else ""
                    val notesText = if (item.notes.isNotBlank()) " [Nota: ${item.notes}]" else ""
                    "• ${item.quantity}x ${item.name}$sauceText$notesText"
                }
            } else {
                tvSummary.visibility = View.GONE
            }

            // 6. Total
            tvTotal.text = "S/ ${String.format(Locale.US, "%.2f", order.total)}"

            // 7. REGLA DE CANCELACIÓN Y MENSAJES INFORMATIVOS
            val canCancel = OrderStatus.canCustomerCancel(order.status)

            if (canCancel) {
                // Estado PENDIENTE: El cliente PUEDE cancelar
                layoutCancelSection.visibility = View.VISIBLE
                layoutInfoNotice.visibility = View.GONE
                btnCancel.setOnClickListener {
                    onCancelOrderClick(order)
                }
            } else {
                // Estados EN_PROCESO, EN_CAMINO, ENTREGADO, CANCELADO: Cancelación BLOQUEADA
                layoutCancelSection.visibility = View.GONE
                layoutInfoNotice.visibility = View.VISIBLE

                when (statusEnum) {
                    OrderStatus.EN_PROCESO -> {
                        tvInfoIcon.text = "👨‍🍳"
                        tvInfoMessage.text = "¡Tu pedido ya está preparándose en cocina! 🍗 Por razones de preparación de alimentos, ya no es posible cancelarlo."
                    }
                    OrderStatus.EN_CAMINO -> {
                        tvInfoIcon.text = "🛵"
                        tvInfoMessage.text = "Tu pedido va en camino con el repartidor. ¡Ten listo tu método de pago si es contra entrega!"
                    }
                    OrderStatus.ENTREGADO -> {
                        tvInfoIcon.text = "✅"
                        tvInfoMessage.text = "Pedido entregado con éxito. ¡Buen provecho y gracias por elegir Yalazas!"
                    }
                    OrderStatus.CANCELADO -> {
                        tvInfoIcon.text = "❌"
                        tvInfoMessage.text = "Este pedido fue cancelado."
                    }
                    else -> {
                        tvInfoIcon.text = "ℹ️"
                        tvInfoMessage.text = "Pedido en estado: ${order.status}"
                    }
                }
            }
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean =
            oldItem == newItem
    }
}
