package com.yalazas.app.ui.admin

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yalazas.app.R
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import java.text.SimpleDateFormat
import java.util.Locale

/** Dibuja los pedidos de la lista de ventas administrativas. */
class AdaptadorVentas(
    private val onDetailsClick: (Order, String) -> Unit,
    private val onChangeStatusClick: (Order) -> Unit
) : ListAdapter<Pair<Order, String>, AdaptadorVentas.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_order_card, parent, false)
        return OrderViewHolder(view, onDetailsClick, onChangeStatusClick)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val (order, saleNumber) = getItem(position)
        holder.bind(order, saleNumber)
    }

    class OrderViewHolder(
        itemView: View,
        private val onDetailsClick: (Order, String) -> Unit,
        private val onChangeStatusClick: (Order) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val tvNumber: TextView = itemView.findViewById(R.id.tvOrderNumber)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvOrderDateTime)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvOrderStatus)
        private val tvCustomer: TextView = itemView.findViewById(R.id.tvOrderCustomer)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvOrderPhone)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvOrderAddress)
        private val tvSummary: TextView = itemView.findViewById(R.id.tvOrderSummary)
        private val tvTotal: TextView = itemView.findViewById(R.id.tvOrderTotal)
        private val btnDetails: MaterialButton = itemView.findViewById(R.id.btnOrderDetails)
        private val btnStatus: MaterialButton = itemView.findViewById(R.id.btnChangeStatus)

        fun bind(order: Order, saleNumber: String) {
            tvNumber.text = saleNumber
            val codigoPedido = if (order.id.isNotBlank()) "#${order.id.takeLast(5).uppercase(Locale.getDefault())}" else "#ORDEN"

            // Formato de Fecha y Hora
            val dateObj = order.timestamp?.toDate()
            if (dateObj != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault())
                tvDateTime.text = "$codigoPedido · ${sdf.format(dateObj)}"
            } else {
                tvDateTime.text = "$codigoPedido · Fecha no disponible"
            }

            // Datos del cliente
            tvCustomer.text = order.customerName.ifBlank { "Cliente Sin Nombre" }
            tvPhone.text = when {
                order.serviceType == "MESA" -> "🍽️ Atención en salón"
                order.phone.isNotBlank() -> "📞 ${order.phone}"
                else -> "📞 Sin tel."
            }
            tvAddress.text = if (order.address.isNotBlank()) {
                if (order.reference.isNotBlank()) "${order.address} (Ref: ${order.reference})" else order.address
            } else {
                "Recojo en tienda / Sin dirección"
            }

            // Resumen de productos
            if (order.items.isNotEmpty()) {
                tvSummary.visibility = View.VISIBLE
                tvSummary.text = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }
            } else {
                tvSummary.visibility = View.GONE
            }

            // Total
            tvTotal.text = "S/ ${String.format(Locale.US, "%.2f", order.total)}"

            val statusEnum = OrderStatus.desdeTexto(order.status)
            val allowedTransitions = OrderStatus.obtenerTransicionesPermitidas(order.status)
                .filterNot { order.serviceType == "MESA" && it == OrderStatus.EN_CAMINO }
            val estilo = PaletaEstadosVenta.para(statusEnum)
            val requiereRevisionPago = PaletaEstadosVenta.requiereRevisionPago(
                order.paymentMethod, order.paymentStatus, order.paymentProofStatus, order.status
            )
            tvTotal.setTextColor(color(if (statusEnum == OrderStatus.CANCELADO) R.color.admin_status_cancelled else R.color.admin_ui_amount))
            cardView.setCardBackgroundColor(color(if (requiereRevisionPago) R.color.admin_payment_review_card else estilo.tarjeta))
            cardView.strokeColor = color(if (requiereRevisionPago) R.color.admin_payment_review_border else estilo.borde)
            cardView.strokeWidth = itemView.resources.displayMetrics.density.times(if (requiereRevisionPago) 2f else 1f).toInt()
            tvStatus.setTextColor(color(estilo.textoEstado))
            tvStatus.background = createPillDrawable(color(estilo.fondoEstado))
            btnStatus.backgroundTintList = ColorStateList.valueOf(color(estilo.boton))

            when (statusEnum) {
                OrderStatus.CANCELADO -> {
                    tvStatus.text = "❌ CANCELADO"
                }
                OrderStatus.PENDIENTE -> {
                    tvStatus.text = "🟡 PENDIENTE"
                }
                OrderStatus.EN_PROCESO -> {
                    tvStatus.text = "👨‍🍳 EN COCINA"
                }
                OrderStatus.LISTO_PARA_ENTREGA -> {
                    tvStatus.text = "📦 LISTO"
                }
                OrderStatus.EN_CAMINO -> {
                    tvStatus.text = "🛵 EN CAMINO"
                }
                OrderStatus.ENTREGADO -> {
                    tvStatus.text = "✅ ENTREGADO"
                }
            }

            btnStatus.text = if (allowedTransitions.isEmpty()) "Cerrado" else "Estado"
            btnDetails.text = if (requiereRevisionPago) "Revisar" else "Detalles"
            btnDetails.contentDescription = if (requiereRevisionPago)
                "Revisar comprobante de pago de $saleNumber" else "Ver detalles de $saleNumber"
            btnDetails.backgroundTintList = ColorStateList.valueOf(color(
                if (requiereRevisionPago) R.color.admin_payment_review_button else R.color.admin_ui_button_secondary
            ))

            btnDetails.setOnClickListener { onDetailsClick(order, saleNumber) }
            btnStatus.setOnClickListener { onChangeStatusClick(order) }
        }

        private fun color(id: Int): Int = ContextCompat.getColor(itemView.context, id)

        private fun createPillDrawable(bgColor: Int): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(bgColor)
            }
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Pair<Order, String>>() {
        override fun areItemsTheSame(oldItem: Pair<Order, String>, newItem: Pair<Order, String>): Boolean =
            oldItem.first.id == newItem.first.id

        override fun areContentsTheSame(oldItem: Pair<Order, String>, newItem: Pair<Order, String>): Boolean =
            oldItem.first == newItem.first && oldItem.second == newItem.second
    }
}
