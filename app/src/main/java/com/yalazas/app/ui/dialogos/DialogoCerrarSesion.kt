package com.yalazas.app.ui.dialogos

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
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.yalazas.app.MainActivity
import com.yalazas.app.R
import com.yalazas.app.model.Role
import com.yalazas.app.repositorio.RepositorioAutenticacion
import java.util.Locale

/** Solicita confirmación antes de cerrar la sesión. */
class DialogoCerrarSesion : DialogFragment() {

    private var customMessage: String? = null
    private var onConfirmListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        customMessage = arguments?.getString(ARG_MESSAGE)
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
        return inflater.inflate(R.layout.dialog_logout_confirmation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = true

        val iconContainer = view.findViewById<View>(R.id.flLogoutIconContainer)
        val tvTitle = view.findViewById<TextView>(R.id.tvLogoutTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvLogoutMessage)
        val cardUserInfo = view.findViewById<View>(R.id.cardUserInfoLogout)
        val tvAvatarInitial = view.findViewById<TextView>(R.id.tvUserAvatarInitial)
        val tvUserName = view.findViewById<TextView>(R.id.tvUserNameLogout)
        val tvUserEmail = view.findViewById<TextView>(R.id.tvUserEmailLogout)
        val tvUserRoleBadge = view.findViewById<TextView>(R.id.tvUserRoleBadge)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelLogout)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmLogout)

        // Custom or default message
        if (!customMessage.isNullOrBlank()) {
            tvMessage.text = customMessage
        }

        // Populate User Info
        val profile = RepositorioAutenticacion.currentProfile
        val firebaseUser = RepositorioAutenticacion.currentUser

        val displayName = when {
            !profile?.name.isNullOrBlank() -> profile!!.name
            !firebaseUser?.displayName.isNullOrBlank() -> firebaseUser!!.displayName!!
            !firebaseUser?.email.isNullOrBlank() -> firebaseUser!!.email!!.substringBefore("@")
            else -> "Usuario"
        }

        val displayEmail = when {
            !profile?.email.isNullOrBlank() -> profile!!.email
            !firebaseUser?.email.isNullOrBlank() -> firebaseUser!!.email!!
            else -> "Cuenta activa en Yalazas"
        }

        val rol = if (profile?.role.equals("DELIVERY", ignoreCase = true)) Role.REPARTIDOR
            else Role.desdeTexto(profile?.role.orEmpty())
        val (roleText, roleColor) = when (rol) {
            Role.ADMIN -> Pair("🛡️ Administrador", Color.parseColor("#FFD54F"))
            Role.COCINERO -> Pair("👨‍🍳 Cocinero", Color.parseColor("#E81B62"))
            Role.MESERO -> Pair("🍽️ Mesero", Color.parseColor("#FFD54F"))
            Role.REPARTIDOR -> Pair("🛵 Repartidor", Color.parseColor("#69F0AE"))
            Role.CLIENTE -> Pair("👤 Cliente", Color.parseColor("#40C4FF"))
        }

        tvUserName.text = displayName
        tvUserEmail.text = displayEmail
        tvUserRoleBadge.text = roleText
        tvUserRoleBadge.setTextColor(roleColor)
        tvAvatarInitial.text = displayName.trim().take(1).uppercase(Locale.getDefault())

        // Play smooth pop & cascade entrance animation
        animarEntrada(iconContainer, tvTitle, tvMessage, cardUserInfo, btnCancel, btnConfirm)

        btnCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }

        btnConfirm.setOnClickListener {
            dismissAllowingStateLoss()
            if (onConfirmListener != null) {
                onConfirmListener?.invoke()
            } else {
                // Default global logout behavior
                RepositorioAutenticacion.cerrarSesion()
                Toast.makeText(requireContext(), "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
                try {
                    findNavController().navigate(R.id.nav_home)
                } catch (_: Exception) {
                    (activity as? MainActivity)?.recreate()
                }
            }
        }
    }

    private fun animarEntrada(
        iconContainer: View,
        title: View,
        message: View,
        card: View,
        btnCancel: View,
        btnConfirm: View
    ) {
        // 1. Icon Pop & Overshoot bounce
        iconContainer.scaleX = 0.1f
        iconContainer.scaleY = 0.1f
        iconContainer.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(iconContainer, View.SCALE_X, 0.1f, 1.15f, 1f)
        val scaleY = ObjectAnimator.ofFloat(iconContainer, View.SCALE_Y, 0.1f, 1.15f, 1f)
        val alphaIcon = ObjectAnimator.ofFloat(iconContainer, View.ALPHA, 0f, 1f)

        val iconAnimSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alphaIcon)
            duration = 500
            interpolator = OvershootInterpolator(2.0f)
        }

        // 2. Cascade slide-up and fade-in for content elements
        val contentViews = listOf(title, message, card, btnCancel, btnConfirm)
        val animators = contentViews.mapIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 30f

            val alphaAnim = ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f)
            val transAnim = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 30f, 0f)

            AnimatorSet().apply {
                playTogether(alphaAnim, transAnim)
                duration = 380
                startDelay = (150 + index * 45).toLong()
                interpolator = DecelerateInterpolator()
            }
        }

        AnimatorSet().apply {
            play(iconAnimSet)
            animators.forEach { play(it) }
            start()
        }
    }

    fun definirAccionConfirmacion(listener: () -> Unit): DialogoCerrarSesion {
        this.onConfirmListener = listener
        return this
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        const val TAG = "DialogoCerrarSesion"
        private const val ARG_MESSAGE = "arg_message"

        fun crear(customMessage: String? = null): DialogoCerrarSesion {
            return DialogoCerrarSesion().apply {
                arguments = Bundle().apply {
                    if (customMessage != null) {
                        putString(ARG_MESSAGE, customMessage)
                    }
                }
            }
        }
    }
}
