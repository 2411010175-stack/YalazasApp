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

class BatchTakeConfirmationDialogFragment : DialogFragment() {

    private var orders: List<Order> = emptyList()
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
        return inflater.inflate(R.layout.dialog_batch_take_confirmation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = true

        val iconContainer = view.findViewById<View>(R.id.flBatchTakeIconContainer)
        val tvTitle = view.findViewById<TextView>(R.id.tvBatchTakeTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvBatchTakeSubtitle)
        val cardPreview = view.findViewById<View>(R.id.cardBatchPreviewTake)
        val tvCountBadge = view.findViewById<TextView>(R.id.tvBatchOrdersCountBadge)
        val tvTotalAmount = view.findViewById<TextView>(R.id.tvBatchTotalAmount)
        val tvPreviewList = view.findViewById<TextView>(R.id.tvBatchOrdersPreviewList)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelBatchTake)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmBatchTake)

        val count = orders.size
        tvCountBadge.text = if (count == 1) "📦 1 Pedido" else "📦 $count Pedidos"
        val totalSum = orders.sumOf { it.total }
        tvTotalAmount.text = "Total: S/ %.2f".format(Locale.getDefault(), totalSum)

        val previewLines = StringBuilder()
        orders.take(3).forEach { order ->
            val code = "#${order.id.takeLast(6).uppercase()}"
            val client = order.customerName.ifBlank { "Cliente" }
            val addr = order.address.ifBlank { "Dirección" }
            previewLines.append("• $code - $client ($addr)\n")
        }
        if (orders.size > 3) {
            val remaining = orders.size - 3
            previewLines.append("• ... y $remaining pedido(s) más")
        }
        tvPreviewList.text = previewLines.toString().trimEnd()

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

    fun setOrders(orders: List<Order>): BatchTakeConfirmationDialogFragment {
        this.orders = orders
        return this
    }

    fun setOnConfirmListener(listener: () -> Unit): BatchTakeConfirmationDialogFragment {
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
        const val TAG = "BatchTakeConfirmationDialogFragment"

        fun newInstance(orders: List<Order>, onConfirm: () -> Unit): BatchTakeConfirmationDialogFragment {
            return BatchTakeConfirmationDialogFragment().apply {
                setOrders(orders)
                setOnConfirmListener(onConfirm)
            }
        }
    }
}
