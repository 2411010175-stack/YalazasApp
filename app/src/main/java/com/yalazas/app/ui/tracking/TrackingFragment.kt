package com.yalazas.app.ui.tracking

import android.os.Bundle
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.MainActivity
import com.yalazas.app.R
import com.yalazas.app.model.AuditLog
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.repository.AuthRepository
import java.util.Date

class TrackingFragment : Fragment(R.layout.fragment_tracking) {

    private val db = FirebaseFirestore.getInstance()
    private var ordersListener: ListenerRegistration? = null
    private lateinit var adapter: CustomerTrackingAdapter

    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var btnEmptyGoToMenu: MaterialButton
    private lateinit var tvActiveCountBadge: TextView
    private lateinit var rvOrders: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progressBarTracking)
        layoutEmpty = view.findViewById(R.id.layoutEmptyTracking)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        btnEmptyGoToMenu = view.findViewById(R.id.btnEmptyGoToMenu)
        tvActiveCountBadge = view.findViewById(R.id.tvActiveOrdersCountBadge)
        rvOrders = view.findViewById(R.id.rvCustomerOrders)

        adapter = CustomerTrackingAdapter(
            onCancelOrderClick = { order ->
                confirmCustomerCancelOrder(order)
            }
        )

        rvOrders.layoutManager = LinearLayoutManager(requireContext())
        rvOrders.adapter = adapter

        btnEmptyGoToMenu.setOnClickListener {
            (activity as? MainActivity)?.navigateToMenu() ?: findNavController().navigate(R.id.nav_menu)
        }

        listenToCustomerOrdersRealtime()
    }

    private fun listenToCustomerOrdersRealtime() {
        val currentUserId = AuthRepository.currentProfile?.uid ?: AuthRepository.currentUser?.uid

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

                    val activeCount = orderList.count {
                        val statusEnum = OrderStatus.fromString(it.status)
                        statusEnum in listOf(OrderStatus.PENDIENTE, OrderStatus.EN_PROCESO, OrderStatus.EN_CAMINO)
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
                            (activity as? MainActivity)?.navigateToMenu() ?: findNavController().navigate(R.id.nav_menu)
                        }
                    } else {
                        layoutEmpty.visibility = View.GONE
                        rvOrders.visibility = View.VISIBLE
                        adapter.submitList(orderList)
                    }
                }
            }
    }

    private fun confirmCustomerCancelOrder(order: Order) {
        // REGLA DE NEGOCIO: Validar nuevamente que el pedido esté en PENDIENTE
        if (!OrderStatus.canCustomerCancel(order.status)) {
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
                cancelOrderInFirestore(order)
            }
            .setNegativeButton("No, Mantener Pedido", null)
            .show()
    }

    private fun cancelOrderInFirestore(order: Order) {
        progressBar.visibility = View.VISIBLE
        val currentUserId = AuthRepository.currentProfile?.uid ?: AuthRepository.currentUser?.uid ?: "CLIENTE"

        val updates = mapOf(
            "status" to OrderStatus.CANCELADO.key,
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection("orders").document(order.id)
            .update(updates)
            .addOnSuccessListener {
                // Registrar log de auditoría
                val log = AuditLog(
                    id = db.collection("auditoria").document().id,
                    usuarioAfectadoId = currentUserId,
                    usuarioAdministradorId = currentUserId,
                    accion = "CANCELACION_CLIENTE_PEDIDO",
                    rolAnterior = order.status,
                    rolNuevo = OrderStatus.CANCELADO.key
                )
                db.collection("auditoria").document(log.id).set(log)

                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Tu pedido ha sido cancelado", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error al cancelar pedido: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ordersListener?.remove()
        ordersListener = null
    }
}
