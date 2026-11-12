package com.yalazas.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yalazas.app.R
import com.yalazas.app.databinding.ItemMesaAdminBinding

/** Cada tarjeta muestra una mesa configurada y si aún atiende comandas. */
data class MesaAdminVista(val numero: Int, val comandasActivas: Int)

class AdaptadorMesasAdmin(private val alQuitar: (Int) -> Unit) :
    RecyclerView.Adapter<AdaptadorMesasAdmin.MesaHolder>() {
    private var mesas: List<MesaAdminVista> = emptyList()
    private var operando = false

    fun mostrar(nuevas: List<MesaAdminVista>, enOperacion: Boolean) {
        mesas = nuevas
        operando = enOperacion
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MesaHolder = MesaHolder(
        ItemMesaAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = mesas.size

    override fun onBindViewHolder(holder: MesaHolder, position: Int) = holder.mostrar(mesas[position])

    inner class MesaHolder(private val binding: ItemMesaAdminBinding) : RecyclerView.ViewHolder(binding.root) {
        fun mostrar(mesa: MesaAdminVista) {
            val contexto = binding.root.context
            val ocupada = mesa.comandasActivas > 0
            val acento = ContextCompat.getColor(contexto,
                if (ocupada) R.color.yalaza_pink else R.color.insumos_verde)
            binding.tvNumeroMesaAdmin.text = "Mesa ${mesa.numero}"
            binding.tvEstadoMesaAdmin.text = if (ocupada) "Ocupada" else "Libre"
            binding.tvEstadoMesaAdmin.setTextColor(acento)
            binding.tvDetalleMesaAdmin.text = if (ocupada)
                "${mesa.comandasActivas} comanda${if (mesa.comandasActivas == 1) "" else "s"} en curso"
            else "Sin comandas activas"
            binding.iconoMesaAdmin.setColorFilter(acento)
            binding.cardMesaAdmin.strokeColor = acento
            binding.cardMesaAdmin.setCardBackgroundColor(ContextCompat.getColor(contexto,
                if (ocupada) R.color.mesero_ready_surface else R.color.admin_ui_surface))
            binding.btnQuitarMesaAdmin.isEnabled = !ocupada && !operando
            binding.btnQuitarMesaAdmin.text = if (ocupada) "En uso" else "Quitar"
            binding.btnQuitarMesaAdmin.setOnClickListener { alQuitar(mesa.numero) }
            binding.root.contentDescription = "Mesa ${mesa.numero}, ${if (ocupada) "ocupada" else "libre"}. ${binding.tvDetalleMesaAdmin.text}"
        }
    }
}
