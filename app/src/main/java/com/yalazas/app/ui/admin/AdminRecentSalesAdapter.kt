package com.yalazas.app.ui.admin

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
    val customerName: String = "",
    val phone: String = "",
    val address: String = "",
    val reference: String = "",
    val comment: String = "",
    val status: String = "PENDIENTE",
    val items: List<OrderItem> = emptyList(),
    val itemsSummary: String = ""
)

class AdminRecentSalesAdapter(
    private val onDetailsClick: (AdminSaleItem) -> Unit
) : RecyclerView.Adapter<AdminRecentSalesAdapter.SaleViewHolder>() {

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
            tvTime.text = sale.time
            tvTotal.text = "S/ ${String.format(Locale.US, "%.2f", sale.total)}"

            val statusEnum = OrderStatus.fromString(sale.status)
            when (statusEnum) {
                OrderStatus.CANCELADO -> {
                    // Estado Cancelado: Tema Rojo Destacado con puntito
                    card.setCardBackgroundColor(Color.parseColor("#261114"))
                    card.strokeColor = Color.parseColor("#FF5252")
                    tvStatus.text = "● CANCEL."
                    tvStatus.setTextColor(Color.parseColor("#FF5252"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#4A151B"))
                    tvDate.setTextColor(Color.parseColor("#FF8A80"))
                    tvTime.setTextColor(Color.parseColor("#FF8A80"))
                    tvTotal.setTextColor(Color.parseColor("#FF5252"))
                    btnDetails.background = createButtonDrawable(Color.parseColor("#6E1B24"))
                }
                OrderStatus.PENDIENTE -> {
                    // Estado Pendiente: Tema Amarillo Oro con puntito
                    card.setCardBackgroundColor(Color.parseColor("#1B1910"))
                    card.strokeColor = Color.parseColor("#FFD54F")
                    tvStatus.text = "● PENDIENTE"
                    tvStatus.setTextColor(Color.parseColor("#FFD54F"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#383212"))
                    tvDate.setTextColor(Color.parseColor("#FFE082"))
                    tvTime.setTextColor(Color.parseColor("#FFE082"))
                    tvTotal.setTextColor(Color.parseColor("#FFD54F"))
                    btnDetails.background = createButtonDrawable(Color.parseColor("#A66B08"))
                }
                OrderStatus.EN_PROCESO -> {
                    // Estado En Cocina: Tema Azul / Celeste con puntito
                    card.setCardBackgroundColor(Color.parseColor("#0C1B24"))
                    card.strokeColor = Color.parseColor("#29B6F6")
                    tvStatus.text = "● COCINA"
                    tvStatus.setTextColor(Color.parseColor("#29B6F6"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#10374A"))
                    tvDate.setTextColor(Color.parseColor("#81D4FA"))
                    tvTime.setTextColor(Color.parseColor("#81D4FA"))
                    tvTotal.setTextColor(Color.parseColor("#29B6F6"))
                    btnDetails.background = createButtonDrawable(Color.parseColor("#145B7F"))
                }
                OrderStatus.EN_CAMINO -> {
                    // Estado En Camino: Tema Púrpura / Repartidor con puntito
                    card.setCardBackgroundColor(Color.parseColor("#1B1024"))
                    card.strokeColor = Color.parseColor("#AB47BC")
                    tvStatus.text = "● CAMINO"
                    tvStatus.setTextColor(Color.parseColor("#AB47BC"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#38194A"))
                    tvDate.setTextColor(Color.parseColor("#CE93D8"))
                    tvTime.setTextColor(Color.parseColor("#CE93D8"))
                    tvTotal.setTextColor(Color.parseColor("#AB47BC"))
                    btnDetails.background = createButtonDrawable(Color.parseColor("#5A247A"))
                }
                OrderStatus.ENTREGADO -> {
                    // Estado Entregado: Tema Verde Éxito con puntito
                    card.setCardBackgroundColor(Color.parseColor("#0D1F15"))
                    card.strokeColor = Color.parseColor("#4CAF50")
                    tvStatus.text = "● ENTREG."
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    tvStatus.background = createPillDrawable(Color.parseColor("#143E23"))
                    tvDate.setTextColor(Color.parseColor("#A5D6A7"))
                    tvTime.setTextColor(Color.parseColor("#A5D6A7"))
                    tvTotal.setTextColor(Color.parseColor("#4CAF50"))
                    btnDetails.background = createButtonDrawable(Color.parseColor("#1B6330"))
                }
            }

            btnDetails.setOnClickListener {
                onDetailsClick(sale)
            }
        }

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
