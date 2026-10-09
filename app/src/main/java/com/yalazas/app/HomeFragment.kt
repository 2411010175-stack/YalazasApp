package com.yalazas.app

import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.yalazas.app.ui.dialogs.LogoutConfirmationDialogFragment
import kotlin.math.max

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity as? MainActivity ?: return

        val scrollView = view.findViewById<NestedScrollView>(R.id.scrollContent)
        val welcomeHero = view.findViewById<View>(R.id.welcomeHero)
        val welcomeImage = view.findViewById<ImageView>(R.id.welcomeImage)
        val homeContent = view.findViewById<View>(R.id.homeContent)
        val scrollHint = view.findViewById<View>(R.id.scrollHint)
        val scrollHintArrow = view.findViewById<View>(R.id.scrollHintArrow)

        view.findViewById<View>(R.id.btnInstagram).setOnClickListener {
            activity.openInstagram()
        }

        view.findViewById<View>(R.id.btnOrderNow).setOnClickListener {
            activity.navigateToMenu()
        }

        val btnAdmin = view.findViewById<View>(R.id.btnAdminPanel)
        if (com.yalazas.app.repository.AuthRepository.currentProfile?.role == "Admin") {
            btnAdmin.visibility = View.VISIBLE
            btnAdmin.setOnClickListener {
                findNavController().navigate(R.id.nav_admin_dashboard)
            }
        } else {
            btnAdmin.visibility = View.GONE
        }

        view.findViewById<View>(R.id.btnLogout).setOnClickListener {
            LogoutConfirmationDialogFragment.newInstance("¿Deseas cerrar tu sesión actual de Yalazas?")
                .setOnConfirmListener {
                    com.yalazas.app.repository.AuthRepository.logout()
                    findNavController().navigate(R.id.nav_login)
                }
                .show(parentFragmentManager, LogoutConfirmationDialogFragment.TAG)
        }

        scrollView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val heroHeight = scrollView.height
                    if (heroHeight > 0) {
                        welcomeHero.layoutParams = welcomeHero.layoutParams.apply {
                            height = heroHeight
                        }
                        welcomeImage.post { applyTopCropMatrix(welcomeImage) }
                    }
                }
            },
        )

        scrollHintArrow.startAnimation(
            AnimationUtils.loadAnimation(requireContext(), R.anim.scroll_hint_bounce),
        )

        scrollHint.setOnClickListener {
            scrollView.smoothScrollTo(0, homeContent.top)
        }

        scrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                val showHint = scrollY < welcomeHero.height * 0.35f
                scrollHint.visibility = if (showHint) View.VISIBLE else View.GONE
            },
        )
    }

    private fun applyTopCropMatrix(image: ImageView) {
        val drawable = image.drawable ?: return
        val viewWidth = image.width.toFloat()
        val viewHeight = image.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        if (drawableWidth <= 0f || drawableHeight <= 0f) return

        val scale = max(viewWidth / drawableWidth, viewHeight / drawableHeight)
        val scaledWidth = drawableWidth * scale
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate((viewWidth - scaledWidth) / 2f, 0f)
        image.imageMatrix = matrix
    }
}
