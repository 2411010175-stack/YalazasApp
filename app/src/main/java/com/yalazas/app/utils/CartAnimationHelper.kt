package com.yalazas.app.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Path
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.yalazas.app.R

object CartAnimationHelper {

    /**
     * Executes a smooth arc fly-to-cart animation from the clicked button/view
     * to the bottom navigation cart icon, ending with a pulse bounce effect.
     */
    fun animateFlyToCart(
        activity: Activity?,
        fromView: View?,
        targetView: View?,
        onEnd: (() -> Unit)? = null
    ) {
        if (activity == null || fromView == null || targetView == null) {
            onEnd?.invoke()
            return
        }

        val decorView = activity.window.decorView as? ViewGroup ?: run {
            onEnd?.invoke()
            return
        }

        // 1. Calculate origin position
        val fromLoc = IntArray(2)
        fromView.getLocationInWindow(fromLoc)
        val startX = fromLoc[0] + (fromView.width / 2f)
        val startY = fromLoc[1] + (fromView.height / 2f)

        // 2. Calculate destination position (Cart icon in bottom nav)
        val targetLoc = IntArray(2)
        targetView.getLocationInWindow(targetLoc)
        val endX = targetLoc[0] + (targetView.width / 2f)
        val endY = targetLoc[1] + (targetView.height / 2f)

        // If target is offscreen or hidden, just invoke callback
        if (endX <= 0 && endY <= 0) {
            onEnd?.invoke()
            return
        }

        val density = activity.resources.displayMetrics.density
        val sizePx = (38 * density).toInt()

        // 3. Create flying transient badge
        val flyingContainer = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
            background = ContextCompat.getDrawable(activity, R.drawable.bg_cart_flying_item)
            elevation = 120f
            x = startX - (sizePx / 2f)
            y = startY - (sizePx / 2f)
        }

        val icon = AppCompatImageView(activity).apply {
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setImageResource(R.drawable.ic_shopping_bag_cart)
            setColorFilter(ContextCompat.getColor(activity, R.color.white))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        flyingContainer.addView(icon)
        decorView.addView(flyingContainer)

        // 4. Parabolic trajectory path
        val path = Path().apply {
            val initialX = startX - (sizePx / 2f)
            val initialY = startY - (sizePx / 2f)
            val finalX = endX - (sizePx / 2f)
            val finalY = endY - (sizePx / 2f)

            moveTo(initialX, initialY)

            // Calculate arch control point (curving upwards before diving to bottom nav)
            val controlX = (initialX + finalX) / 2f
            val controlY = minOf(initialY, finalY) - (120 * density)
            quadTo(controlX, controlY, finalX, finalY)
        }

        val pathAnimator = ObjectAnimator.ofFloat(flyingContainer, View.X, View.Y, path)
        val scaleXAnimator = ObjectAnimator.ofFloat(flyingContainer, View.SCALE_X, 1.0f, 0.28f)
        val scaleYAnimator = ObjectAnimator.ofFloat(flyingContainer, View.SCALE_Y, 1.0f, 0.28f)
        val alphaAnimator = ObjectAnimator.ofFloat(flyingContainer, View.ALPHA, 1.0f, 0.85f)
        val rotationAnimator = ObjectAnimator.ofFloat(flyingContainer, View.ROTATION, 0f, 360f)

        AnimatorSet().apply {
            playTogether(pathAnimator, scaleXAnimator, scaleYAnimator, alphaAnimator, rotationAnimator)
            duration = 440
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    decorView.removeView(flyingContainer)

                    // 5. Cart icon bouncy impact and soft haptic feedback
                    targetView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    targetView.animate()
                        .scaleX(1.35f)
                        .scaleY(1.35f)
                        .setDuration(130)
                        .setInterpolator(OvershootInterpolator(2.5f))
                        .withEndAction {
                            targetView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(130)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .start()
                        }
                        .start()

                    onEnd?.invoke()
                }
            })
            start()
        }
    }
}
