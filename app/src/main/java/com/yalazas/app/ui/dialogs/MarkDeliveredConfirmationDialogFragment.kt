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
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.yalazas.app.R
import com.yalazas.app.model.Order
import java.util.Locale

class MarkDeliveredConfirmationDialogFragment : DialogFragment() {

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
        return inflater.inflate(R.layout.dialog_mark_delivered_confirmation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = true

        val currentOrder = order ?: return

        val iconContainer = view.findViewById<View>(R.id.flDeliveredIconContainer)
        val tvTitle = view.findViewById<TextView>(R.id.tvDeliveredTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvDeliveredSubtitle)
        val cardPreview = view.findViewById<View>(R.id.cardDeliveredPreview)
        val tvCode = view.findViewById<TextView>(R.id.tvDeliveredOrderCode)
        val tvStopBadge = view.findViewById<TextView>(R.id.tvDeliveredStopBadge)
        val tvCustomer = view.findViewById<TextView>(R.id.tvDeliveredCustomer)
        val tvPaymentTitle = view.findViewById<TextView>(R.id.tvDeliveredPaymentTitle)
        val tvPaymentAmount = view.findViewById<TextView>(R.id.tvDeliveredPaymentAmount)
        val ivPaymentIcon = view.findViewById<ImageView>(R.id.ivDeliveredPaymentIcon)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelDelivered)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmDelivered)

        val codeStr = "#${currentOrder.id.takeLast(6).uppercase()}"
        tvCode.text = codeStr

        if (currentOrder.routeOrderIndex > 0) {
            tvStopBadge.visibility = View.VISIBLE
            tvStopBadge.text = "🏁 Parada #${currentOrder.routeOrderIndex}"
        } else {
            tvStopBadge.visibility = View.GONE
        }

        val fullAddress = if (currentOrder.reference.isNotBlank()) {
            "${currentOrder.customerName} • ${currentOrder.address} (${currentOrder.reference})"
        } else {
            "${currentOrder.customerName} • ${currentOrder.address}"
        }
        tvCustomer.text = fullAddress

        val isCash = currentOrder.paymentMethod.contains("EFECTIVO", ignoreCase = true)
        if (isCash) {
            tvPaymentTitle.text = "Cobro en Efectivo"
            tvPaymentTitle.setTextColor(Color.parseColor("#FFD54F"))
            tvPaymentAmount.text = "Cobrar al cliente: S/ %.2f".format(Locale.getDefault(), currentOrder.total)
            ivPaymentIcon.setImageResource(R.drawable.ic_cash)
        } else {
            val method = currentOrder.paymentMethod.ifBlank { "Digital" }
            tvPaymentTitle.text = "Pago Digital ($method)"
            tvPaymentTitle.setTextColor(Color.parseColor("#40C4FF"))
            tvPaymentAmount.text = "Monto Verificado: S/ %.2f".format(Locale.getDefault(), currentOrder.total)
            ivPaymentIcon.setImageResource(R.drawable.ic_receipt)
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

    fun setOrder(order: Order): MarkDeliveredConfirmationDialogFragment {
        this.order = order
        return this
    }

    fun setOnConfirmListener(listener: () -> Unit): MarkDeliveredConfirmationDialogFragment {
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
        const val TAG = "MarkDeliveredConfirmationDialogFragment"

        fun newInstance(order: Order, onConfirm: () -> Unit): MarkDeliveredConfirmationDialogFragment {
            return MarkDeliveredConfirmationDialogFragment().apply {
                setOrder(order)
                setOnConfirmListener(onConfirm)
            }
        }
    }
}
