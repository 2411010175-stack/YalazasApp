package com.yalazas.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class ContactFragment : Fragment(R.layout.fragment_contact) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity as? MainActivity ?: return

        view.findViewById<MaterialButton>(R.id.btnContactChat).setOnClickListener {
            activity.openWhatsAppFor(getString(R.string.whatsapp_contact))
        }
        view.findViewById<MaterialButton>(R.id.btnContactInstagram).setOnClickListener {
            activity.openInstagram()
        }
        view.findViewById<View>(R.id.cardContactMap).setOnClickListener {
            activity.openMap()
        }
        view.findViewById<View>(R.id.tvContactAddress).setOnClickListener {
            activity.openMap()
        }
    }
}
