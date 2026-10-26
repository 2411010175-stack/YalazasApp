package com.yalazas.app.ui.cart

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.yalazas.app.R
import com.yalazas.app.repositorio.ConstructorPedido
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioCarrito
import com.yalazas.app.repositorio.RepositorioPedidos
import com.yalazas.app.ui.ubicacion.HojaSeleccionUbicacion
import com.yalazas.app.utilidades.CalculadoraTarifaEntrega
import com.yalazas.app.utilidades.FachadaPagos
import java.util.Locale
import kotlin.math.abs

/** Calcula entrega y pago antes de enviar la solicitud de pedido. */
class HojaConfirmacionPedido : BottomSheetDialogFragment() {

    private var deliveryAddress: String = ""
    private var deliveryReference: String = ""
    private var deliveryLat: Double = 0.0
    private var deliveryLng: Double = 0.0

    // Modalidad de entrega, distancia calculada y totales mostrados al cliente.
    private var isDelivery: Boolean = true
    private var deliveryDistanceKm: Double = 0.0
    private var currentDeliveryFee: Double = 0.0
    private var cartSubtotal: Double = 0.0
    private var finalTotal: Double = 0.0

    // Métoodo de pago elegido y datos necesarios para calcular el vuelto.
    private var selectedPaymentMethod: String = "EFECTIVO"
    private var paymentAmount: Double = 0.0
    private var paymentChange: Double = 0.0

    private var onOrderPlacedSuccess: ((orderId: String, total: Double, address: String) -> Unit)? = null
    private var onAddressUpdated: ((address: String, reference: String, lat: Double, lng: Double) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deliveryAddress = arguments?.getString(ARG_ADDRESS).orEmpty()
        deliveryReference = arguments?.getString(ARG_REFERENCE).orEmpty()
        deliveryLat = arguments?.getDouble(ARG_LAT) ?: 0.0
        deliveryLng = arguments?.getDouble(ARG_LNG) ?: 0.0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_order_confirmation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnClose = view.findViewById<AppCompatImageButton>(R.id.btnCloseConfirmSheet)
        val btnTypeDelivery = view.findViewById<MaterialButton>(R.id.btnTypeDelivery)
        val btnTypePickup = view.findViewById<MaterialButton>(R.id.btnTypePickup)
        val cardAddress = view.findViewById<MaterialCardView>(R.id.cardConfirmDeliveryAddress)
        val tvAddressTitle = view.findViewById<TextView>(R.id.tvConfirmAddressHeaderTitle)
        val tvAddress = view.findViewById<TextView>(R.id.tvConfirmAddressText)
        val tvReference = view.findViewById<TextView>(R.id.tvConfirmReferenceText)
        val tvDistanceBadge = view.findViewById<TextView>(R.id.tvConfirmDistanceText)
        val tvEstimatedTime = view.findViewById<TextView>(R.id.tvConfirmEstimatedTime)
        val btnEditMap = view.findViewById<MaterialButton>(R.id.btnConfirmEditMap)

        val layoutItemsContainer = view.findViewById<LinearLayout>(R.id.layoutConfirmItemsContainer)
        val tvItemsHeader = view.findViewById<TextView>(R.id.tvConfirmItemsCountHeader)
        val tvSubtotal = view.findViewById<TextView>(R.id.tvConfirmSubtotal)
        val tvDeliveryFeeLabel = view.findViewById<TextView>(R.id.tvConfirmDeliveryFeeLabel)
        val tvDeliveryFeeValue = view.findViewById<TextView>(R.id.tvConfirmDeliveryFeeValue)
        val layoutSavings = view.findViewById<View>(R.id.layoutConfirmSavings)
        val tvSavings = view.findViewById<TextView>(R.id.tvConfirmSavings)
        val tvTotal = view.findViewById<TextView>(R.id.tvConfirmTotal)
        val etNotes = view.findViewById<TextInputEditText>(R.id.etConfirmKitchenNotes)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitOrderConfirm)
        val pbLoading = view.findViewById<ProgressBar>(R.id.pbSubmittingOrder)

        // Payment UI elements
        val tvSelectedPaymentBadge = view.findViewById<TextView>(R.id.tvSelectedPaymentBadge)
        val cardPayCash = view.findViewById<MaterialCardView>(R.id.cardPayOptionCash)
        val cardPayYape = view.findViewById<MaterialCardView>(R.id.cardPayOptionYape)
        val cardPayPlin = view.findViewById<MaterialCardView>(R.id.cardPayOptionPlin)
        val cardPayTransfer = view.findViewById<MaterialCardView>(R.id.cardPayOptionTransfer)

        val layoutCashDetails = view.findViewById<LinearLayout>(R.id.layoutPaymentCashDetails)
        val layoutYapeDetails = view.findViewById<LinearLayout>(R.id.layoutPaymentYapeDetails)
        val layoutPlinDetails = view.findViewById<LinearLayout>(R.id.layoutPaymentPlinDetails)
        val layoutTransferDetails = view.findViewById<LinearLayout>(R.id.layoutPaymentTransferDetails)

        val etCashAmount = view.findViewById<TextInputEditText>(R.id.etCashPaymentAmount)
        val btnQuickExact = view.findViewById<MaterialButton>(R.id.btnQuickExactAmount)
        val btnQuick20 = view.findViewById<MaterialButton>(R.id.btnQuick20)
        val btnQuick50 = view.findViewById<MaterialButton>(R.id.btnQuick50)
        val btnQuick100 = view.findViewById<MaterialButton>(R.id.btnQuick100)
        val tvCashChangeIcon = view.findViewById<TextView>(R.id.tvCashChangeIcon)
        val tvCashChangeMessage = view.findViewById<TextView>(R.id.tvCashChangeMessage)

        val tvYapeNumber = view.findViewById<TextView>(R.id.tvYapePhoneNumber)
        val btnCopyYape = view.findViewById<MaterialButton>(R.id.btnCopyYapeNumber)
        val tvPlinNumber = view.findViewById<TextView>(R.id.tvPlinPhoneNumber)
        val btnCopyPlin = view.findViewById<MaterialButton>(R.id.btnCopyPlinNumber)
        val tvBcpNumber = view.findViewById<TextView>(R.id.tvBcpAccountNumber)
        val btnCopyBcp = view.findViewById<MaterialButton>(R.id.btnCopyBcpNumber)
        val tvInterbankNumber = view.findViewById<TextView>(R.id.tvInterbankAccountNumber)
        val btnCopyInterbank = view.findViewById<MaterialButton>(R.id.btnCopyInterbankNumber)

        // Close button
        btnClose.setOnClickListener {
            dismiss()
        }

        // El subtotal proviene del carrito; la tarifa de entrega se suma más abajo.
        cartSubtotal = RepositorioCarrito.obtenerTotal()
        val savings = RepositorioCarrito.obtenerAhorroTotal()
        tvSubtotal.text = "S/ ${String.format(Locale.US, "%.2f", cartSubtotal)}"

        if (savings > 0.0) {
            layoutSavings.visibility = View.VISIBLE
            tvSavings.text = "- S/ ${String.format(Locale.US, "%.2f", savings)}"
        } else {
            layoutSavings.visibility = View.GONE
        }

        // La fachada de pagos reúne el cálculo y los mensajes del efectivo.
        fun actualizarCalculoEfectivo() {
            val inputStr = etCashAmount.text.toString().trim()
            val entered = inputStr.toDoubleOrNull() ?: 0.0
            paymentAmount = entered

            // Delegamos la lógica compleja de cálculo y textos a la Fachada de Pagos
            val result = FachadaPagos.calcularVuelto(entered, finalTotal)

            paymentChange = result.vuelto
            tvCashChangeIcon.text = result.icono
            tvCashChangeMessage.text = result.mensaje

            when {
                result.esInformativo -> tvCashChangeMessage.setTextColor(Color.parseColor("#B2CEC0"))
                result.esAdvertencia -> tvCashChangeMessage.setTextColor(Color.parseColor("#FFAB40"))
                else -> tvCashChangeMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_lime))
            }
        }

        // Recalcula tarifa, total y textos cada vez que cambia la dirección o la modalidad.
        fun recalcularTotalesYActualizarVista() {
            if (isDelivery) {
                // Calculate distance if GPS is available
                deliveryDistanceKm = if (deliveryLat != 0.0 && deliveryLng != 0.0) {
                    CalculadoraTarifaEntrega.calcularDistanciaKm(
                        lat2 = deliveryLat,
                        lon2 = deliveryLng
                    )
                } else {
                    0.0
                }

                currentDeliveryFee = CalculadoraTarifaEntrega.calcularTarifaEntrega(deliveryDistanceKm, isDelivery = true)
                finalTotal = cartSubtotal + currentDeliveryFee

                tvAddressTitle.text = "📍 Dirección de Entrega"
                tvAddress.text = deliveryAddress.ifBlank { "Ubicación fijada en mapa" }
                btnEditMap.visibility = View.VISIBLE

                if (deliveryReference.isNotBlank()) {
                    tvReference.visibility = View.VISIBLE
                    tvReference.text = "Ref: $deliveryReference"
                } else {
                    tvReference.visibility = View.GONE
                }

                if (deliveryDistanceKm > 0.0) {
                    tvDistanceBadge.text = "📏 ${CalculadoraTarifaEntrega.formatearDistancia(deliveryDistanceKm)}"
                    tvDistanceBadge.visibility = View.VISIBLE
                } else {
                    tvDistanceBadge.text = "🛵 Envío estándar"
                    tvDistanceBadge.visibility = View.VISIBLE
                }

                tvEstimatedTime.text = "⏱️ ${CalculadoraTarifaEntrega.estimarTiempoEntrega(deliveryDistanceKm, isDelivery = true)}"
                tvDeliveryFeeLabel.text = "Costo de Envío (Delivery)"
                tvDeliveryFeeValue.text = "S/ ${String.format(Locale.US, "%.2f", currentDeliveryFee)}"
            } else {
                // In-store pickup
                deliveryDistanceKm = 0.0
                currentDeliveryFee = CalculadoraTarifaEntrega.calcularTarifaEntrega(0.0, isDelivery = false)
                finalTotal = cartSubtotal

                tvAddressTitle.text = "🏪 Retiro en Local Yalazas"
                tvAddress.text = "${CalculadoraTarifaEntrega.NOMBRE_LOCAL}\n${CalculadoraTarifaEntrega.DIRECCION_LOCAL}"
                tvReference.visibility = View.GONE
                btnEditMap.visibility = View.GONE

                tvDistanceBadge.text = "🏪 Sin costo de envío"
                tvDistanceBadge.visibility = View.VISIBLE
                tvEstimatedTime.text = "⏱️ ${CalculadoraTarifaEntrega.estimarTiempoEntrega(0.0, isDelivery = false)}"

                tvDeliveryFeeLabel.text = "Costo de Envío (Recojo)"
                tvDeliveryFeeValue.text = "¡GRATIS!"
            }

            tvTotal.text = "S/ ${String.format(Locale.US, "%.2f", finalTotal)}"
            etCashAmount.setText(String.format(Locale.US, "%.2f", finalTotal))
            actualizarCalculoEfectivo()
        }

        // Delivery Mode Switcher Listener
        btnTypeDelivery.setOnClickListener {
            if (!isDelivery) {
                isDelivery = true
                btnTypeDelivery.setBackgroundColor(Color.parseColor("#244432"))
                btnTypeDelivery.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_lime))

                btnTypePickup.setBackgroundColor(Color.TRANSPARENT)
                btnTypePickup.setTextColor(Color.parseColor("#8AA394"))

                recalcularTotalesYActualizarVista()
            }
        }

        btnTypePickup.setOnClickListener {
            if (isDelivery) {
                isDelivery = false
                btnTypePickup.setBackgroundColor(Color.parseColor("#244432"))
                btnTypePickup.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_lime))

                btnTypeDelivery.setBackgroundColor(Color.TRANSPARENT)
                btnTypeDelivery.setTextColor(Color.parseColor("#8AA394"))

                recalcularTotalesYActualizarVista()
            }
        }

        // Edit location via HojaSeleccionUbicacion
        btnEditMap.setOnClickListener {
            HojaSeleccionUbicacion.crear(
                currentAddress = deliveryAddress,
                currentReference = deliveryReference,
                lat = deliveryLat,
                lng = deliveryLng
            ) { newAddress, newRef, newLat, newLng ->
                deliveryAddress = newAddress
                deliveryReference = newRef
                deliveryLat = newLat
                deliveryLng = newLng
                recalcularTotalesYActualizarVista()
                onAddressUpdated?.invoke(newAddress, newRef, newLat, newLng)
            }.show(parentFragmentManager, HojaSeleccionUbicacion.TAG)
        }

        // Populate Order Items Dynamically
        val items = RepositorioCarrito.items.value ?: emptyList()
        val totalItemsCount = items.sumOf { it.quantity }
        tvItemsHeader.text = "RESUMEN DEL PEDIDO ($totalItemsCount producto${if (totalItemsCount != 1) "s" else ""})"

        layoutItemsContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        for (item in items) {
            val itemView = inflater.inflate(R.layout.item_confirm_order_product, layoutItemsContainer, false)

            val tvQty = itemView.findViewById<TextView>(R.id.tvItemConfirmQty)
            val tvName = itemView.findViewById<TextView>(R.id.tvItemConfirmName)
            val tvSub = itemView.findViewById<TextView>(R.id.tvItemConfirmSubtotal)
            val tvSauces = itemView.findViewById<TextView>(R.id.tvItemConfirmSauces)
            val tvItemNotes = itemView.findViewById<TextView>(R.id.tvItemConfirmNotes)

            tvQty.text = "${item.quantity}x"
            tvName.text = item.product.name
            tvSub.text = "S/ ${String.format(Locale.US, "%.2f", item.subtotal)}"

            if (item.selectedSauces.isNotEmpty()) {
                tvSauces.visibility = View.VISIBLE
                val recargo = if (item.recargoSalsasPorUnidad > 0.0) {
                    " · + S/ ${String.format(Locale.US, "%.2f", item.recargoSalsasPorUnidad)} por unidad"
                } else ""
                tvSauces.text = "🍗 Salsas: ${item.selectedSauces.joinToString(", ")}$recargo"
            } else {
                tvSauces.visibility = View.GONE
            }

            if (item.notes.isNotBlank()) {
                tvItemNotes.visibility = View.VISIBLE
                tvItemNotes.text = "📝 Nota: ${item.notes}"
            } else {
                tvItemNotes.visibility = View.GONE
            }

            layoutItemsContainer.addView(itemView)
        }

        // Initial Calculation
        recalcularTotalesYActualizarVista()

        // Payment Method Selector Logic
        fun resaltarTarjetaPago(selectedCard: MaterialCardView) {
            val cards = listOf(cardPayCash, cardPayYape, cardPayPlin, cardPayTransfer)
            for (c in cards) {
                if (c == selectedCard) {
                    c.setCardBackgroundColor(Color.parseColor("#1F3327"))
                    c.strokeColor = ContextCompat.getColor(requireContext(), R.color.yalaza_lime)
                    c.strokeWidth = (1.5 * resources.displayMetrics.density).toInt()
                } else {
                    c.setCardBackgroundColor(Color.parseColor("#17221C"))
                    c.strokeColor = Color.parseColor("#2D3F33")
                    c.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                }
            }
        }

        fun seleccionarMetodoPago(method: String) {
            selectedPaymentMethod = method
            tvSelectedPaymentBadge.text = method.lowercase().replaceFirstChar { it.uppercase() }

            layoutCashDetails.visibility = if (method == "EFECTIVO") View.VISIBLE else View.GONE
            layoutYapeDetails.visibility = if (method == "YAPE") View.VISIBLE else View.GONE
            layoutPlinDetails.visibility = if (method == "PLIN") View.VISIBLE else View.GONE
            layoutTransferDetails.visibility = if (method == "TRANSFERENCIA") View.VISIBLE else View.GONE

            when (method) {
                "EFECTIVO" -> resaltarTarjetaPago(cardPayCash)
                "YAPE" -> resaltarTarjetaPago(cardPayYape)
                "PLIN" -> resaltarTarjetaPago(cardPayPlin)
                "TRANSFERENCIA" -> resaltarTarjetaPago(cardPayTransfer)
            }
        }

        cardPayCash.setOnClickListener { seleccionarMetodoPago("EFECTIVO") }
        cardPayYape.setOnClickListener { seleccionarMetodoPago("YAPE") }
        cardPayPlin.setOnClickListener { seleccionarMetodoPago("PLIN") }
        cardPayTransfer.setOnClickListener { seleccionarMetodoPago("TRANSFERENCIA") }

        etCashAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actualizarCalculoEfectivo()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Quick amount chips
        btnQuickExact.setOnClickListener {
            etCashAmount.setText(String.format(Locale.US, "%.2f", finalTotal))
            etCashAmount.setSelection(etCashAmount.text?.length ?: 0)
        }
        btnQuick20.setOnClickListener {
            val amount = 20.0
            etCashAmount.setText(String.format(Locale.US, "%.2f", amount))
            etCashAmount.setSelection(etCashAmount.text?.length ?: 0)
        }
        btnQuick50.setOnClickListener {
            val amount = 50.0
            etCashAmount.setText(String.format(Locale.US, "%.2f", amount))
            etCashAmount.setSelection(etCashAmount.text?.length ?: 0)
        }
        btnQuick100.setOnClickListener {
            val amount = 100.0
            etCashAmount.setText(String.format(Locale.US, "%.2f", amount))
            etCashAmount.setSelection(etCashAmount.text?.length ?: 0)
        }

        // Copy actions to clipboard using Facade
        btnCopyYape.setOnClickListener {
            val msg = FachadaPagos.copiarAlPortapapeles(requireContext(), "Yape Yalazas", tvYapeNumber.text.toString(), "¡Número Yape copiado al portapapeles! 📱")
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        btnCopyPlin.setOnClickListener {
            val msg = FachadaPagos.copiarAlPortapapeles(requireContext(), "Plin Yalazas", tvPlinNumber.text.toString(), "¡Número Plin copiado al portapapeles! 🔵")
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        btnCopyBcp.setOnClickListener {
            val msg = FachadaPagos.copiarAlPortapapeles(requireContext(), "BCP Yalazas", tvBcpNumber.text.toString(), "¡Número de cuenta BCP copiado! 🏦")
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        btnCopyInterbank.setOnClickListener {
            val msg = FachadaPagos.copiarAlPortapapeles(requireContext(), "Interbank Yalazas", tvInterbankNumber.text.toString(), "¡Número de cuenta Interbank copiado! 🏦")
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Construye el pedido por etapas y comprueba el total mostrado antes de registrarlo.
        btnSubmit.setOnClickListener {
            val currentItems = RepositorioCarrito.items.value ?: emptyList()
            if (currentItems.isEmpty()) {
                Toast.makeText(requireContext(), "Tu carrito está vacío", Toast.LENGTH_SHORT).show()
                dismiss()
                return@setOnClickListener
            }

            val profile = RepositorioAutenticacion.currentProfile
            if (profile == null) {
                Toast.makeText(requireContext(), "Inicia sesión para realizar tu pedido", Toast.LENGTH_SHORT).show()
                dismiss()
                return@setOnClickListener
            }

            val effectiveAddress = if (isDelivery) {
                deliveryAddress.ifBlank { "Ubicación fijada en mapa" }
            } else {
                "${CalculadoraTarifaEntrega.NOMBRE_LOCAL} (${CalculadoraTarifaEntrega.DIRECCION_LOCAL})"
            }

            val solicitud = ConstructorPedido.paraCliente(profile)
                .agregarArticulos(currentItems)
                .configurarEntrega(
                    direccion = effectiveAddress,
                    referencia = if (isDelivery) deliveryReference else "Recojo en Tienda",
                    latitud = if (isDelivery) deliveryLat else CalculadoraTarifaEntrega.LATITUD_LOCAL,
                    longitud = if (isDelivery) deliveryLng else CalculadoraTarifaEntrega.LONGITUD_LOCAL,
                    esDelivery = isDelivery,
                    distanciaKm = deliveryDistanceKm,
                    tarifa = currentDeliveryFee
                )
                .agregarComentario(etNotes.text.toString())
                .configurarPago(selectedPaymentMethod, paymentAmount)
                .construir()

            // Si el carrito cambió mientras estaba abierta esta hoja, se actualiza el total
            // y se pide una nueva revisión antes de enviar el pedido.
            if (!solicitud.totalAmount.isFinite()) {
                Toast.makeText(requireContext(), "El total del pedido no es válido.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (abs(solicitud.totalAmount - finalTotal) > 0.01) {
                cartSubtotal = currentItems.sumOf { it.subtotal }
                tvSubtotal.text = "S/ ${String.format(Locale.US, "%.2f", cartSubtotal)}"
                recalcularTotalesYActualizarVista()
                Toast.makeText(requireContext(), "El total cambió. Revísalo antes de enviar el pedido.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (selectedPaymentMethod == "EFECTIVO" && solicitud.paymentAmount < solicitud.totalAmount) {
                Toast.makeText(
                    requireContext(),
                    "El monto en efectivo debe ser al menos el total (S/ ${String.format(Locale.US, "%.2f", solicitud.totalAmount)})",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Animate button press
            val scaleX = ObjectAnimator.ofFloat(btnSubmit, View.SCALE_X, 1f, 0.95f, 1f)
            val scaleY = ObjectAnimator.ofFloat(btnSubmit, View.SCALE_Y, 1f, 0.95f, 1f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 200
                start()
            }

            // Show loading state
            btnSubmit.isEnabled = false
            btnSubmit.text = "Registrando tu orden..."
            pbLoading.visibility = View.VISIBLE

            // El repositorio vuelve a validar y conserva las claves existentes de Firestore.
            RepositorioPedidos.registrarPedido(solicitud) { result ->
                if (!isAdded || this@HojaConfirmacionPedido.view == null) return@registrarPedido
                result.onSuccess { orderId ->
                    pbLoading.visibility = View.GONE
                    dismissAllowingStateLoss()
                    onOrderPlacedSuccess?.invoke(orderId, solicitud.totalAmount, effectiveAddress)
                }.onFailure { error ->
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Enviar Pedido a Cocina 🚀"
                    pbLoading.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "No se pudo registrar el pedido: ${error.localizedMessage ?: "Inténtalo de nuevo"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            bottomSheet.requestLayout()
        }
    }

    companion object {
        const val TAG = "HojaConfirmacionPedido"
        private const val ARG_ADDRESS = "arg_address"
        private const val ARG_REFERENCE = "arg_reference"
        private const val ARG_LAT = "arg_lat"
        private const val ARG_LNG = "arg_lng"

        fun crear(
            address: String,
            reference: String,
            lat: Double,
            lng: Double,
            onAddressUpdated: ((address: String, reference: String, lat: Double, lng: Double) -> Unit)? = null,
            onOrderPlaced: (orderId: String, total: Double, address: String) -> Unit
        ): HojaConfirmacionPedido {
            return HojaConfirmacionPedido().apply {
                this.onAddressUpdated = onAddressUpdated
                this.onOrderPlacedSuccess = onOrderPlaced
                arguments = Bundle().apply {
                    putString(ARG_ADDRESS, address)
                    putString(ARG_REFERENCE, reference)
                    putDouble(ARG_LAT, lat)
                    putDouble(ARG_LNG, lng)
                }
            }
        }
    }
}
