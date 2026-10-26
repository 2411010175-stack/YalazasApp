package com.yalazas.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

/** Presenta los medios de contacto del restaurante. */
class ContactFragment : Fragment(R.layout.fragment_contact) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity as? MainActivity ?: return

        view.findViewById<MaterialButton>(R.id.btnContactChat).setOnClickListener {
            activity.abrirWhatsAppConMensaje(getString(R.string.whatsapp_contact))
        }
        view.findViewById<MaterialButton>(R.id.btnContactInstagram).setOnClickListener {
            activity.abrirInstagram()
        }
        view.findViewById<View>(R.id.cardContactMap).setOnClickListener {
            activity.abrirMapa()
        }
        view.findViewById<View>(R.id.tvContactAddress).setOnClickListener {
            activity.abrirMapa()
        }
    }
}
