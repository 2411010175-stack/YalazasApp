package com.yalazas.app.ui.cocina

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentCocinaBinding
import com.yalazas.app.databinding.DialogoConfirmacionCocinaBinding
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.model.Role
import com.yalazas.app.ui.dialogos.DialogoCerrarSesion
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioPedidos
import com.yalazas.app.repositorio.ServiciosFirebase

/** Panel de cocina: pedidos activos y listos, sincronizados con Firestore. */
class CocinaDashboardFragment : Fragment(R.layout.fragment_cocina) {
    private enum class SeccionCocina { NUEVOS, EN_COCINA, LISTOS }
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
    private var seccion = SeccionCocina.NUEVOS
    private var tiemposDeEstado: Map<String, Long> = emptyMap()
    private var cargaCompletada = false
    private var mensajeError: String? = null
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
        seccion = savedInstanceState?.getString(CLAVE_SECCION)?.let {
            runCatching { SeccionCocina.valueOf(it) }.getOrNull()
        } ?: SeccionCocina.NUEVOS
        cargaCompletada = false
        mensajeError = null
        todos = emptyList()
        tiemposDeEstado = emptyMap()
        primeraCarga = true
        idsActivos = emptySet()
        val nombre = RepositorioAutenticacion.currentProfile?.name.orEmpty().ifBlank { "cocinero" }
        binding.tvSaludoCocina.text = "Hola, $nombre · Cocina en vivo"
        adaptador = AdaptadorComandasCocina(::confirmarEstado)
        binding.listaCocina.layoutManager = GridLayoutManager(requireContext(), columnas())
        binding.listaCocina.adapter = adaptador
        binding.navNuevosCocina.setOnClickListener { cambiarSeccion(SeccionCocina.NUEVOS) }
        binding.navEnCocina.setOnClickListener { cambiarSeccion(SeccionCocina.EN_COCINA) }
        binding.navListosCocina.setOnClickListener { cambiarSeccion(SeccionCocina.LISTOS) }
        mostrarPedidos()
        binding.btnSalirCocina.setOnClickListener {
            DialogoCerrarSesion.crear("¿Deseas salir del panel de cocina?")
                .definirAccionConfirmacion {
                    RepositorioAutenticacion.cerrarSesion()
                    findNavController().navigate(
                        R.id.nav_login, null,
                        NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                    )
                }
                .show(parentFragmentManager, DialogoCerrarSesion.TAG)
        }
        reloj.post(actualizarReloj)
        escucharPedidos()
    }

    private fun columnas(): Int = if (resources.configuration.screenWidthDp >= 600) 2 else 1

    private fun cambiarSeccion(nueva: SeccionCocina) {
        seccion = nueva
        binding.listaCocina.scrollToPosition(0)
        mostrarPedidos()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(CLAVE_SECCION, seccion.name)
        super.onSaveInstanceState(outState)
    }

    /** La consulta coincide con el límite de lectura de las reglas de cocina. */
    private fun escucharPedidos() {
        escucha?.remove()
        escucha = ServiciosFirebase.firestore.collection("orders")
            .whereIn("status", listOf("PENDIENTE", "EN_PROCESO", "LISTO_PARA_ENTREGA"))
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener
                binding.progresoCocina.visibility = View.GONE
                if (error != null) {
                    cargaCompletada = true
                    mensajeError = "No se pudieron cargar las comandas. Revisa la conexión o los permisos."
                    mostrarPedidos()
                    mostrarAviso("No se pudieron cargar las comandas", error = true)
                    return@addSnapshotListener
                }
                cargaCompletada = true
                mensajeError = null
                todos = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.copy(id = doc.id)
                }?.sortedBy { it.timestamp?.toDate()?.time ?: Long.MAX_VALUE } ?: emptyList()
                tiemposDeEstado = snapshot?.documents?.associate { doc ->
                    doc.id to (doc.getLong("updatedAt") ?: 0L)
                }.orEmpty()
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
        val nuevos = todos.filter { OrderStatus.desdeTexto(it.status) == OrderStatus.PENDIENTE }
        val enCocina = todos.filter { OrderStatus.desdeTexto(it.status) == OrderStatus.EN_PROCESO }
        val listos = todos.filter { OrderStatus.desdeTexto(it.status) == OrderStatus.LISTO_PARA_ENTREGA }
        binding.tvTituloListaCocina.text = when (seccion) {
            SeccionCocina.NUEVOS -> "Por preparar"
            SeccionCocina.EN_COCINA -> "En cocina"
            SeccionCocina.LISTOS -> "Listos para entrega"
        }
        binding.tvSubtituloListaCocina.text = when (seccion) {
            SeccionCocina.NUEVOS -> "Los pedidos más antiguos aparecen primero"
            SeccionCocina.EN_COCINA -> "Pedidos en preparación"
            SeccionCocina.LISTOS -> "En espera de servicio o recojo"
        }
        binding.tvNuevosCocina.text = if (cargaCompletada && mensajeError == null)
            nuevos.size.toString() else "—"
        binding.tvEnProcesoCocina.text = if (cargaCompletada && mensajeError == null)
            enCocina.size.toString() else "—"
        binding.tvListosCocina.text = if (cargaCompletada && mensajeError == null) listos.size.toString() else "—"
        actualizarNavegacion()
        if (!cargaCompletada) return
        if (mensajeError != null) {
            adaptador.mostrar(emptyList(), emptyMap())
            binding.tvVacioCocina.text = mensajeError
            binding.tvVacioCocina.visibility = View.VISIBLE
            return
        }
        val visibles = when (seccion) {
            SeccionCocina.NUEVOS -> nuevos
            SeccionCocina.EN_COCINA -> enCocina
            SeccionCocina.LISTOS -> listos
        }
        adaptador.mostrar(visibles, tiemposDeEstado)
        binding.tvVacioCocina.text = when (seccion) {
            SeccionCocina.NUEVOS -> "No hay pedidos por preparar 🍗"
            SeccionCocina.EN_COCINA -> "No hay pedidos en preparación 👨‍🍳"
            SeccionCocina.LISTOS -> "Aún no hay pedidos listos 📦"
        }
        binding.tvVacioCocina.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Tres accesos claros a las etapas que cocina puede consultar. */
    private fun actualizarNavegacion() {
        val inactivo = ContextCompat.getColor(requireContext(), R.color.cocina_text_secondary)
        listOf(
            Triple(seccion == SeccionCocina.NUEVOS, binding.navNuevosCocina,
                binding.textoNavNuevosCocina to binding.iconoNavNuevosCocina),
            Triple(seccion == SeccionCocina.EN_COCINA, binding.navEnCocina,
                binding.textoNavEnCocina to binding.iconoNavEnCocina),
            Triple(seccion == SeccionCocina.LISTOS, binding.navListosCocina,
                binding.textoNavListosCocina to binding.iconoNavListosCocina)
        ).forEachIndexed { indice, (activo, contenedor, elementos) ->
            val color = if (activo) ContextCompat.getColor(requireContext(),
                if (indice == 2) R.color.yalaza_lime_dark else R.color.yalaza_pink) else inactivo
            contenedor.setBackgroundResource(if (activo)
                (if (indice == 2) R.drawable.bg_cocina_nav_lima else R.drawable.bg_cocina_nav_rosa)
                else 0)
            contenedor.isSelected = activo
            elementos.first.setTextColor(color)
            elementos.second.setColorFilter(color)
        }
    }

    private fun confirmarEstado(pedido: Order, destino: OrderStatus) {
        if (actualizando) return
        val reabrir = OrderStatus.desdeTexto(pedido.status) == OrderStatus.LISTO_PARA_ENTREGA
        val codigo = pedido.id.takeLast(6).uppercase()
        val titulo = when {
            reabrir -> "Reabrir comanda"
            destino == OrderStatus.EN_PROCESO -> "Iniciar preparación"
            else -> "Marcar pedido listo"
        }
        val mensaje = when {
            reabrir -> "¿Necesitas corregir el pedido #$codigo?"
            destino == OrderStatus.EN_PROCESO -> "¿Vas a empezar a preparar el pedido #$codigo?"
            pedido.serviceType == "MESA" -> "¿El pedido #$codigo está completo y listo para servir?"
            else -> "¿El pedido #$codigo ya está completo y empacado?"
        }
        val detalle = when {
            reabrir && pedido.serviceType == "MESA" -> "Dejará de estar disponible para el mesero."
            reabrir -> "Dejará de estar disponible para recoger."
            destino == OrderStatus.EN_PROCESO -> "El cliente ya no podrá cancelarlo."
            pedido.serviceType == "MESA" -> "El mesero podrá servirlo en la mesa."
            pedido.isDelivery -> "El repartidor podrá recogerlo."
            else -> "El cliente podrá recogerlo en el local."
        }
        mostrarConfirmacion(
            titulo = titulo,
            mensaje = mensaje,
            detalle = detalle,
            textoAccion = if (reabrir) "Reabrir" else if (destino == OrderStatus.EN_PROCESO) "Empezar" else "Marcar listo",
            icono = if (destino == OrderStatus.LISTO_PARA_ENTREGA) R.drawable.ic_check_circle else R.drawable.ic_receipt
        ) {
            actualizando = true
            RepositorioPedidos.cambiarEstadoCocina(pedido.id, destino) { result ->
                actualizando = false
                if (_binding == null) return@cambiarEstadoCocina
                result.onSuccess {
                    val texto = when {
                        reabrir -> "Pedido #$codigo reabierto para corrección"
                        destino == OrderStatus.EN_PROCESO -> "Pedido #$codigo en preparación"
                        pedido.serviceType == "MESA" -> "Pedido #$codigo listo para servir"
                        pedido.isDelivery -> "Pedido #$codigo listo para reparto"
                        else -> "Pedido #$codigo listo para recoger"
                    }
                    mostrarAviso(texto)
                }.onFailure {
                    mostrarAviso("No se pudo actualizar #$codigo. Revisa conexión o permisos.", error = true)
                }
            }
        }
    }

    /** Todas las acciones de cocina explican su efecto antes de confirmarse. */
    private fun mostrarConfirmacion(
        titulo: String,
        mensaje: String,
        detalle: String,
        textoAccion: String,
        icono: Int,
        alConfirmar: () -> Unit
    ) {
        val contenido = DialogoConfirmacionCocinaBinding.inflate(layoutInflater)
        contenido.tituloConfirmacionCocina.text = titulo
        contenido.mensajeConfirmacionCocina.text = mensaje
        contenido.detalleConfirmacionCocina.text = detalle
        contenido.iconoConfirmacionCocina.setImageResource(icono)
        val dialogo = MaterialAlertDialogBuilder(requireContext(), R.style.Cocina_Dialogo)
            .setView(contenido.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(textoAccion) { _, _ -> alConfirmar() }
            .show()
        dialogo.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.cocina_nav_active))
        dialogo.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.cocina_text_secondary))
    }

    /** Aviso breve encima de la navegación, con un color distinto para errores. */
    private fun mostrarAviso(mensaje: String, error: Boolean = false) {
        if (_binding == null) return
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).apply {
            setAnchorView(binding.navCocina)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            view.background = ContextCompat.getDrawable(
                requireContext(),
                if (error) R.drawable.bg_cocina_aviso_error else R.drawable.bg_cocina_aviso
            )
            (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { margenes ->
                val espacio = (16 * resources.displayMetrics.density).toInt()
                margenes.marginStart = espacio
                margenes.marginEnd = espacio
                view.layoutParams = margenes
            }
            show()
        }
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

    companion object {
        private const val CLAVE_SECCION = "seccion_cocina"
    }
}
