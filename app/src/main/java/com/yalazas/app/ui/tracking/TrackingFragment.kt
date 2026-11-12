package com.yalazas.app.ui.tracking

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.MainActivity
import com.yalazas.app.R
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioUbicacionRepartidor
import com.yalazas.app.repositorio.ServiciosFirebase
import com.yalazas.app.repositorio.RepositorioPedidos
import com.yalazas.app.repositorio.RepositorioImagenes
import java.util.Date

/** Escucha los pedidos del cliente y permite cancelar los pendientes. */
class TrackingFragment : Fragment(R.layout.fragment_tracking) {

    private val db = ServiciosFirebase.firestore
    private var ordersListener: ListenerRegistration? = null
    private val escuchasUbicacion = mutableMapOf<String, ListenerRegistration>()
    private val temporizador = Handler(Looper.getMainLooper())
    private val refrescarEdad = object : Runnable {
        override fun run() {
            if (view != null) {
                adapter.refrescarEdadUbicaciones()
                temporizador.postDelayed(this, 30_000L)
            }
        }
    }
    private lateinit var adapter: AdaptadorSeguimientoCliente

    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var btnEmptyGoToMenu: MaterialButton
    private lateinit var tvActiveCountBadge: TextView
    private lateinit var rvOrders: RecyclerView
    private var pedidoParaComprobante: String? = null
    private var subiendoComprobante = false
    private val elegirComprobante = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val pedidoId = pedidoParaComprobante
        pedidoParaComprobante = null
        if (uri != null && pedidoId != null && isAdded) {
            subiendoComprobante = true
            Toast.makeText(requireContext(), "Subiendo comprobante...", Toast.LENGTH_SHORT).show()
            RepositorioImagenes.subirComprobante(pedidoId, requireContext().contentResolver, uri) { resultado ->
                subiendoComprobante = false
                if (!isAdded || view == null) return@subirComprobante
                resultado.onSuccess {
                    Toast.makeText(requireContext(), "Comprobante enviado a administración", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(requireContext(), error.localizedMessage ?: "No se pudo subir el comprobante", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progressBarTracking)
        layoutEmpty = view.findViewById(R.id.layoutEmptyTracking)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        btnEmptyGoToMenu = view.findViewById(R.id.btnEmptyGoToMenu)
        tvActiveCountBadge = view.findViewById(R.id.tvActiveOrdersCountBadge)
        rvOrders = view.findViewById(R.id.rvCustomerOrders)

        adapter = AdaptadorSeguimientoCliente(
            onCancelOrderClick = { order ->
                confirmarCancelacionCliente(order)
            },
            onViewDriverClick = { order ->
                DialogoUbicacionRepartidor.mostrar(parentFragmentManager, order)
            },
            onUploadProofClick = { order ->
                if (subiendoComprobante) {
                    Toast.makeText(requireContext(), "Espera a que termine la subida actual", Toast.LENGTH_SHORT).show()
                } else {
                    pedidoParaComprobante = order.id
                    elegirComprobante.launch("image/*")
                }
            }
        )

        rvOrders.layoutManager = LinearLayoutManager(requireContext())
        rvOrders.adapter = adapter

        btnEmptyGoToMenu.setOnClickListener {
            (activity as? MainActivity)?.irAlMenu() ?: findNavController().navigate(R.id.nav_menu)
        }

        escucharPedidosDelCliente()
    }

    override fun onResume() {
        super.onResume()
        temporizador.post(refrescarEdad)
    }

    override fun onPause() {
        temporizador.removeCallbacks(refrescarEdad)
        super.onPause()
    }

    /** Escucha solo los pedidos del usuario actual y actualiza la lista al cambiar Firestore. */
    private fun escucharPedidosDelCliente() {
        val currentUserId = RepositorioAutenticacion.currentProfile?.uid ?: RepositorioAutenticacion.currentUser?.uid

        if (currentUserId.isNullOrBlank()) {
            progressBar.visibility = View.GONE
            rvOrders.visibility = View.GONE
            tvActiveCountBadge.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            tvEmptyTitle.text = "Inicia sesión para ver tus pedidos"
            tvEmptyMessage.text = "Ingresa a tu cuenta para realizar seguimiento en tiempo real de tus pedidos."
            btnEmptyGoToMenu.text = "Iniciar Sesión"
            btnEmptyGoToMenu.setOnClickListener {
                findNavController().navigate(R.id.nav_login)
            }
            return
        }

        progressBar.visibility = View.VISIBLE
        ordersListener?.remove()

        ordersListener = db.collection("orders")
            .whereEqualTo("userId", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded || view == null) return@addSnapshotListener
                progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(requireContext(), "Error al cargar pedidos: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val orderList = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Order::class.java)?.apply {
                            id = doc.id
                        }
                    }.sortedByDescending { it.timestamp?.toDate() ?: Date(0) }

                    sincronizarUbicaciones(orderList)

                    val activeCount = orderList.count {
                        val statusEnum = OrderStatus.desdeTexto(it.status)
                        statusEnum in listOf(OrderStatus.PENDIENTE, OrderStatus.EN_PROCESO, OrderStatus.LISTO_PARA_ENTREGA, OrderStatus.EN_CAMINO)
                    }

                    if (activeCount > 0) {
                        tvActiveCountBadge.text = "$activeCount Activo${if (activeCount > 1) "s" else ""}"
                        tvActiveCountBadge.visibility = View.VISIBLE
                    } else {
                        tvActiveCountBadge.visibility = View.GONE
                    }

                    if (orderList.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                        rvOrders.visibility = View.GONE
                        tvEmptyTitle.text = "No tienes pedidos registrados"
                        tvEmptyMessage.text = "¡Aún no has realizado un pedido! Revisa nuestra carta de alitas, combos y promociones para comenzar."
                        btnEmptyGoToMenu.text = "Ver Menú de Alitas"
                        btnEmptyGoToMenu.setOnClickListener {
                            (activity as? MainActivity)?.irAlMenu() ?: findNavController().navigate(R.id.nav_menu)
                        }
                    } else {
                        layoutEmpty.visibility = View.GONE
                        rvOrders.visibility = View.VISIBLE
                        adapter.submitList(orderList)
                    }
                }
            }
    }

    /** Cada pedido activo escucha únicamente su propio documento de posición. */
    private fun sincronizarUbicaciones(pedidos: List<Order>) {
        val idsEnRuta = pedidos.filter {
            it.isDelivery && it.status == OrderStatus.EN_CAMINO.name && it.deliveryDriverId.isNotBlank()
        }.map { it.id }.toSet()

        (escuchasUbicacion.keys - idsEnRuta).forEach { id ->
            escuchasUbicacion.remove(id)?.remove()
            adapter.actualizarUbicacion(id, null)
            (parentFragmentManager.findFragmentByTag(DialogoUbicacionRepartidor.etiqueta(id))
                as? DialogoUbicacionRepartidor)?.dismissAllowingStateLoss()
        }
        (idsEnRuta - escuchasUbicacion.keys).forEach { id ->
            escuchasUbicacion[id] = RepositorioUbicacionRepartidor.observar(
                id,
                alCambiar = { posicion ->
                    if (isAdded && view != null) adapter.actualizarUbicacion(id, posicion)
                },
                alError = {
                    if (isAdded && view != null) adapter.indicarErrorUbicacion(id)
                }
            )
        }
    }

    /** Muestra la confirmación únicamente si el estado local todavía es pendiente. */
    private fun confirmarCancelacionCliente(order: Order) {
        // REGLA DE NEGOCIO: Validar nuevamente que el pedido esté en PENDIENTE
        if (!OrderStatus.puedeCancelarCliente(order.status)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cancelación no disponible")
                .setMessage("Tu pedido ya ingresó a preparación en cocina o se encuentra en camino, por lo que no es posible cancelarlo.")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("Entendido", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Cancelar este pedido?")
            .setMessage("¿Estás seguro de cancelar tu orden? Una vez cancelada no se procesará en cocina ni se enviará.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Sí, Cancelar Pedido") { _, _ ->
                cancelarPedido(order)
            }
            .setNegativeButton("No, Mantener Pedido", null)
            .show()
    }

    /** Delega la escritura al repositorio y muestra el resultado al cliente. */
    private fun cancelarPedido(order: Order) {
        progressBar.visibility = View.VISIBLE
        RepositorioPedidos.cancelarPedidoPropio(order) { result ->
            if (!isAdded || view == null) return@cancelarPedidoPropio
            progressBar.visibility = View.GONE
            result.onSuccess {
                Toast.makeText(requireContext(), "Tu pedido ha sido cancelado", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Error al cancelar pedido: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Evita recibir actualizaciones después de destruir la pantalla.
        ordersListener?.remove()
        ordersListener = null
        escuchasUbicacion.values.forEach { it.remove() }
        escuchasUbicacion.clear()
    }
}
