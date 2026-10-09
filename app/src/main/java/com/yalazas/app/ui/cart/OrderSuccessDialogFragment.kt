package com.yalazas.app.ui.cart

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
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.yalazas.app.MainActivity
import com.yalazas.app.R
import java.util.Locale

class OrderSuccessDialogFragment : DialogFragment() {

    private var orderId: String = ""
    private var totalAmount: Double = 0.0
    private var deliveryAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orderId = arguments?.getString(ARG_ORDER_ID).orEmpty()
        totalAmount = arguments?.getDouble(ARG_TOTAL) ?: 0.0
        deliveryAddress = arguments?.getString(ARG_ADDRESS).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            requestFeature(Window.FEATURE_NO_TITLE)
        }
        return inflater.inflate(R.layout.dialog_order_success, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        val iconContainer = view.findViewById<View>(R.id.layoutSuccessIconContainer)
        val tvCode = view.findViewById<TextView>(R.id.tvSuccessOrderCode)
        val tvAddress = view.findViewById<TextView>(R.id.tvSuccessAddress)
        val tvTotal = view.findViewById<TextView>(R.id.tvSuccessTotal)
        val cardDetails = view.findViewById<View>(R.id.cardSuccessDetails)
        val layoutCodeBadge = view.findViewById<View>(R.id.layoutSuccessCodeBadge)
        val btnTrackOrder = view.findViewById<MaterialButton>(R.id.btnSuccessTrackOrder)
        val btnBackToMenu = view.findViewById<MaterialButton>(R.id.btnSuccessBackToMenu)

        // Set Data
        val codeText = if (orderId.length > 4) "#${orderId.takeLast(5).uppercase(Locale.US)}" else "#$orderId"
        tvCode.text = codeText
        tvAddress.text = deliveryAddress.ifBlank { "Ubicación fijada en mapa" }
        tvTotal.text = "S/ ${String.format(Locale.US, "%.2f", totalAmount)}"

        // Execute Celebratory Bounce Animations
        playSuccessAnimations(iconContainer, layoutCodeBadge, cardDetails, btnTrackOrder, btnBackToMenu)

        btnTrackOrder.setOnClickListener {
            dismissAllowingStateLoss()
            try {
                findNavController().navigate(R.id.nav_tracking)
            } catch (_: Exception) {
                // fallback
            }
        }

        btnBackToMenu.setOnClickListener {
            dismissAllowingStateLoss()
            (activity as? MainActivity)?.navigateToMenu() ?: run {
                try {
                    findNavController().navigate(R.id.nav_menu)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun playSuccessAnimations(
        iconContainer: View,
        badge: View,
        card: View,
        btnTrack: View,
        btnMenu: View
    ) {
        // 1. Icon Pop & Overshoot scale
        iconContainer.scaleX = 0.1f
        iconContainer.scaleY = 0.1f
        iconContainer.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(iconContainer, View.SCALE_X, 0.1f, 1.15f, 1f)
        val scaleY = ObjectAnimator.ofFloat(iconContainer, View.SCALE_Y, 0.1f, 1.15f, 1f)
        val alphaIcon = ObjectAnimator.ofFloat(iconContainer, View.ALPHA, 0f, 1f)

        val iconAnimSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alphaIcon)
            duration = 650
            interpolator = OvershootInterpolator(2.2f)
        }

        // 2. Cascade fade-in and slide up for contents
        val viewsToFade = listOf(badge, card, btnTrack, btnMenu)
        val contentAnimators = viewsToFade.mapIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 40f

            val a = ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f)
            val ty = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 40f, 0f)

            AnimatorSet().apply {
                playTogether(a, ty)
                duration = 450
                startDelay = (200 + index * 60).toLong()
                interpolator = DecelerateInterpolator()
            }
        }

        AnimatorSet().apply {
            play(iconAnimSet)
            contentAnimators.forEach { play(it) }
            start()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        const val TAG = "OrderSuccessDialogFragment"
        private const val ARG_ORDER_ID = "arg_order_id"
        private const val ARG_TOTAL = "arg_total"
        private const val ARG_ADDRESS = "arg_address"

        fun newInstance(orderId: String, total: Double, address: String): OrderSuccessDialogFragment {
            return OrderSuccessDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ORDER_ID, orderId)
                    putDouble(ARG_TOTAL, total)
                    putString(ARG_ADDRESS, address)
                }
            }
        }
    }
}
