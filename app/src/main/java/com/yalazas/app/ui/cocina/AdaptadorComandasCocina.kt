package com.yalazas.app.ui.cocina

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yalazas.app.R
import com.yalazas.app.databinding.ItemComandaCocinaBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Tarjetas de comanda: lo importante para preparar se lee de un vistazo. */
class AdaptadorComandasCocina(
    private val alCambiarEstado: (Order, OrderStatus) -> Unit
) : RecyclerView.Adapter<AdaptadorComandasCocina.ComandaViewHolder>() {
    private var pedidos: List<Order> = emptyList()
    private var tiemposDeEstado: Map<String, Long> = emptyMap()

    fun mostrar(nuevos: List<Order>, tiempos: Map<String, Long>) {
        pedidos = nuevos
        tiemposDeEstado = tiempos
        notifyDataSetChanged()
    }

    fun actualizarTiempos() {
        if (pedidos.isNotEmpty()) notifyItemRangeChanged(0, pedidos.size, "tiempo")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComandaViewHolder {
        val binding = ItemComandaCocinaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ComandaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ComandaViewHolder, position: Int) = holder.mostrar(pedidos[position])
    override fun getItemCount(): Int = pedidos.size

    inner class ComandaViewHolder(private val binding: ItemComandaCocinaBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun mostrar(pedido: Order) {
            val estado = OrderStatus.desdeTexto(pedido.status)
            binding.tvCodigoComanda.text = "Pedido #${pedido.id.takeLast(6).uppercase(Locale.ROOT)}"
            val hora = pedido.timestamp?.toDate()?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
            } ?: "recién recibido"
            binding.tvServicioComanda.text =
                "${when {
                    pedido.serviceType == "MESA" -> "🍽️ Mesa ${pedido.tableNumber}"
                    pedido.isDelivery -> "🛵 Delivery"
                    else -> "🛍️ Retiro en local"
                }} · $hora"
            binding.tvEstadoComanda.text = when (estado) {
                OrderStatus.PENDIENTE -> "NUEVO"
                OrderStatus.EN_PROCESO -> "EN COCINA"
                OrderStatus.LISTO_PARA_ENTREGA -> "LISTO"
                else -> estado.label.uppercase(Locale.getDefault())
            }
            // La cantidad resalta como en una comanda física; nombres y notas siguen legibles.
            val platos = SpannableStringBuilder()
            pedido.items.forEachIndexed { indice, item ->
                if (indice > 0) platos.append("\n\n")
                val inicioCantidad = platos.length
                platos.append("${item.quantity} × ")
                platos.setSpan(
                    StyleSpan(Typeface.BOLD), inicioCantidad, platos.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                platos.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(binding.root.context, R.color.yalaza_lime_dark)),
                    inicioCantidad, platos.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val nombre = item.description.ifBlank { item.name }
                val salsas = if (item.description.isBlank() && item.selectedSauces.isNotEmpty())
                    "\n   Salsas: ${item.selectedSauces.joinToString(", ")}" else ""
                val nota = if (item.notes.isNotBlank()) "\n   📝 ${item.notes}" else ""
                platos.append("$nombre$salsas$nota")
            }
            binding.tvProductosComanda.text = if (platos.isEmpty()) "Sin productos detallados" else platos
            binding.tvNotaComanda.text = if (pedido.comment.isBlank()) "" else "📝 Nota: ${pedido.comment}"
            binding.tvNotaComanda.visibility = if (pedido.comment.isBlank()) View.GONE else View.VISIBLE
            binding.tvEsperaComanda.visibility = if (estado == OrderStatus.LISTO_PARA_ENTREGA)
                View.VISIBLE else View.GONE
            binding.tvEsperaComanda.text = if (pedido.serviceType == "MESA")
                "En espera del mesero · Listo para servir" else
                "En espera de recojo · Listo para despacho"
            val acento = when (estado) {
                OrderStatus.PENDIENTE -> R.color.yalaza_yellow
                OrderStatus.EN_PROCESO -> R.color.yalaza_pink
                OrderStatus.LISTO_PARA_ENTREGA -> R.color.yalaza_lime_dark
                else -> R.color.yalaza_border
            }
            val acentoColor = ContextCompat.getColor(binding.root.context, acento)
            binding.cardComanda.strokeColor = acentoColor
            binding.barraComanda.setBackgroundColor(acentoColor)
            binding.tvEstadoComanda.setTextColor(acentoColor)
            val siguiente = when (estado) {
                OrderStatus.PENDIENTE -> OrderStatus.EN_PROCESO
                OrderStatus.EN_PROCESO -> OrderStatus.LISTO_PARA_ENTREGA
                OrderStatus.LISTO_PARA_ENTREGA -> OrderStatus.EN_PROCESO
                else -> null
            }
            binding.btnAccionComanda.visibility = if (siguiente == null) View.GONE else View.VISIBLE
            binding.btnAccionComanda.text = when (estado) {
                OrderStatus.PENDIENTE -> "Empezar a preparar"
                OrderStatus.EN_PROCESO -> "¡Pedido listo! ✅"
                OrderStatus.LISTO_PARA_ENTREGA -> "Reabrir por error"
                else -> ""
            }
            val fondoBoton = when (estado) {
                OrderStatus.PENDIENTE -> R.color.yalaza_pink
                OrderStatus.EN_PROCESO -> R.color.yalaza_lime_dark
                OrderStatus.LISTO_PARA_ENTREGA -> R.color.yalaza_surface_light
                else -> R.color.yalaza_surface_light
            }
            binding.btnAccionComanda.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, fondoBoton)
            )
            binding.btnAccionComanda.setTextColor(
                if (estado == OrderStatus.EN_PROCESO) Color.BLACK else Color.WHITE
            )
            binding.btnAccionComanda.strokeColor = ColorStateList.valueOf(acentoColor)
            binding.btnAccionComanda.strokeWidth = if (estado == OrderStatus.LISTO_PARA_ENTREGA)
                (binding.root.resources.displayMetrics.density + 0.5f).toInt() else 0
            binding.btnAccionComanda.setOnClickListener {
                if (siguiente != null) alCambiarEstado(pedido, siguiente)
            }
            actualizarTiempo(pedido)
        }

        fun actualizarTiempo(pedido: Order) {
            val estado = OrderStatus.desdeTexto(pedido.status)
            // Cocina guarda updatedAt al iniciar, terminar o reabrir; los pedidos antiguos usan la creación.
            val inicio = if (estado == OrderStatus.PENDIENTE) pedido.timestamp?.toDate()?.time
                else tiemposDeEstado[pedido.id]?.takeIf { it > 0L } ?: pedido.timestamp?.toDate()?.time
            if (inicio == null) {
                binding.tvTiempoComanda.text = "Tiempo no disponible"
                return
            }
            val minutos = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - inicio).coerceAtLeast(0))
            val duracion = if (minutos >= 60)
                "${minutos / 60} h ${String.format(Locale.getDefault(), "%02d", minutos % 60)} min"
                else "$minutos min"
            binding.tvTiempoComanda.text = when (estado) {
                OrderStatus.PENDIENTE -> "Hace $duracion"
                OrderStatus.EN_PROCESO -> "En preparación\nhace $duracion"
                OrderStatus.LISTO_PARA_ENTREGA -> "Listo hace\n$duracion"
                else -> "Hace $duracion"
            }
            val color = when {
                minutos < 10 -> R.color.yalaza_lime_dark
                minutos < 20 -> R.color.yalaza_orange
                else -> R.color.yalaza_pink
            }
            binding.tvTiempoComanda.setTextColor(ContextCompat.getColor(binding.root.context, color))
        }
    }

    override fun onBindViewHolder(holder: ComandaViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("tiempo")) holder.actualizarTiempo(pedidos[position])
        else super.onBindViewHolder(holder, position, payloads)
    }
}
