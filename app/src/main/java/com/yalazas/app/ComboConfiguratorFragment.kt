package com.yalazas.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText

class ComboConfiguratorFragment : Fragment(R.layout.fragment_combo) {

    private var quantity: Int = 1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity as? MainActivity ?: return
        val tvQty = view.findViewById<TextView>(R.id.tvQty)

        fun renderQty() {
            tvQty.text = quantity.toString()
        }
        renderQty()

        view.findViewById<MaterialButton>(R.id.btnQtyMinus).setOnClickListener {
            if (quantity > 1) {
                quantity--
                renderQty()
            }
        }
        view.findViewById<MaterialButton>(R.id.btnQtyPlus).setOnClickListener {
            if (quantity < 20) {
                quantity++
                renderQty()
            }
        }

        val checkBbq = view.findViewById<MaterialCheckBox>(R.id.checkBbq)
        val checkBuffalo = view.findViewById<MaterialCheckBox>(R.id.checkBuffalo)
        val checkGarlic = view.findViewById<MaterialCheckBox>(R.id.checkGarlic)
        val checkRanch = view.findViewById<MaterialCheckBox>(R.id.checkRanch)
        val checkBlue = view.findViewById<MaterialCheckBox>(R.id.checkBlueCheese)

        view.findViewById<MaterialButton>(R.id.btnComboWhatsApp).setOnClickListener {
            val notes = view.findViewById<TextInputEditText>(R.id.inputComboNotes).text?.toString()?.trim().orEmpty()
            val sauces = buildList {
                if (checkBbq.isChecked) add(getString(R.string.combo_sauce_bbq))
                if (checkBuffalo.isChecked) add(getString(R.string.combo_sauce_buffalo))
                if (checkGarlic.isChecked) add(getString(R.string.combo_sauce_garlic))
            }
            val dressings = buildList {
                if (checkRanch.isChecked) add(getString(R.string.combo_dressing_ranch))
                if (checkBlue.isChecked) add(getString(R.string.combo_dressing_blue))
            }
            val msg = buildString {
                append("Hola Yalaza, quiero armar un combo desde la app.\n")
                append("Porciones: ").append(quantity).append('\n')
                append("Salsas: ").append(if (sauces.isEmpty()) "—" else sauces.joinToString(", ")).append('\n')
                append("Aderezos: ").append(if (dressings.isEmpty()) "—" else dressings.joinToString(", "))
                if (notes.isNotEmpty()) {
                    append('\n').append("Notas: ").append(notes)
                }
            }
            activity.openWhatsAppFor(msg)
        }
    }
}
