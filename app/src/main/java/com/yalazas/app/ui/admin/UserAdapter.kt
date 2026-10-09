package com.yalazas.app.ui.admin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yalazas.app.R
import com.yalazas.app.model.UserProfile
import java.util.Locale

class UserAdapter(
    private val onUserClick: (UserProfile) -> Unit
) : ListAdapter<UserProfile, UserAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_row, parent, false)
        return UserViewHolder(view, onUserClick)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class UserViewHolder(
        itemView: View,
        private val onClick: (UserProfile) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvUserAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvUserName)
        private val tvEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        private val tvRole: TextView = itemView.findViewById(R.id.tvUserRole)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvUserStatus)
        private val btnChangeRole: MaterialButton = itemView.findViewById(R.id.btnChangeRole)

        fun bind(user: UserProfile) {
            val displayName = user.name.ifBlank { "Usuario Sin Nombre" }
            tvName.text = displayName
            tvEmail.text = user.email.ifBlank { "Sin correo registrado" }

            // Avatar Inicial
            val initial = displayName.trim().take(1).uppercase(Locale.getDefault())
            tvAvatar.text = if (initial.isNotBlank()) initial else "U"

            // Formato y Color de Rol
            val roleFormatted = user.role.uppercase(Locale.getDefault())
            when {
                roleFormatted.contains("ADMIN") -> {
                    tvRole.text = "👑 ADMINISTRADOR"
                    tvRole.setTextColor(Color.parseColor("#FFD54F")) // Dorado
                }
                roleFormatted.contains("COCIN") -> {
                    tvRole.text = "👨‍🍳 COCINERO"
                    tvRole.setTextColor(Color.parseColor("#FF9800")) // Naranja
                }
                roleFormatted.contains("MESE") -> {
                    tvRole.text = "🍽️ MESERO"
                    tvRole.setTextColor(Color.parseColor("#29B6F6")) // Cyan
                }
                roleFormatted.contains("REPART") -> {
                    tvRole.text = "🛵 REPARTIDOR"
                    tvRole.setTextColor(Color.parseColor("#AB47BC")) // Púrpura
                }
                else -> {
                    tvRole.text = "👤 CLIENTE"
                    tvRole.setTextColor(Color.parseColor("#A0B2A6")) // Verde suave
                }
            }

            // Estado de Cuenta
            if (user.active) {
                tvStatus.text = "🟢 Activo"
                tvStatus.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                tvStatus.text = "🔴 Desactivado"
                tvStatus.setTextColor(Color.parseColor("#FF5252"))
            }

            itemView.setOnClickListener { onClick(user) }
            btnChangeRole.setOnClickListener { onClick(user) }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<UserProfile>() {
        override fun areItemsTheSame(oldItem: UserProfile, newItem: UserProfile): Boolean = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: UserProfile, newItem: UserProfile): Boolean = oldItem == newItem
    }
}
