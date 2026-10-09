package com.yalazas.app.ui.admin

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

class AdminSalesAdapter(
    private val onDetailsClick: (Order, String) -> Unit,
    private val onChangeStatusClick: (Order) -> Unit
) : ListAdapter<Pair<Order, String>, AdminSalesAdapter.OrderViewHolder>(OrderDiffCallback()) {

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

            // Formato de Fecha y Hora
            val dateObj = order.timestamp?.toDate()
            if (dateObj != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault())
                tvDateTime.text = sdf.format(dateObj)
            } else {
                tvDateTime.text = "Fecha no disponible"
            }

            // Datos del cliente
            tvCustomer.text = order.customerName.ifBlank { "Cliente Sin Nombre" }
            tvPhone.text = if (order.phone.isNotBlank()) "📞 ${order.phone}" else "📞 Sin tel."
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

            // Estilos del Badge y Card por Estado
            val statusEnum = OrderStatus.fromString(order.status)
            val allowedTransitions = OrderStatus.getAllowedTransitions(order.status)

            when (statusEnum) {
                OrderStatus.CANCELADO -> {
                    cardView.setCardBackgroundColor(Color.parseColor("#201012"))
                    cardView.strokeColor = Color.parseColor("#FF5252")
                    tvStatus.text = "❌ CANCELADO"
                    tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#48151A"))
                }
                OrderStatus.PENDIENTE -> {
                    cardView.setCardBackgroundColor(Color.parseColor("#17201B"))
                    cardView.strokeColor = Color.parseColor("#FFD54F")
                    tvStatus.text = "🟡 PENDIENTE"
                    tvStatus.setTextColor(Color.parseColor("#FFD54F"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#383212"))
                }
                OrderStatus.EN_PROCESO -> {
                    cardView.setCardBackgroundColor(Color.parseColor("#0F1E24"))
                    cardView.strokeColor = Color.parseColor("#29B6F6")
                    tvStatus.text = "👨‍🍳 EN COCINA"
                    tvStatus.setTextColor(Color.parseColor("#29B6F6"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#10374A"))
                }
                OrderStatus.EN_CAMINO -> {
                    cardView.setCardBackgroundColor(Color.parseColor("#181222"))
                    cardView.strokeColor = Color.parseColor("#AB47BC")
                    tvStatus.text = "🛵 EN CAMINO"
                    tvStatus.setTextColor(Color.parseColor("#AB47BC"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#38194A"))
                }
                OrderStatus.ENTREGADO -> {
                    cardView.setCardBackgroundColor(Color.parseColor("#112217"))
                    cardView.strokeColor = Color.parseColor("#4CAF50")
                    tvStatus.text = "✅ ENTREGADO"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#143E23"))
                }
            }

            btnStatus.text = if (allowedTransitions.isEmpty()) "Cerrado" else "Estado"

            btnDetails.setOnClickListener { onDetailsClick(order, saleNumber) }
            btnStatus.setOnClickListener { onChangeStatusClick(order) }
        }

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
