package com.yalazas.app.ui.mesero

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yalazas.app.R
import com.yalazas.app.databinding.ItemPedidoMesaBinding
import com.yalazas.app.databinding.ItemMesaSalonBinding
import com.yalazas.app.databinding.ItemProductoMesaBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.model.Product
import com.yalazas.app.utilidades.ImagenCatalogo
import java.text.SimpleDateFormat
import java.util.Locale

/** Estado visual de una mesa calculado a partir de las comandas recibidas. */
data class EstadoMesaSalon(val numero: Int, val estado: String, val detalle: String, val color: Int)

class AdaptadorMesasSalon(private val alSeleccionar: ((Int) -> Unit)? = null) :
    RecyclerView.Adapter<AdaptadorMesasSalon.MesaHolder>() {
    private var mesas = emptyList<EstadoMesaSalon>()
    private var seleccionada = 0

    fun mostrar(nuevas: List<EstadoMesaSalon>, numeroSeleccionado: Int = 0) {
        mesas = nuevas
        seleccionada = numeroSeleccionado
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MesaHolder =
        MesaHolder(ItemMesaSalonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = mesas.size

    override fun onBindViewHolder(holder: MesaHolder, position: Int) = holder.mostrar(mesas[position])

    inner class MesaHolder(private val binding: ItemMesaSalonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(mesa: EstadoMesaSalon) {
            val color = ContextCompat.getColor(binding.root.context, mesa.color)
            binding.tvNumeroMesaSalon.text = "Mesa ${mesa.numero}"
            binding.tvEstadoMesaSalon.text = mesa.detalle
            binding.tvEstadoMesaSalon.setTextColor(color)
            val elegida = alSeleccionar != null && mesa.numero == seleccionada
            binding.cardMesaSalon.setStrokeColor(ContextCompat.getColor(binding.root.context,
                if (elegida) R.color.yalaza_pink else mesa.color))
            binding.cardMesaSalon.strokeWidth = (binding.root.resources.displayMetrics.density *
                if (elegida) 2f else 1f).toInt()
            binding.cardMesaSalon.setCardBackgroundColor(ContextCompat.getColor(binding.root.context,
                if (elegida) R.color.mesero_ready_surface else R.color.cocina_surface))
            binding.iconoMesaSalon.setColorFilter(color)
            binding.root.contentDescription = "Mesa ${mesa.numero}, ${mesa.estado}. ${mesa.detalle}"
            binding.root.isClickable = alSeleccionar != null
            binding.root.isFocusable = alSeleccionar != null
            binding.root.setOnClickListener { alSeleccionar?.invoke(mesa.numero) }
        }
    }
}

/** Carta de Firestore, con el precio final calculado por las promociones existentes. */
class AdaptadorProductosMesa(private val alElegir: (Product) -> Unit) :
    RecyclerView.Adapter<AdaptadorProductosMesa.ProductoHolder>() {
    private var productos: List<Product> = emptyList()

    fun mostrar(nuevos: List<Product>) {
        productos = nuevos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoHolder =
        ProductoHolder(ItemProductoMesaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = productos.size
    override fun onBindViewHolder(holder: ProductoHolder, position: Int) = holder.mostrar(productos[position])

    inner class ProductoHolder(private val binding: ItemProductoMesaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(producto: Product) {
            binding.tvNombreProductoMesa.text = producto.name
            binding.tvPrecioProductoMesa.text = String.format(Locale.getDefault(), "S/ %.2f", producto.price)
            val desc = producto.description.ifBlank { producto.tagline }
            if (desc.isNotBlank()) {
                binding.tvDescripcionProductoMesa.text = desc
                binding.tvDescripcionProductoMesa.visibility = View.VISIBLE
            } else {
                binding.tvDescripcionProductoMesa.visibility = View.GONE
            }
            if (producto.imageUrl.isNotBlank()) {
                binding.ivProductoMesa.load(ImagenCatalogo.miniatura(producto.imageUrl, 160, 160)) {
                    crossfade(true)
                    size(160, 160)
                    placeholder(R.drawable.welcome_food_logo)
                    error(R.drawable.welcome_food_logo)
                }
            } else {
                binding.ivProductoMesa.setImageResource(R.drawable.welcome_food_logo)
            }
            binding.btnAgregarProductoMesa.setOnClickListener { alElegir(producto) }
        }
    }
}

/** Cada tarjeta representa una comanda de mesa, con cobro habilitado solo cuando cocina la termina. */
class AdaptadorPedidosMesa(private val alCobrar: (Order) -> Unit) :
    RecyclerView.Adapter<AdaptadorPedidosMesa.PedidoHolder>() {
    private var pedidos: List<Order> = emptyList()
    private var historial = false
    private var uidMesero = ""
    private var nombreMesero = ""

    fun mostrar(nuevos: List<Order>, enHistorial: Boolean, uid: String, nombre: String) {
        pedidos = nuevos
        historial = enHistorial
        uidMesero = uid
        nombreMesero = nombre
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PedidoHolder =
        PedidoHolder(ItemPedidoMesaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = pedidos.size
    override fun onBindViewHolder(holder: PedidoHolder, position: Int) = holder.mostrar(pedidos[position])

    inner class PedidoHolder(private val binding: ItemPedidoMesaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(pedido: Order) {
            val estado = OrderStatus.desdeTexto(pedido.status)
            val hora = pedido.timestamp?.toDate()?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
            }.orEmpty()
            binding.tvMesaPedido.text = "Mesa ${pedido.tableNumber}"
            val horaVisible = (if (historial) pedido.paymentVerifiedAt else pedido.timestamp)?.toDate()?.let {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
            }.orEmpty()
            binding.tvClientePedidoMesa.text = if (historial) {
                val verificador = if (pedido.paymentVerifiedBy == uidMesero) nombreMesero else "otro mesero"
                "Confirmado por: $verificador" + if (horaVisible.isNotBlank()) " · $horaVisible" else ""
            } else listOf(pedido.customerName, hora).filter { it.isNotBlank() }.joinToString(" · ")
            binding.tvEstadoPedidoMesa.text = when (estado) {
                OrderStatus.PENDIENTE -> "NUEVO"
                OrderStatus.EN_PROCESO -> "EN COCINA"
                OrderStatus.LISTO_PARA_ENTREGA -> "LISTO PARA SERVIR"
                OrderStatus.ENTREGADO -> "ENTREGADO"
                else -> estado.label.uppercase(Locale.getDefault())
            }
            val color = when (estado) {
                OrderStatus.PENDIENTE -> R.color.yalaza_yellow
                OrderStatus.EN_PROCESO -> R.color.admin_status_cooking
                OrderStatus.LISTO_PARA_ENTREGA -> R.color.yalaza_pink
                else -> R.color.cocina_text_secondary
            }
            binding.tvEstadoPedidoMesa.setTextColor(ContextCompat.getColor(binding.root.context, color))
            binding.root.setStrokeColor(ContextCompat.getColor(binding.root.context,
                if (estado == OrderStatus.LISTO_PARA_ENTREGA) R.color.yalaza_pink else R.color.cocina_border))
            binding.root.setCardBackgroundColor(ContextCompat.getColor(binding.root.context,
                if (estado == OrderStatus.LISTO_PARA_ENTREGA) R.color.mesero_ready_surface else R.color.cocina_surface))
            binding.tvPlatosPedidoMesa.text = pedido.items.joinToString("\n") {
                "${it.quantity} × ${it.description.ifBlank { it.name }}"
            }
            binding.tvNotaPedidoMesa.visibility = if (pedido.comment.isBlank()) View.GONE else View.VISIBLE
            binding.tvNotaPedidoMesa.text = "Nota: ${pedido.comment}"
            binding.tvTotalPedidoMesa.text = String.format(Locale.getDefault(), "S/ %.2f", pedido.total)
            binding.tvPagoPedidoMesa.text = if (pedido.paymentStatus == "PAGADO")
                "Pago comprobado · ${pedido.paymentMethod.lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }}"
                else "Pago pendiente"
            val puedeCobrar = estado == OrderStatus.LISTO_PARA_ENTREGA && pedido.paymentStatus == "PENDIENTE"
            binding.btnEntregarPedidoMesa.visibility = if (puedeCobrar) View.VISIBLE else View.GONE
            binding.btnEntregarPedidoMesa.setOnClickListener { alCobrar(pedido) }
            binding.contenedorPlatosPedidoMesa.visibility = if (historial) View.GONE else View.VISIBLE
            binding.tvNotaPedidoMesa.visibility = if (historial || pedido.comment.isBlank()) View.GONE else View.VISIBLE
            binding.btnVerDetallePedidoMesa.visibility = if (historial) View.VISIBLE else View.GONE
            binding.btnVerDetallePedidoMesa.text = "Ver detalle"
            binding.btnVerDetallePedidoMesa.setOnClickListener {
                val mostrar = binding.contenedorPlatosPedidoMesa.visibility != View.VISIBLE
                binding.contenedorPlatosPedidoMesa.visibility = if (mostrar) View.VISIBLE else View.GONE
                binding.tvNotaPedidoMesa.visibility = if (mostrar && pedido.comment.isNotBlank()) View.VISIBLE else View.GONE
                binding.btnVerDetallePedidoMesa.text = if (mostrar) "Ocultar detalle" else "Ver detalle"
            }
        }
    }
}
