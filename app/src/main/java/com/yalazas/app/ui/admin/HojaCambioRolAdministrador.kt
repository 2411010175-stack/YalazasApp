package com.yalazas.app.ui.admin

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yalazas.app.R
import com.yalazas.app.model.UserProfile
import java.util.Locale

/** Recoge el nuevo rol y estado de una cuenta para el administrador. */
class HojaCambioRolAdministrador : BottomSheetDialogFragment() {

    private var userId: String = ""
    private var userName: String = ""
    private var userEmail: String = ""
    private var initialRole: String = "CLIENTE"
    private var isUserActive: Boolean = true
    private var vehicleType: String = ""
    private var vehiclePlate: String = ""
    private var vehicleModel: String = ""

    private var selectedRole: String = "CLIENTE"
    private var isAccountActive: Boolean = true

    var onRoleSaved: ((newRole: String, isActive: Boolean, type: String, plate: String, model: String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString(ARG_USER_ID, "")
            userName = it.getString(ARG_USER_NAME, "")
            userEmail = it.getString(ARG_USER_EMAIL, "")
            initialRole = it.getString(ARG_INITIAL_ROLE, "CLIENTE")
            isUserActive = it.getBoolean(ARG_IS_ACTIVE, true)
            vehicleType = it.getString(ARG_VEHICLE_TYPE, "")
            vehiclePlate = it.getString(ARG_VEHICLE_PLATE, "")
            vehicleModel = it.getString(ARG_VEHICLE_MODEL, "")
        }
        selectedRole = normalizarRol(initialRole)
        isAccountActive = isUserActive
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_admin_change_role, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvAvatar = view.findViewById<TextView>(R.id.tvSheetUserAvatar)
        val tvName = view.findViewById<TextView>(R.id.tvSheetUserName)
        val tvEmail = view.findViewById<TextView>(R.id.tvSheetUserEmail)
        val tvCurrentRoleBadge = view.findViewById<TextView>(R.id.tvSheetCurrentRoleBadge)

        // Cards de Rol
        val cardAdmin = view.findViewById<MaterialCardView>(R.id.cardRoleAdmin)
        val cardCocinero = view.findViewById<MaterialCardView>(R.id.cardRoleCocinero)
        val cardMesero = view.findViewById<MaterialCardView>(R.id.cardRoleMesero)
        val cardRepartidor = view.findViewById<MaterialCardView>(R.id.cardRoleRepartidor)
        val cardCliente = view.findViewById<MaterialCardView>(R.id.cardRoleCliente)

        // Radio Buttons
        val rbAdmin = view.findViewById<RadioButton>(R.id.rbRoleAdmin)
        val rbCocinero = view.findViewById<RadioButton>(R.id.rbRoleCocinero)
        val rbMesero = view.findViewById<RadioButton>(R.id.rbRoleMesero)
        val rbRepartidor = view.findViewById<RadioButton>(R.id.rbRoleRepartidor)
        val rbCliente = view.findViewById<RadioButton>(R.id.rbRoleCliente)

        // Switch de Estado
        val tvStatusDesc = view.findViewById<TextView>(R.id.tvSheetAccountStatusDesc)
        val switchActive = view.findViewById<SwitchMaterial>(R.id.switchSheetUserActive)
        val vehicleCard = view.findViewById<View>(R.id.cardSheetVehicle)
        val typeLayout = view.findViewById<TextInputLayout>(R.id.layoutSheetVehicleType)
        val plateLayout = view.findViewById<TextInputLayout>(R.id.layoutSheetVehiclePlate)
        val typeInput = view.findViewById<TextInputEditText>(R.id.inputSheetVehicleType)
        val plateInput = view.findViewById<TextInputEditText>(R.id.inputSheetVehiclePlate)
        val modelInput = view.findViewById<TextInputEditText>(R.id.inputSheetVehicleModel)
        typeInput.setText(vehicleType)
        plateInput.setText(vehiclePlate)
        modelInput.setText(vehicleModel)

        // Botones
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnSheetCancelRole)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSheetSaveRole)

        // 1. Cabecera del Usuario
        val displayName = userName.ifBlank { userEmail.substringBefore("@").ifBlank { "Usuario" } }
        tvName.text = displayName
        tvEmail.text = userEmail.ifBlank { "Sin correo registrado" }
        tvAvatar.text = displayName.take(1).uppercase(Locale.getDefault())

        val currentRoleClean = normalizarRol(initialRole)
        actualizarInsigniaRol(tvCurrentRoleBadge, currentRoleClean)

        // 2. Estado de la Cuenta (Switch)
        switchActive.isChecked = isAccountActive
        actualizarTextoEstado(tvStatusDesc, isAccountActive)

        switchActive.setOnCheckedChangeListener { _, isChecked ->
            isAccountActive = isChecked
            actualizarTextoEstado(tvStatusDesc, isChecked)
        }

        // 3. Selección de Roles Visual
        fun actualizarInterfazRol() {
            vehicleCard.visibility = if (selectedRole == "REPARTIDOR") View.VISIBLE else View.GONE
            // Reset Admin
            val isAdmin = selectedRole == "ADMINISTRADOR"
            rbAdmin.isChecked = isAdmin
            cardAdmin.strokeColor = if (isAdmin) Color.parseColor("#FFD54F") else Color.parseColor("#2C3B33")
            cardAdmin.setCardBackgroundColor(if (isAdmin) Color.parseColor("#221C0E") else Color.parseColor("#18221D"))

            // Reset Cocinero
            val isCocinero = selectedRole == "COCINERO"
            rbCocinero.isChecked = isCocinero
            cardCocinero.strokeColor = if (isCocinero) Color.parseColor("#FF9800") else Color.parseColor("#2C3B33")
            cardCocinero.setCardBackgroundColor(if (isCocinero) Color.parseColor("#24180A") else Color.parseColor("#18221D"))

            // Reset Mesero
            val isMesero = selectedRole == "MESERO"
            rbMesero.isChecked = isMesero
            cardMesero.strokeColor = if (isMesero) Color.parseColor("#29B6F6") else Color.parseColor("#2C3B33")
            cardMesero.setCardBackgroundColor(if (isMesero) Color.parseColor("#0E1D26") else Color.parseColor("#18221D"))

            // Reset Repartidor
            val isRepartidor = selectedRole == "REPARTIDOR"
            rbRepartidor.isChecked = isRepartidor
            cardRepartidor.strokeColor = if (isRepartidor) Color.parseColor("#AB47BC") else Color.parseColor("#2C3B33")
            cardRepartidor.setCardBackgroundColor(if (isRepartidor) Color.parseColor("#200E26") else Color.parseColor("#18221D"))

            // Reset Cliente
            val isCliente = selectedRole == "CLIENTE"
            rbCliente.isChecked = isCliente
            val limeColor = ContextCompat.getColor(requireContext(), R.color.yalaza_lime)
            cardCliente.strokeColor = if (isCliente) limeColor else Color.parseColor("#2C3B33")
            cardCliente.setCardBackgroundColor(if (isCliente) Color.parseColor("#17261D") else Color.parseColor("#18221D"))
        }

        actualizarInterfazRol()

        cardAdmin.setOnClickListener {
            selectedRole = "ADMINISTRADOR"
            actualizarInterfazRol()
        }

        cardCocinero.setOnClickListener {
            selectedRole = "COCINERO"
            actualizarInterfazRol()
        }

        cardMesero.setOnClickListener {
            selectedRole = "MESERO"
            actualizarInterfazRol()
        }

        cardRepartidor.setOnClickListener {
            selectedRole = "REPARTIDOR"
            actualizarInterfazRol()
        }

        cardCliente.setOnClickListener {
            selectedRole = "CLIENTE"
            actualizarInterfazRol()
        }

        // 4. Acciones
        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val type = typeInput.text?.toString()?.trim().orEmpty()
            val plate = plateInput.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty()
            val model = modelInput.text?.toString()?.trim().orEmpty()
            typeLayout.error = null
            plateLayout.error = null
            if (selectedRole == "REPARTIDOR" &&
                (type.isNotBlank() || plate.isNotBlank() || model.isNotBlank())) {
                if (type.isBlank()) {
                    typeLayout.error = "Indica el tipo de vehículo"
                    return@setOnClickListener
                }
                if (plate.isBlank()) {
                    plateLayout.error = "Indica la placa"
                    return@setOnClickListener
                }
            }
            onRoleSaved?.invoke(selectedRole, isAccountActive, type, plate, model)
            dismiss()
        }
    }

    private fun normalizarRol(role: String): String {
        val upper = role.uppercase(Locale.getDefault())
        return when {
            upper.contains("ADMIN") -> "ADMINISTRADOR"
            upper.contains("COCIN") -> "COCINERO"
            upper.contains("MESER") -> "MESERO"
            upper.contains("REPART") -> "REPARTIDOR"
            else -> "CLIENTE"
        }
    }

    private fun actualizarInsigniaRol(tvBadge: TextView, role: String) {
        when (role) {
            "ADMINISTRADOR" -> {
                tvBadge.text = "👑 ADMIN"
                tvBadge.setTextColor(Color.parseColor("#FFD54F"))
            }
            "COCINERO" -> {
                tvBadge.text = "👨‍🍳 COCINERO"
                tvBadge.setTextColor(Color.parseColor("#FF9800"))
            }
            "MESERO" -> {
                tvBadge.text = "🍽️ MESERO"
                tvBadge.setTextColor(Color.parseColor("#29B6F6"))
            }
            "REPARTIDOR" -> {
                tvBadge.text = "🛵 REPARTIDOR"
                tvBadge.setTextColor(Color.parseColor("#AB47BC"))
            }
            else -> {
                tvBadge.text = "👤 CLIENTE"
                tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.yalaza_lime))
            }
        }
    }

    private fun actualizarTextoEstado(tvDesc: TextView, isActive: Boolean) {
        if (isActive) {
            tvDesc.text = "🟢 Cuenta habilitada para ingresar al sistema"
            tvDesc.setTextColor(ContextCompat.getColor(requireContext(), R.color.admin_kpi_green))
        } else {
            tvDesc.text = "🔴 Cuenta inhabilitada / Acceso bloqueado"
            tvDesc.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    companion object {
        const val TAG = "HojaCambioRolAdministrador"
        private const val ARG_USER_ID = "arg_user_id"
        private const val ARG_USER_NAME = "arg_user_name"
        private const val ARG_USER_EMAIL = "arg_user_email"
        private const val ARG_INITIAL_ROLE = "arg_initial_role"
        private const val ARG_IS_ACTIVE = "arg_is_active"
        private const val ARG_VEHICLE_TYPE = "arg_vehicle_type"
        private const val ARG_VEHICLE_PLATE = "arg_vehicle_plate"
        private const val ARG_VEHICLE_MODEL = "arg_vehicle_model"

        fun crear(user: UserProfile): HojaCambioRolAdministrador {
            return HojaCambioRolAdministrador().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, user.uid)
                    putString(ARG_USER_NAME, user.name)
                    putString(ARG_USER_EMAIL, user.email)
                    putString(ARG_INITIAL_ROLE, user.role)
                    putBoolean(ARG_IS_ACTIVE, user.active)
                    putString(ARG_VEHICLE_TYPE, user.vehicleType)
                    putString(ARG_VEHICLE_PLATE, user.vehiclePlate)
                    putString(ARG_VEHICLE_MODEL, user.vehicleModel)
                }
            }
        }
    }
}
