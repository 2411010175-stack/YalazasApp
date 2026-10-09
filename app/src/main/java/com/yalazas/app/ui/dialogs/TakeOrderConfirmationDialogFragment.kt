package com.yalazas.app.ui.dialogs

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.yalazas.app.R
import com.yalazas.app.model.Order
import java.util.Locale

class TakeOrderConfirmationDialogFragment : DialogFragment() {

    private var order: Order? = null
    private var onConfirmListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            requestFeature(Window.FEATURE_NO_TITLE)
        }
        return inflater.inflate(R.layout.dialog_take_order_confirmation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = true

        val currentOrder = order ?: return

        val iconContainer = view.findViewById<View>(R.id.flTakeOrderIconContainer)
        val tvTitle = view.findViewById<TextView>(R.id.tvTakeOrderTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvTakeOrderSubtitle)
        val cardPreview = view.findViewById<View>(R.id.cardOrderPreviewTake)
        val tvCode = view.findViewById<TextView>(R.id.tvTakeOrderCode)
        val tvTotal = view.findViewById<TextView>(R.id.tvTakeOrderTotal)
        val tvCustomer = view.findViewById<TextView>(R.id.tvTakeOrderCustomer)
        val tvAddress = view.findViewById<TextView>(R.id.tvTakeOrderAddress)
        val tvPayment = view.findViewById<TextView>(R.id.tvTakeOrderPayment)
        val tvDistance = view.findViewById<TextView>(R.id.tvTakeOrderDistance)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelTakeOrder)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmTakeOrder)

        val codeStr = "#${currentOrder.id.takeLast(6).uppercase()}"
        tvCode.text = codeStr
        tvTotal.text = "S/ %.2f".format(Locale.getDefault(), currentOrder.total)
        tvCustomer.text = currentOrder.customerName.ifBlank { "Cliente Yalazas" }

        val fullAddress = if (currentOrder.reference.isNotBlank()) {
            "${currentOrder.address} (${currentOrder.reference})"
        } else {
            currentOrder.address.ifBlank { "Dirección no especificada" }
        }
        tvAddress.text = fullAddress

        val paymentMethod = currentOrder.paymentMethod.ifBlank { "Efectivo" }
        tvPayment.text = "💳 $paymentMethod"

        if (currentOrder.deliveryDistanceKm > 0.05) {
            tvDistance.visibility = View.VISIBLE
            tvDistance.text = "📍 ~%.1f km".format(Locale.getDefault(), currentOrder.deliveryDistanceKm)
        } else {
            tvDistance.visibility = View.GONE
        }

        playEntranceAnimations(iconContainer, tvTitle, tvSubtitle, cardPreview, btnCancel, btnConfirm)

        btnCancel.setOnClickListener { dismissAllowingStateLoss() }

        btnConfirm.setOnClickListener {
            dismissAllowingStateLoss()
            onConfirmListener?.invoke()
        }
    }

    private fun playEntranceAnimations(
        iconContainer: View,
        title: View,
        subtitle: View,
        card: View,
        btnCancel: View,
        btnConfirm: View
    ) {
        iconContainer.scaleX = 0.1f
        iconContainer.scaleY = 0.1f
        iconContainer.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(iconContainer, View.SCALE_X, 0.1f, 1.15f, 1f)
        val scaleY = ObjectAnimator.ofFloat(iconContainer, View.SCALE_Y, 0.1f, 1.15f, 1f)
        val alphaIcon = ObjectAnimator.ofFloat(iconContainer, View.ALPHA, 0f, 1f)

        val iconAnimSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alphaIcon)
            duration = 450
            interpolator = OvershootInterpolator(2.0f)
        }

        val contentViews = listOf(title, subtitle, card, btnCancel, btnConfirm)
        val animators = contentViews.mapIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 30f

            val alphaAnim = ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f)
            val transAnim = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 30f, 0f)

            AnimatorSet().apply {
                playTogether(alphaAnim, transAnim)
                duration = 350
                startDelay = (140 + index * 40).toLong()
                interpolator = DecelerateInterpolator()
            }
        }

        AnimatorSet().apply {
            play(iconAnimSet)
            animators.forEach { play(it) }
            start()
        }
    }

    fun setOrder(order: Order): TakeOrderConfirmationDialogFragment {
        this.order = order
        return this
    }

    fun setOnConfirmListener(listener: () -> Unit): TakeOrderConfirmationDialogFragment {
        this.onConfirmListener = listener
        return this
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        const val TAG = "TakeOrderConfirmationDialogFragment"

        fun newInstance(order: Order, onConfirm: () -> Unit): TakeOrderConfirmationDialogFragment {
            return TakeOrderConfirmationDialogFragment().apply {
                setOrder(order)
                setOnConfirmListener(onConfirm)
            }
        }
    }
}
