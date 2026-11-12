package com.yalazas.app.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.yalazas.app.R
import com.yalazas.app.model.AuditLog
import com.yalazas.app.model.UserProfile
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.ServiciosFirebase
import java.util.Locale

/** Lista usuarios y permite gestionar roles y activación. */
class AdminUsersFragment : Fragment(R.layout.fragment_admin_users) {

    private val db = ServiciosFirebase.firestore
    private var usersListener: ListenerRegistration? = null
    private lateinit var adapter: AdaptadorUsuarios
    private var allUsers = listOf<UserProfile>()
    private var currentFilter = ""

    private lateinit var tvUsersCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: View

    // Definición de Roles del Sistema Yalazas
    private val availableRoles = listOf(
        "ADMINISTRADOR" to "👑 ADMINISTRADOR (Acceso total al sistema)",
        "COCINERO" to "👨‍🍳 COCINERO (Comandas y Cocina)",
        "MESERO" to "🍽️ MESERO (Salón y Pedidos)",
        "REPARTIDOR" to "🛵 REPARTIDOR (Entregas y Delivery)",
        "CLIENTE" to "👤 CLIENTE (Usuario Estándar)"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUsersCount = view.findViewById(R.id.tvUsersCount)
        progressBar = view.findViewById(R.id.progressBar)
        layoutEmpty = view.findViewById(R.id.layoutEmptyUsers)
        val recyclerView = view.findViewById<RecyclerView>(R.id.usersRecyclerView)
        val inputSearch = view.findViewById<TextInputEditText>(R.id.inputSearchUser)

        adapter = AdaptadorUsuarios { selectedUser ->
            showEditUserRoleDialog(selectedUser)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentFilter = s?.toString()?.trim() ?: ""
                filterUsers(currentFilter)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        escucharUsuariosEnTiempoReal()
    }

    /** Escucha perfiles de `users` para actualizar la lista administrativa. */
    private fun escucharUsuariosEnTiempoReal() {
        progressBar.visibility = View.VISIBLE
        usersListener?.remove()
        usersListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE
                if (error != null) {
                    Toast.makeText(requireContext(), "Error al cargar usuarios: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val userList = mutableListOf<UserProfile>()
                    for (doc in snapshot.documents) {
                        val profile = doc.toObject(UserProfile::class.java)
                        if (profile != null) {
                            // El ID real y único del documento en Firestore es siempre doc.id
                            userList.add(profile.copy(uid = doc.id))
                        }
                    }

                    allUsers = userList
                    tvUsersCount.text = "${allUsers.size} usuarios"
                    filterUsers(currentFilter)
                }
            }
    }

    private fun filterUsers(query: String) {
        val lower = query.lowercase(Locale.getDefault())
        val filtered = if (lower.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { user ->
                user.name.lowercase(Locale.getDefault()).contains(lower) ||
                user.email.lowercase(Locale.getDefault()).contains(lower) ||
                user.role.lowercase(Locale.getDefault()).contains(lower) ||
                user.phone.lowercase(Locale.getDefault()).contains(lower)
            }
        }

        adapter.submitList(filtered)
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditUserRoleDialog(user: UserProfile) {
        val currentAdminId = RepositorioAutenticacion.currentProfile?.uid ?: ""

        // Evitar que el administrador se quite permisos a sí mismo por error
        if (user.uid.isNotBlank() && user.uid == currentAdminId) {
            Toast.makeText(requireContext(), "No puedes modificar los permisos de tu propia cuenta.", Toast.LENGTH_SHORT).show()
            return
        }

        val sheet = HojaCambioRolAdministrador.crear(user)
        sheet.onRoleSaved = { newRole, isActive, type, plate, model ->
            saveUserRoleAndStatus(user, newRole, isActive, type, plate, model, currentAdminId)
        }
        sheet.show(parentFragmentManager, HojaCambioRolAdministrador.TAG)
    }

    private fun saveUserRoleAndStatus(
        user: UserProfile, newRole: String, isActive: Boolean,
        type: String, plate: String, model: String, adminId: String
    ) {
        val targetUid = user.uid.trim()
        if (targetUid.isBlank()) {
            Toast.makeText(requireContext(), "Error: El usuario no tiene un identificador UID válido.", Toast.LENGTH_SHORT).show()
            return
        }

        val roleChanged = user.role != newRole
        val statusChanged = user.active != isActive
        val vehicleChanged = newRole == "REPARTIDOR" &&
            (user.vehicleType != type || user.vehiclePlate != plate || user.vehicleModel != model)

        if (!roleChanged && !statusChanged && !vehicleChanged) {
            return
        }

        progressBar.visibility = View.VISIBLE

        val updates = mutableMapOf<String, Any>(
            "uid" to targetUid,
            "role" to newRole,
            "active" to isActive,
            "updatedAt" to System.currentTimeMillis()
        )
        if (vehicleChanged) {
            updates["vehicleType"] = type
            updates["vehiclePlate"] = plate
            updates["vehicleModel"] = model
        }

        // Usamos set con merge para que cree o actualice sin fallar con NOT_FOUND
        db.collection("users").document(targetUid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                if (roleChanged) {
                    val logRole = AuditLog(
                        id = db.collection("auditoria").document().id,
                        usuarioAfectadoId = targetUid,
                        usuarioAdministradorId = adminId,
                        accion = "CAMBIO_ROL",
                        rolAnterior = user.role,
                        rolNuevo = newRole
                    )
                    db.collection("auditoria").document(logRole.id).set(logRole)
                }

                if (statusChanged) {
                    val actionName = if (isActive) "ACTIVAR_CUENTA" else "DESACTIVAR_CUENTA"
                    val logStatus = AuditLog(
                        id = db.collection("auditoria").document().id,
                        usuarioAfectadoId = targetUid,
                        usuarioAdministradorId = adminId,
                        accion = actionName,
                        rolAnterior = newRole,
                        rolNuevo = newRole
                    )
                    db.collection("auditoria").document(logStatus.id).set(logStatus)
                }

                val displayName = user.name.ifBlank { user.email.ifBlank { "usuario" } }
                Toast.makeText(requireContext(), "Perfil de $displayName actualizado correctamente", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error al actualizar perfil: ${e.message}", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usersListener?.remove()
        usersListener = null
    }
}
