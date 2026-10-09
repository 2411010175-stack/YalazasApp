package com.yalazas.app.ui.admin

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yalazas.app.R
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import java.util.Locale

class AdminChangeStatusBottomSheet : BottomSheetDialogFragment() {

    private var orderId: String = ""
    private var saleNumber: String = ""
    private var currentStatus: String = "PENDIENTE"
    private var customerName: String = ""
    private var totalAmount: Double = 0.0

    private var selectedStatus: OrderStatus? = null

    var onStatusSelected: ((newStatus: String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            orderId = it.getString(ARG_ORDER_ID, "")
            saleNumber = it.getString(ARG_SALE_NUMBER, "")
            currentStatus = it.getString(ARG_CURRENT_STATUS, "PENDIENTE")
            customerName = it.getString(ARG_CUSTOMER_NAME, "")
            totalAmount = it.getDouble(ARG_TOTAL_AMOUNT, 0.0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_admin_change_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvOrderTitle = view.findViewById<TextView>(R.id.tvStatusSheetOrderTitle)
        val tvCustomerInfo = view.findViewById<TextView>(R.id.tvStatusSheetCustomerInfo)
        val tvCurrentBadge = view.findViewById<TextView>(R.id.tvStatusSheetCurrentBadge)
        val layoutStepper = view.findViewById<LinearLayout>(R.id.layoutSheetStepper)
        val layoutFinishedBanner = view.findViewById<LinearLayout>(R.id.layoutSheetFinishedBanner)
        val tvFinishedIcon = view.findViewById<TextView>(R.id.tvSheetFinishedIcon)
        val tvFinishedMessage = view.findViewById<TextView>(R.id.tvSheetFinishedMessage)
        val tvSectionTitle = view.findViewById<TextView>(R.id.tvStatusSheetSectionTitle)
        val tvSectionDesc = view.findViewById<TextView>(R.id.tvStatusSheetSectionDesc)
        val containerCards = view.findViewById<LinearLayout>(R.id.containerStatusCards)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnStatusSheetCancel)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnStatusSheetConfirm)

        // 1. Datos de Cabecera
        val orderLabel = if (saleNumber.startsWith("Venta") || saleNumber.startsWith("#")) {
            saleNumber
        } else if (saleNumber.isNotBlank()) {
            "Venta N° $saleNumber"
        } else {
            "Orden #${orderId.takeLast(5).uppercase(Locale.US)}"
        }
        tvOrderTitle.text = orderLabel

        val clientLabel = customerName.ifBlank { "Cliente" }
        tvCustomerInfo.text = "👤 $clientLabel • Total: S/ ${String.format(Locale.US, "%.2f", totalAmount)}"

        // Badge de estado actual
        updateStatusBadge(tvCurrentBadge, currentStatus)

        // Stepper Visual
        if (OrderStatus.fromString(currentStatus) == OrderStatus.CANCELADO) {
            layoutStepper.visibility = View.GONE
        } else {
            layoutStepper.visibility = View.VISIBLE
            updateMiniStepper(view, currentStatus)
        }

        // 2. Calcular Transiciones Permitidas
        val allowedTransitions = OrderStatus.getAllowedTransitions(currentStatus)

        if (allowedTransitions.isEmpty()) {
            // Orden cerrada (ENTREGADO o CANCELADO)
            layoutFinishedBanner.visibility = View.VISIBLE
            tvSectionTitle.visibility = View.GONE
            tvSectionDesc.visibility = View.GONE
            containerCards.visibility = View.GONE
            btnConfirm.visibility = View.GONE
            btnCancel.text = "Cerrar"

            val currentEnum = OrderStatus.fromString(currentStatus)
            if (currentEnum == OrderStatus.ENTREGADO) {
                tvFinishedIcon.text = "✅"
                tvFinishedMessage.text = "Este pedido ya se encuentra COMPLETADO y ENTREGADO con éxito."
            } else {
                tvFinishedIcon.text = "❌"
                tvFinishedMessage.text = "Este pedido ya ha sido CANCELADO y se encuentra cerrado."
            }
        } else {
            layoutFinishedBanner.visibility = View.GONE
            tvSectionTitle.visibility = View.VISIBLE
            tvSectionDesc.visibility = View.VISIBLE
            containerCards.visibility = View.VISIBLE
            btnConfirm.visibility = View.VISIBLE

            // Seleccionar por defecto la primera opción de avance
            selectedStatus = allowedTransitions.first()

            val cardViews = mutableListOf<Triple<MaterialCardView, RadioButton, OrderStatus>>()
            val inflater = LayoutInflater.from(requireContext())

            for (targetStatus in allowedTransitions) {
                val itemView = inflater.inflate(R.layout.item_admin_status_option_card, containerCards, false)
                val card = itemView.findViewById<MaterialCardView>(R.id.cardStatusOption)
                val tvIcon = itemView.findViewById<TextView>(R.id.tvOptionIcon)
                val tvTitle = itemView.findViewById<TextView>(R.id.tvOptionTitle)
                val tvDesc = itemView.findViewById<TextView>(R.id.tvOptionDesc)
                val rb = itemView.findViewById<RadioButton>(R.id.rbOptionSelected)

                when (targetStatus) {
                    OrderStatus.EN_PROCESO -> {
                        tvIcon.text = "👨‍🍳"
                        tvTitle.text = "Avanzar a: EN COCINA"
                        tvTitle.setTextColor(Color.parseColor("#29B6F6"))
                        tvDesc.text = "El equipo de cocina iniciará la preparación de los platos."
                        rb.buttonTintList = ColorStateList.valueOf(Color.parseColor("#29B6F6"))
                    }
                    OrderStatus.EN_CAMINO -> {
                        tvIcon.text = "🛵"
                        tvTitle.text = "Avanzar a: EN CAMINO"
                        tvTitle.setTextColor(Color.parseColor("#AB47BC"))
                        tvDesc.text = "El pedido está listo y el repartidor va en ruta al cliente."
                        rb.buttonTintList = ColorStateList.valueOf(Color.parseColor("#AB47BC"))
                    }
                    OrderStatus.ENTREGADO -> {
                        tvIcon.text = "✅"
                        tvTitle.text = "Avanzar a: ENTREGADO"
                        tvTitle.setTextColor(Color.parseColor("#4CAF50"))
                        tvDesc.text = "El cliente ha recibido su pedido y la orden finaliza con éxito."
                        rb.buttonTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                    }
                    OrderStatus.CANCELADO -> {
                        tvIcon.text = "❌"
                        tvTitle.text = "Cancelar Pedido (Anular orden)"
                        tvTitle.setTextColor(Color.parseColor("#FF5252"))
                        tvDesc.text = "Anular esta orden por falta de insumos, cancelación o error."
                        rb.buttonTintList = ColorStateList.valueOf(Color.parseColor("#FF5252"))
                    }
                    else -> {
                        tvIcon.text = targetStatus.emoji
                        tvTitle.text = "Avanzar a: ${targetStatus.label}"
                        tvTitle.setTextColor(Color.parseColor("#C5D92D"))
                        tvDesc.text = "Cambiar estado del pedido a ${targetStatus.label}"
                        rb.buttonTintList = ColorStateList.valueOf(Color.parseColor("#C5D92D"))
                    }
                }

                cardViews.add(Triple(card, rb, targetStatus))
                containerCards.addView(itemView)
            }

            fun refreshSelectionUI() {
                val currentSel = selectedStatus
                for ((card, rb, status) in cardViews) {
                    val isSelected = (status == currentSel)
                    rb.isChecked = isSelected

                    if (isSelected) {
                        when (status) {
                            OrderStatus.EN_PROCESO -> {
                                card.strokeColor = Color.parseColor("#29B6F6")
                                card.setCardBackgroundColor(Color.parseColor("#0C1D26"))
                                btnConfirm.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#29B6F6"))
                                btnConfirm.text = "Avanzar a Cocina"
                                btnConfirm.setTextColor(Color.BLACK)
                            }
                            OrderStatus.EN_CAMINO -> {
                                card.strokeColor = Color.parseColor("#AB47BC")
                                card.setCardBackgroundColor(Color.parseColor("#200E26"))
                                btnConfirm.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#AB47BC"))
                                btnConfirm.text = "Avanzar a En Camino"
                                btnConfirm.setTextColor(Color.WHITE)
                            }
                            OrderStatus.ENTREGADO -> {
                                card.strokeColor = Color.parseColor("#4CAF50")
                                card.setCardBackgroundColor(Color.parseColor("#12291A"))
                                btnConfirm.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                                btnConfirm.text = "Marcar como Entregado"
                                btnConfirm.setTextColor(Color.BLACK)
                            }
                            OrderStatus.CANCELADO -> {
                                card.strokeColor = Color.parseColor("#FF5252")
                                card.setCardBackgroundColor(Color.parseColor("#2B0E12"))
                                btnConfirm.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF5252"))
                                btnConfirm.text = "Confirmar Cancelación"
                                btnConfirm.setTextColor(Color.WHITE)
                            }
                            else -> {
                                val limeColor = ContextCompat.getColor(requireContext(), R.color.yalaza_lime)
                                card.strokeColor = limeColor
                                card.setCardBackgroundColor(Color.parseColor("#17261D"))
                                btnConfirm.backgroundTintList = ColorStateList.valueOf(limeColor)
                                btnConfirm.text = "Confirmar Cambio"
                                btnConfirm.setTextColor(Color.BLACK)
                            }
                        }
                    } else {
                        card.strokeColor = Color.parseColor("#2C3B33")
                        card.setCardBackgroundColor(Color.parseColor("#18221D"))
                    }
                }
            }

            for ((card, _, status) in cardViews) {
                card.setOnClickListener {
                    selectedStatus = status
                    refreshSelectionUI()
                }
            }

            refreshSelectionUI()
        }

        // 3. Acciones de Botones
        btnCancel.setOnClickListener {
            dismiss()
        }

        btnConfirm.setOnClickListener {
            val target = selectedStatus ?: return@setOnClickListener

            if (target == OrderStatus.CANCELADO) {
                // Confirmación de seguridad al cancelar
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("¿Confirmar Cancelación?")
                    .setMessage("¿Estás seguro de cancelar este pedido? Esta acción anulará la orden.")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("Sí, Cancelar Pedido") { _, _ ->
                        onStatusSelected?.invoke(target.key)
                        dismiss()
                    }
                    .setNegativeButton("No, Volver", null)
                    .show()
            } else {
                onStatusSelected?.invoke(target.key)
                dismiss()
            }
        }
    }

    private fun updateStatusBadge(tvBadge: TextView, status: String) {
        val upper = status.uppercase(Locale.getDefault())
        when {
            upper.contains("PENDIENTE") -> {
                tvBadge.text = "● PENDIENTE"
                tvBadge.setTextColor(Color.parseColor("#FFD54F"))
                tvBadge.background = createPillDrawable(Color.parseColor("#383212"))
            }
            upper.contains("PROCESO") || upper.contains("COCIN") -> {
                tvBadge.text = "● EN COCINA"
                tvBadge.setTextColor(Color.parseColor("#29B6F6"))
                tvBadge.background = createPillDrawable(Color.parseColor("#10374A"))
            }
            upper.contains("CAMINO") || upper.contains("REPART") -> {
                tvBadge.text = "● EN CAMINO"
                tvBadge.setTextColor(Color.parseColor("#AB47BC"))
                tvBadge.background = createPillDrawable(Color.parseColor("#38194A"))
            }
            upper.contains("ENTREG") -> {
                tvBadge.text = "● ENTREGADO"
                tvBadge.setTextColor(Color.parseColor("#4CAF50"))
                tvBadge.background = createPillDrawable(Color.parseColor("#143E23"))
            }
            upper.contains("CANCEL") -> {
                tvBadge.text = "● CANCELADO"
                tvBadge.setTextColor(Color.parseColor("#FF5252"))
                tvBadge.background = createPillDrawable(Color.parseColor("#4A151B"))
            }
            else -> {
                tvBadge.text = "● $upper"
                tvBadge.setTextColor(Color.parseColor("#9EADA4"))
                tvBadge.background = createPillDrawable(Color.parseColor("#24332B"))
            }
        }
    }

    private fun updateMiniStepper(root: View, status: String) {
        val statusEnum = OrderStatus.fromString(status)
        val tvStep1Text = root.findViewById<TextView>(R.id.tvMiniStep1Text)
        val tvStep2Text = root.findViewById<TextView>(R.id.tvMiniStep2Text)
        val tvStep3Text = root.findViewById<TextView>(R.id.tvMiniStep3Text)
        val tvStep4Text = root.findViewById<TextView>(R.id.tvMiniStep4Text)

        val line1to2 = root.findViewById<View>(R.id.lineMiniStep1to2)
        val line2to3 = root.findViewById<View>(R.id.lineMiniStep2to3)
        val line3to4 = root.findViewById<View>(R.id.lineMiniStep3to4)

        val textActive = Color.WHITE
        val textInactive = Color.parseColor("#9EADA4")
        val activeLineColor = Color.parseColor("#C5D92D")
        val inactiveLineColor = Color.parseColor("#2C3B33")

        val isStep1Active = statusEnum != OrderStatus.CANCELADO
        val isStep2Active = statusEnum in listOf(OrderStatus.EN_PROCESO, OrderStatus.EN_CAMINO, OrderStatus.ENTREGADO)
        val isStep3Active = statusEnum in listOf(OrderStatus.EN_CAMINO, OrderStatus.ENTREGADO)
        val isStep4Active = statusEnum == OrderStatus.ENTREGADO

        tvStep1Text.setTextColor(if (isStep1Active) textActive else textInactive)
        tvStep2Text.setTextColor(if (isStep2Active) textActive else textInactive)
        tvStep3Text.setTextColor(if (isStep3Active) textActive else textInactive)
        tvStep4Text.setTextColor(if (isStep4Active) textActive else textInactive)

        line1to2.setBackgroundColor(if (isStep2Active) activeLineColor else inactiveLineColor)
        line2to3.setBackgroundColor(if (isStep3Active) activeLineColor else inactiveLineColor)
        line3to4.setBackgroundColor(if (isStep4Active) activeLineColor else inactiveLineColor)
    }

    private fun createPillDrawable(bgColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(bgColor)
        }
    }

    companion object {
        const val TAG = "AdminChangeStatusBottomSheet"
        private const val ARG_ORDER_ID = "arg_order_id"
        private const val ARG_SALE_NUMBER = "arg_sale_number"
        private const val ARG_CURRENT_STATUS = "arg_current_status"
        private const val ARG_CUSTOMER_NAME = "arg_customer_name"
        private const val ARG_TOTAL_AMOUNT = "arg_total_amount"

        fun newInstance(
            orderId: String,
            saleNumber: String,
            currentStatus: String,
            customerName: String,
            totalAmount: Double
        ): AdminChangeStatusBottomSheet {
            return AdminChangeStatusBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ORDER_ID, orderId)
                    putString(ARG_SALE_NUMBER, saleNumber)
                    putString(ARG_CURRENT_STATUS, currentStatus)
                    putString(ARG_CUSTOMER_NAME, customerName)
                    putDouble(ARG_TOTAL_AMOUNT, totalAmount)
                }
            }
        }

        fun fromOrder(order: Order, saleNumber: String = ""): AdminChangeStatusBottomSheet {
            return newInstance(
                orderId = order.id,
                saleNumber = saleNumber,
                currentStatus = order.status,
                customerName = order.customerName,
                totalAmount = order.total
            )
        }
    }
}
