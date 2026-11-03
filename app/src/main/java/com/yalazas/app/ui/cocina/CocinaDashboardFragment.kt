package com.yalazas.app.ui.cocina

import android.media.AudioManager
import android.media.ToneGenerator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentCocinaBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.model.Role
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioPedidos
import com.yalazas.app.repositorio.ServiciosFirebase

/** Panel de cocina: pedidos activos y listos, sincronizados con Firestore. */
class CocinaDashboardFragment : Fragment(R.layout.fragment_cocina) {
    private var _binding: FragmentCocinaBinding? = null
    private val binding get() = _binding!!
    private var escucha: ListenerRegistration? = null
    private val reloj = Handler(Looper.getMainLooper())
    private val actualizarReloj = object : Runnable {
        override fun run() {
            if (_binding != null) {
                adaptador.actualizarTiempos()
                reloj.postDelayed(this, 60_000)
            }
        }
    }
    private lateinit var adaptador: AdaptadorComandasCocina
    private var todos: List<Order> = emptyList()
    private var mostrarListos = false
    private var primeraCarga = true
    private var idsActivos: Set<String> = emptySet()
    private var actualizando = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCocinaBinding.bind(view)
        if (Role.desdeTexto(RepositorioAutenticacion.currentProfile?.role.orEmpty()) != Role.COCINERO) {
            findNavController().navigate(R.id.nav_login)
            return
        }
        val nombre = RepositorioAutenticacion.currentProfile?.name.orEmpty().ifBlank { "cocinero" }
        binding.tvSaludoCocina.text = "Hola, $nombre · Comandas en tiempo real"
        adaptador = AdaptadorComandasCocina(::confirmarEstado)
        binding.listaCocina.layoutManager = GridLayoutManager(requireContext(), columnas())
        binding.listaCocina.adapter = adaptador
        binding.btnActivosCocina.setOnClickListener { mostrarListos = false; mostrarPedidos() }
        binding.btnListosCocina.setOnClickListener { mostrarListos = true; mostrarPedidos() }
        binding.btnSalirCocina.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cerrar sesión")
                .setMessage("¿Deseas salir del panel de cocina?")
                .setNegativeButton("Volver", null)
                .setPositiveButton("Salir") { _, _ ->
                    RepositorioAutenticacion.cerrarSesion()
                    findNavController().navigate(
                        R.id.nav_login, null,
                        NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                    )
                }.show()
        }
        reloj.post(actualizarReloj)
        escucharPedidos()
    }

    private fun columnas(): Int = if (resources.configuration.screenWidthDp >= 600) 2 else 1

    /** La consulta coincide con el límite de lectura de las reglas de cocina. */
    private fun escucharPedidos() {
        escucha?.remove()
        escucha = ServiciosFirebase.firestore.collection("orders")
            .whereIn("status", listOf("PENDIENTE", "EN_PROCESO", "LISTO_PARA_ENTREGA"))
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener
                binding.progresoCocina.visibility = View.GONE
                if (error != null) {
                    binding.tvVacioCocina.visibility = View.VISIBLE
                    binding.tvVacioCocina.text = "No se pudieron cargar las comandas. Revisa la conexión o los permisos."
                    Toast.makeText(requireContext(), error.localizedMessage, Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                todos = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.copy(id = doc.id)
                }?.sortedBy { it.timestamp?.toDate()?.time ?: Long.MAX_VALUE } ?: emptyList()
                val nuevosActivos = todos.filter {
                    OrderStatus.desdeTexto(it.status) in listOf(OrderStatus.PENDIENTE, OrderStatus.EN_PROCESO)
                }.map { it.id }.toSet()
                if (!primeraCarga && (nuevosActivos - idsActivos).isNotEmpty()) sonarComanda()
                primeraCarga = false
                idsActivos = nuevosActivos
                mostrarPedidos()
            }
    }

    private fun mostrarPedidos() {
        if (_binding == null) return
        val activos = todos.filter {
            OrderStatus.desdeTexto(it.status) in listOf(OrderStatus.PENDIENTE, OrderStatus.EN_PROCESO)
        }
        val listos = todos.filter { OrderStatus.desdeTexto(it.status) == OrderStatus.LISTO_PARA_ENTREGA }
        binding.btnActivosCocina.text = "Por preparar (${activos.size})"
        binding.btnListosCocina.text = "Listos (${listos.size})"
        val seleccionado = ContextCompat.getColor(requireContext(), R.color.yalaza_pink)
        val normal = ContextCompat.getColor(requireContext(), R.color.yalaza_surface)
        binding.btnActivosCocina.backgroundTintList = ColorStateList.valueOf(if (mostrarListos) normal else seleccionado)
        binding.btnListosCocina.backgroundTintList = ColorStateList.valueOf(if (mostrarListos) seleccionado else normal)
        binding.btnActivosCocina.setTextColor(android.graphics.Color.WHITE)
        binding.btnListosCocina.setTextColor(android.graphics.Color.WHITE)
        val visibles = if (mostrarListos) listos else activos
        adaptador.mostrar(visibles)
        binding.tvVacioCocina.text = if (mostrarListos)
            "Aún no hay pedidos listos 📦" else "No hay pedidos por preparar 🍗"
        binding.tvVacioCocina.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmarEstado(pedido: Order, destino: OrderStatus) {
        if (actualizando) return
        val reabrir = OrderStatus.desdeTexto(pedido.status) == OrderStatus.LISTO_PARA_ENTREGA
        val mensaje = when {
            reabrir -> "¿Reabrir el pedido #${pedido.id.takeLast(6)} para corregirlo? Dejará de estar disponible para reparto."
            destino == OrderStatus.EN_PROCESO -> "¿Empezar a preparar el pedido #${pedido.id.takeLast(6)}? El cliente ya no podrá cancelarlo."
            else -> "¿El pedido #${pedido.id.takeLast(6)} está completo y empacado? Aparecerá al repartidor."
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(when {
                reabrir -> "Reabrir comanda"
                destino == OrderStatus.EN_PROCESO -> "Iniciar preparación"
                else -> "Marcar pedido listo"
            })
            .setMessage(mensaje)
            .setNegativeButton("Volver", null)
            .setPositiveButton("Confirmar") { _, _ ->
                actualizando = true
                RepositorioPedidos.cambiarEstadoCocina(pedido.id, destino) { result ->
                    actualizando = false
                    if (_binding == null) return@cambiarEstadoCocina
                    result.onSuccess {
                        Toast.makeText(requireContext(), "Estado actualizado", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(requireContext(), it.localizedMessage ?: "No se pudo cambiar el estado", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    private fun sonarComanda() {
        val tono = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
        tono.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        reloj.postDelayed({ tono.release() }, 300)
    }

    override fun onDestroyView() {
        escucha?.remove()
        escucha = null
        reloj.removeCallbacks(actualizarReloj)
        _binding = null
        super.onDestroyView()
    }
}
