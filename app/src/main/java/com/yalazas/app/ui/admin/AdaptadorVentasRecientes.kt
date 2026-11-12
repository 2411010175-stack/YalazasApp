package com.yalazas.app.ui.admin

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.yalazas.app.R
import com.yalazas.app.model.OrderItem
import com.yalazas.app.model.OrderStatus
import java.util.Locale

data class AdminSaleItem(
    val id: String = "",
    val saleNumber: String = "00001",
    val date: String = "20/04/2026",
    val time: String = "20:05",
    val total: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val isDelivery: Boolean = false,
    val serviceType: String = "",
    val tableNumber: Int = 0,
    val customerName: String = "",
    val phone: String = "",
    val address: String = "",
    val reference: String = "",
    val comment: String = "",
    val status: String = "PENDIENTE",
    val paymentMethod: String = "",
    val paymentStatus: String = "",
    val paymentProofStatus: String = "",
    val items: List<OrderItem> = emptyList(),
    val itemsSummary: String = ""
)

/** Dibuja las ventas recientes del resumen administrativo. */
class AdaptadorVentasRecientes(
    private val onDetailsClick: (AdminSaleItem) -> Unit
) : RecyclerView.Adapter<AdaptadorVentasRecientes.SaleViewHolder>() {

    private var sales: List<AdminSaleItem> = emptyList()

    fun submitList(newList: List<AdminSaleItem>) {
        sales = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SaleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_recent_sale, parent, false)
        return SaleViewHolder(view)
    }

    override fun onBindViewHolder(holder: SaleViewHolder, position: Int) {
        holder.bind(sales[position])
    }

    override fun getItemCount(): Int = sales.size

    inner class SaleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.cardRecentSale)
        private val tvNumber: TextView = itemView.findViewById(R.id.tvSaleNumber)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvSaleStatus)
        private val tvDate: TextView = itemView.findViewById(R.id.tvSaleDate)
        private val tvTime: TextView = itemView.findViewById(R.id.tvSaleTime)
        private val tvTotal: TextView = itemView.findViewById(R.id.tvSaleTotal)
        private val btnDetails: Button = itemView.findViewById(R.id.btnSaleDetails)

        fun bind(sale: AdminSaleItem) {
            tvNumber.text = sale.saleNumber
            tvDate.text = sale.date
            val codigoPedido = if (sale.id.isNotBlank()) "#${sale.id.takeLast(5).uppercase(Locale.getDefault())}" else "#ORDEN"
            tvTime.text = "$codigoPedido · ${sale.time}"
            tvTotal.text = "S/ ${String.format(Locale.US, "%.2f", sale.total)}"

            val statusEnum = OrderStatus.desdeTexto(sale.status)
            val estilo = PaletaEstadosVenta.para(statusEnum)
            val requiereRevisionPago = PaletaEstadosVenta.requiereRevisionPago(
                sale.paymentMethod, sale.paymentStatus, sale.paymentProofStatus, sale.status
            )
            // El fondo y el botón distinguen el estado; la fecha conserva el mismo contraste.
            card.setCardBackgroundColor(color(if (requiereRevisionPago) R.color.admin_payment_review_card else estilo.tarjeta))
            card.strokeColor = color(if (requiereRevisionPago) R.color.admin_payment_review_border else estilo.borde)
            card.strokeWidth = (itemView.resources.displayMetrics.density * if (requiereRevisionPago) 2f else 1.5f).toInt()
            tvDate.setTextColor(color(R.color.admin_ui_text_secondary))
            tvTime.setTextColor(color(R.color.admin_ui_text_secondary))
            tvTotal.setTextColor(color(if (statusEnum == OrderStatus.CANCELADO) R.color.admin_status_cancelled else R.color.admin_ui_amount))
            btnDetails.background = createButtonDrawable(color(
                if (requiereRevisionPago) R.color.admin_payment_review_button else estilo.boton
            ))
            btnDetails.text = if (requiereRevisionPago) "REVISAR PAGO" else "DETALLES"
            btnDetails.contentDescription = if (requiereRevisionPago)
                "Revisar comprobante de pago de la venta ${sale.saleNumber}"
            else "Ver detalles de la venta ${sale.saleNumber}"
            tvStatus.setTextColor(color(estilo.textoEstado))
            tvStatus.background = createPillDrawable(color(estilo.fondoEstado))

            when (statusEnum) {
                OrderStatus.CANCELADO -> {
                    tvStatus.text = "● CANCEL."
                }
                OrderStatus.PENDIENTE -> {
                    tvStatus.text = "● PENDIENTE"
                }
                OrderStatus.EN_PROCESO -> {
                    tvStatus.text = "● COCINA"
                }
                OrderStatus.LISTO_PARA_ENTREGA -> {
                    tvStatus.text = "● LISTO"
                }
                OrderStatus.EN_CAMINO -> {
                    tvStatus.text = "● CAMINO"
                }
                OrderStatus.ENTREGADO -> {
                    tvStatus.text = "● ENTREG."
                }
            }

            btnDetails.setOnClickListener {
                onDetailsClick(sale)
            }
        }

        private fun color(id: Int): Int = ContextCompat.getColor(itemView.context, id)

        private fun createPillDrawable(bgColor: Int): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(bgColor)
            }
        }

        private fun createButtonDrawable(bgColor: Int): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f
                setColor(bgColor)
            }
        }
    }
}
