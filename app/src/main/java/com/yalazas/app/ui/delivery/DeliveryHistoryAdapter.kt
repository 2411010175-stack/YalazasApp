package com.yalazas.app.ui.delivery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yalazas.app.databinding.ItemDeliveryHistoryRowBinding
import com.yalazas.app.model.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeliveryHistoryAdapter(
    private var orders: List<Order> = emptyList(),
    private val onItemClick: ((Order) -> Unit)? = null
) : RecyclerView.Adapter<DeliveryHistoryAdapter.HistoryViewHolder>() {

    fun updateOrders(newOrders: List<Order>) {
        orders = newOrders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemDeliveryHistoryRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class HistoryViewHolder(
        private val binding: ItemDeliveryHistoryRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            val numStr = "Pedido #${order.id.takeLast(6).uppercase()}"
            binding.tvHistoryOrderNumber.text = numStr

            val customerName = if (order.customerName.isNotBlank()) order.customerName else "Cliente Yalazas"
            val customerPhone = if (order.phone.isNotBlank()) order.phone else "Sin tel."
            binding.tvHistoryCustomer.text = "👤 $customerName • 📞 $customerPhone"

            val addressText = if (order.address.isNotBlank()) order.address else "Entrega completada"
            binding.tvHistoryAddress.text = "📍 $addressText"

            val method = order.paymentMethod.uppercase()
            when {
                method.contains("EFECTIVO") -> {
                    binding.tvHistoryPaymentMethodTag.text = "💵 EFECTIVO"
                    binding.tvHistoryPaymentMethodTag.setBackgroundColor(0xFF1D3E2B.toInt())
                    binding.tvHistoryPaymentMethodTag.setTextColor(0xFF00E676.toInt())
                }
                method.contains("YAPE") -> {
                    binding.tvHistoryPaymentMethodTag.text = "📱 YAPE"
                    binding.tvHistoryPaymentMethodTag.setBackgroundColor(0xFF381E48.toInt())
                    binding.tvHistoryPaymentMethodTag.setTextColor(0xFFE040FB.toInt())
                }
                method.contains("PLIN") -> {
                    binding.tvHistoryPaymentMethodTag.text = "🔵 PLIN"
                    binding.tvHistoryPaymentMethodTag.setBackgroundColor(0xFF0F324D.toInt())
                    binding.tvHistoryPaymentMethodTag.setTextColor(0xFF40C4FF.toInt())
                }
                method.contains("TRANSF") -> {
                    binding.tvHistoryPaymentMethodTag.text = "🏦 TRANSF."
                    binding.tvHistoryPaymentMethodTag.setBackgroundColor(0xFF1E3A3A.toInt())
                    binding.tvHistoryPaymentMethodTag.setTextColor(0xFF64FFDA.toInt())
                }
                else -> {
                    binding.tvHistoryPaymentMethodTag.text = "💳 ${order.paymentMethod.ifBlank { "COBRADO" }}"
                    binding.tvHistoryPaymentMethodTag.setBackgroundColor(0xFF223C2D.toInt())
                    binding.tvHistoryPaymentMethodTag.setTextColor(0xFFBACEC2.toInt())
                }
            }

            val timestampMillis = order.timestamp?.toDate()?.time ?: 0L
            val deliveredTime = if (timestampMillis > 0) {
                val sdf = SimpleDateFormat("dd/MM • hh:mm a", Locale.getDefault())
                "Entregado: ${sdf.format(Date(timestampMillis))}"
            } else {
                "Entregado con éxito"
            }
            binding.tvHistoryDeliveredTime.text = deliveredTime

            binding.tvHistoryTotalAmount.text = String.format(Locale.getDefault(), "S/ %.2f", order.total)

            binding.root.setOnClickListener {
                onItemClick?.invoke(order)
            }
        }
    }
}
