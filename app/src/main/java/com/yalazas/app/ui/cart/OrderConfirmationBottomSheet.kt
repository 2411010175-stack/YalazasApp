package com.yalazas.app.ui.cart

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yalazas.app.R
import com.yalazas.app.model.CartItem
import com.yalazas.app.model.UserProfile
import com.yalazas.app.repository.AuthRepository
import com.yalazas.app.repository.CartRepository
import com.yalazas.app.ui.location.LocationPickerBottomSheet
import com.yalazas.app.utils.DeliveryPricingHelper
import java.util.Locale

class OrderConfirmationBottomSheet : BottomSheetDialogFragment() {

    private var deliveryAddress: String = ""
    private var deliveryReference: String = ""
    private var deliveryLat: Double = 0.0
    private var deliveryLng: Double = 0.0

    // Delivery mode & Pricing
    private var isDelivery: Boolean = true
    private var deliveryDistanceKm: Double = 0.0
    private var currentDeliveryFee: Double = 0.0
    private var cartSubtotal: Double = 0.0
    private var finalTotal: Double = 0.0

    // Payment state
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

        // Cart items and base subtotal
        cartSubtotal = CartRepository.getTotal()
        val savings = CartRepository.getTotalSavings()
        tvSubtotal.text = "S/ ${String.format(Locale.US, "%.2f", cartSubtotal)}"

        if (savings > 0.0) {
            layoutSavings.visibility = View.VISIBLE
            tvSavings.text = "- S/ ${String.format(Locale.US, "%.2f", savings)}"
        } else {
            layoutSavings.visibility = View.GONE
        }

        // Cash Change Calculation helper
        fun updateCashCalculations() {
            val inputStr = etCashAmount.text.toString().trim()
            val entered = inputStr.toDoubleOrNull() ?: 0.0
            paymentAmount = entered

            if (entered <= 0.0) {
                tvCashChangeIcon.text = "ℹ️"
                tvCashChangeMessage.text = "Ingresa el monto con el que pagarás para calcular tu vuelto"
                tvCashChangeMessage.setTextColor(Color.parseColor("#B2CEC0"))
                paymentChange = 0.0
            } else if (entered < finalTotal) {
                val missing = finalTotal - entered
                tvCashChangeIcon.text = "⚠️"
                tvCashChangeMessage.text = "Monto insuficiente (Faltan S/ ${String.format(Locale.US, "%.2f", missing)})"
                tvCashChangeMessage.setTextColor(Color.parseColor("#FFAB40"))
                paymentChange = 0.0
            } else if (entered == finalTotal) {
                tvCashChangeIcon.text = "✅"
                tvCashChangeMessage.text = "Pago con monto exacto (Sin vuelto)"
                tvCashChangeMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_lime))
                paymentChange = 0.0
            } else {
                val change = entered - finalTotal
                paymentChange = change
                tvCashChangeIcon.text = "💵"
                tvCashChangeMessage.text = "Tu vuelto será: S/ ${String.format(Locale.US, "%.2f", change)}"
                tvCashChangeMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_lime))
            }
        }

        // Helper to update delivery mode, distance, fee, and totals
        fun recalculateTotalsAndRefreshViews() {
            if (isDelivery) {
                // Calculate distance if GPS is available
                deliveryDistanceKm = if (deliveryLat != 0.0 && deliveryLng != 0.0) {
                    DeliveryPricingHelper.calculateDistanceKm(
                        lat2 = deliveryLat,
                        lon2 = deliveryLng
                    )
                } else {
                    0.0
                }

                currentDeliveryFee = DeliveryPricingHelper.calculateDeliveryFee(deliveryDistanceKm, isDelivery = true)
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
                    tvDistanceBadge.text = "📏 ${DeliveryPricingHelper.formatDistance(deliveryDistanceKm)}"
                    tvDistanceBadge.visibility = View.VISIBLE
                } else {
                    tvDistanceBadge.text = "🛵 Envío estándar"
                    tvDistanceBadge.visibility = View.VISIBLE
                }

                tvEstimatedTime.text = "⏱️ ${DeliveryPricingHelper.estimateDeliveryTime(deliveryDistanceKm, isDelivery = true)}"
                tvDeliveryFeeLabel.text = "Costo de Envío (Delivery)"
                tvDeliveryFeeValue.text = "S/ ${String.format(Locale.US, "%.2f", currentDeliveryFee)}"
            } else {
                // In-store pickup
                deliveryDistanceKm = 0.0
                currentDeliveryFee = 0.0
                finalTotal = cartSubtotal

                tvAddressTitle.text = "🏪 Retiro en Local Yalazas"
                tvAddress.text = "${DeliveryPricingHelper.STORE_NAME}\n${DeliveryPricingHelper.STORE_ADDRESS}"
                tvReference.visibility = View.GONE
                btnEditMap.visibility = View.GONE

                tvDistanceBadge.text = "🏪 Sin costo de envío"
                tvDistanceBadge.visibility = View.VISIBLE
                tvEstimatedTime.text = "⏱️ ${DeliveryPricingHelper.estimateDeliveryTime(0.0, isDelivery = false)}"

                tvDeliveryFeeLabel.text = "Costo de Envío (Recojo)"
                tvDeliveryFeeValue.text = "¡GRATIS!"
            }

            tvTotal.text = "S/ ${String.format(Locale.US, "%.2f", finalTotal)}"
            etCashAmount.setText(String.format(Locale.US, "%.2f", finalTotal))
            updateCashCalculations()
        }

        // Delivery Mode Switcher Listener
        btnTypeDelivery.setOnClickListener {
            if (!isDelivery) {
                isDelivery = true
                btnTypeDelivery.setBackgroundColor(Color.parseColor("#244432"))
                btnTypeDelivery.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_lime))

                btnTypePickup.setBackgroundColor(Color.TRANSPARENT)
                btnTypePickup.setTextColor(Color.parseColor("#8AA394"))

                recalculateTotalsAndRefreshViews()
            }
        }

        btnTypePickup.setOnClickListener {
            if (isDelivery) {
                isDelivery = false
                btnTypePickup.setBackgroundColor(Color.parseColor("#244432"))
                btnTypePickup.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_lime))

                btnTypeDelivery.setBackgroundColor(Color.TRANSPARENT)
                btnTypeDelivery.setTextColor(Color.parseColor("#8AA394"))

                recalculateTotalsAndRefreshViews()
            }
        }

        // Edit location via LocationPickerBottomSheet
        btnEditMap.setOnClickListener {
            LocationPickerBottomSheet.newInstance(
                currentAddress = deliveryAddress,
                currentReference = deliveryReference,
                lat = deliveryLat,
                lng = deliveryLng
            ) { newAddress, newRef, newLat, newLng ->
                deliveryAddress = newAddress
                deliveryReference = newRef
                deliveryLat = newLat
                deliveryLng = newLng
                recalculateTotalsAndRefreshViews()
                onAddressUpdated?.invoke(newAddress, newRef, newLat, newLng)
            }.show(parentFragmentManager, LocationPickerBottomSheet.TAG)
        }

        // Populate Order Items Dynamically
        val items = CartRepository.items.value ?: emptyList()
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
                tvSauces.text = "🍗 Salsas: ${item.selectedSauces.joinToString(", ")}"
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
        recalculateTotalsAndRefreshViews()

        // Payment Method Selector Logic
        fun highlightPaymentCard(selectedCard: MaterialCardView) {
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

        fun selectPaymentMethod(method: String) {
            selectedPaymentMethod = method
            tvSelectedPaymentBadge.text = method.lowercase().replaceFirstChar { it.uppercase() }

            layoutCashDetails.visibility = if (method == "EFECTIVO") View.VISIBLE else View.GONE
            layoutYapeDetails.visibility = if (method == "YAPE") View.VISIBLE else View.GONE
            layoutPlinDetails.visibility = if (method == "PLIN") View.VISIBLE else View.GONE
            layoutTransferDetails.visibility = if (method == "TRANSFERENCIA") View.VISIBLE else View.GONE

            when (method) {
                "EFECTIVO" -> highlightPaymentCard(cardPayCash)
                "YAPE" -> highlightPaymentCard(cardPayYape)
                "PLIN" -> highlightPaymentCard(cardPayPlin)
                "TRANSFERENCIA" -> highlightPaymentCard(cardPayTransfer)
            }
        }

        cardPayCash.setOnClickListener { selectPaymentMethod("EFECTIVO") }
        cardPayYape.setOnClickListener { selectPaymentMethod("YAPE") }
        cardPayPlin.setOnClickListener { selectPaymentMethod("PLIN") }
        cardPayTransfer.setOnClickListener { selectPaymentMethod("TRANSFERENCIA") }

        etCashAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCashCalculations()
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

        // Copy actions to clipboard
        fun copyToClipboard(label: String, text: String, successMsg: String) {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text.replace(" ", "").replace("-", ""))
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show()
        }

        btnCopyYape.setOnClickListener {
            copyToClipboard("Yape Yalazas", tvYapeNumber.text.toString(), "¡Número Yape copiado al portapapeles! 📱")
        }
        btnCopyPlin.setOnClickListener {
            copyToClipboard("Plin Yalazas", tvPlinNumber.text.toString(), "¡Número Plin copiado al portapapeles! 🔵")
        }
        btnCopyBcp.setOnClickListener {
            copyToClipboard("BCP Yalazas", tvBcpNumber.text.toString(), "¡Número de cuenta BCP copiado! 🏦")
        }
        btnCopyInterbank.setOnClickListener {
            copyToClipboard("Interbank Yalazas", tvInterbankNumber.text.toString(), "¡Número de cuenta Interbank copiado! 🏦")
        }

        // Submit Order
        btnSubmit.setOnClickListener {
            val currentItems = CartRepository.items.value ?: emptyList()
            if (currentItems.isEmpty()) {
                Toast.makeText(requireContext(), "Tu carrito está vacío", Toast.LENGTH_SHORT).show()
                dismiss()
                return@setOnClickListener
            }

            val profile = AuthRepository.currentProfile
            if (profile == null) {
                Toast.makeText(requireContext(), "Inicia sesión para realizar tu pedido", Toast.LENGTH_SHORT).show()
                dismiss()
                return@setOnClickListener
            }

            if (selectedPaymentMethod == "EFECTIVO") {
                val inputStr = etCashAmount.text.toString().trim()
                val entered = inputStr.toDoubleOrNull() ?: 0.0
                if (entered < finalTotal) {
                    Toast.makeText(
                        requireContext(),
                        "El monto en efectivo debe ser al menos el total (S/ ${String.format(Locale.US, "%.2f", finalTotal)})",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
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

            val comment = etNotes.text.toString().trim()
            val effectiveAddress = if (isDelivery) {
                deliveryAddress.ifBlank { "Ubicación fijada en mapa" }
            } else {
                "${DeliveryPricingHelper.STORE_NAME} (${DeliveryPricingHelper.STORE_ADDRESS})"
            }

            sendOrderToFirestore(
                items = currentItems,
                profile = profile,
                address = effectiveAddress,
                reference = if (isDelivery) deliveryReference else "Recojo en Tienda",
                lat = if (isDelivery) deliveryLat else DeliveryPricingHelper.STORE_LATITUDE,
                lng = if (isDelivery) deliveryLng else DeliveryPricingHelper.STORE_LONGITUDE,
                comment = comment,
                totalAmount = finalTotal,
                paymentMethod = selectedPaymentMethod,
                paymentAmount = if (selectedPaymentMethod == "EFECTIVO") paymentAmount else finalTotal,
                paymentChange = if (selectedPaymentMethod == "EFECTIVO") paymentChange else 0.0,
                isDelivery = isDelivery,
                deliveryDistanceKm = deliveryDistanceKm,
                deliveryFee = currentDeliveryFee,
                onSuccess = { orderId ->
                    pbLoading.visibility = View.GONE
                    CartRepository.clearCart()
                    dismissAllowingStateLoss()
                    onOrderPlacedSuccess?.invoke(orderId, finalTotal, effectiveAddress)
                },
                onError = { errorMessage ->
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Enviar Pedido a Cocina 🚀"
                    pbLoading.visibility = View.GONE
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun sendOrderToFirestore(
        items: List<CartItem>,
        profile: UserProfile,
        address: String,
        reference: String,
        lat: Double,
        lng: Double,
        comment: String,
        totalAmount: Double,
        paymentMethod: String,
        paymentAmount: Double,
        paymentChange: Double,
        isDelivery: Boolean,
        deliveryDistanceKm: Double,
        deliveryFee: Double,
        onSuccess: (orderId: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        val orderData = hashMapOf(
            "userId" to profile.uid,
            "customerName" to profile.name,
            "phone" to profile.phone,
            "address" to address,
            "reference" to reference,
            "latitude" to (if (lat != 0.0) lat else profile.latitude),
            "longitude" to (if (lng != 0.0) lng else profile.longitude),
            "comment" to comment,
            "total" to totalAmount,
            "status" to "PENDIENTE",
            "paymentMethod" to paymentMethod,
            "paymentAmount" to paymentAmount,
            "paymentChange" to paymentChange,
            "paymentStatus" to "PENDIENTE",
            "deliveryDriverId" to "",
            "deliveryDriverName" to "",
            "deliveryDriverPhone" to "",
            "isDelivery" to isDelivery,
            "deliveryDistanceKm" to deliveryDistanceKm,
            "deliveryFee" to deliveryFee,
            "routeOrderIndex" to 0,
            "items" to items.map {
                hashMapOf(
                    "productId" to it.product.id,
                    "name" to it.product.name,
                    "quantity" to it.quantity,
                    "subtotal" to it.subtotal,
                    "notes" to it.notes,
                    "selectedSauces" to it.selectedSauces
                )
            },
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("orders").add(orderData)
            .addOnSuccessListener { documentRef ->
                onSuccess(documentRef.id)
            }
            .addOnFailureListener { e ->
                onError("Error al conectar con la cocina: ${e.localizedMessage ?: "Inténtalo de nuevo"}")
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
        const val TAG = "OrderConfirmationBottomSheet"
        private const val ARG_ADDRESS = "arg_address"
        private const val ARG_REFERENCE = "arg_reference"
        private const val ARG_LAT = "arg_lat"
        private const val ARG_LNG = "arg_lng"

        fun newInstance(
            address: String,
            reference: String,
            lat: Double,
            lng: Double,
            onAddressUpdated: ((address: String, reference: String, lat: Double, lng: Double) -> Unit)? = null,
            onOrderPlaced: (orderId: String, total: Double, address: String) -> Unit
        ): OrderConfirmationBottomSheet {
            return OrderConfirmationBottomSheet().apply {
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
