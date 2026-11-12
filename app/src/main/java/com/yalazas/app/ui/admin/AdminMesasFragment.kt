package com.yalazas.app.ui.admin

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.yalazas.app.R
import com.yalazas.app.databinding.FragmentAdminMesasBinding
import com.yalazas.app.model.Role
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioMesas
import com.yalazas.app.repositorio.ServiciosFirebase

/** Configura las mesas disponibles y muestra su ocupación a partir de las comandas reales. */
class AdminMesasFragment : Fragment(R.layout.fragment_admin_mesas) {
    private var _binding: FragmentAdminMesasBinding? = null
    private val binding get() = _binding!!
    private var escuchaMesas: ListenerRegistration? = null
    private var escuchaPedidos: ListenerRegistration? = null
    private lateinit var adaptador: AdaptadorMesasAdmin
    private var mesas = emptyList<Int>()
    private var ocupacion = emptyMap<Int, Int>()
    private var cargado = false
    private var ocupacionCargada = false
    private var errorMesas: String? = null
    private var operando = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminMesasBinding.bind(view)
        val perfil = RepositorioAutenticacion.currentProfile
        if (perfil == null || !perfil.active || Role.desdeTexto(perfil.role) != Role.ADMIN) {
            findNavController().navigateUp()
            return
        }
        adaptador = AdaptadorMesasAdmin(::confirmarQuitar)
        binding.listaMesasAdmin.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.listaMesasAdmin.adapter = adaptador
        binding.btnVolverMesasAdmin.setOnClickListener { findNavController().navigateUp() }
        binding.btnAgregarMesaAdmin.setOnClickListener { mostrarDialogoAgregar() }
        dibujar()

        escuchaMesas = RepositorioMesas.escuchar({ numeros ->
            if (_binding == null) return@escuchar
            mesas = numeros
            cargado = true
            errorMesas = null
            dibujar()
        }, { error ->
            if (_binding == null) return@escuchar
            cargado = false
            errorMesas = "No se pudieron cargar las mesas: ${error.localizedMessage}"
            dibujar()
        })
        escuchaPedidos = ServiciosFirebase.firestore.collection("orders")
            .whereEqualTo("serviceType", "MESA")
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener
                if (error != null) {
                    ocupacionCargada = false
                    dibujar()
                    mostrarAviso("No se pudo actualizar la ocupación de las mesas.", true)
                    return@addSnapshotListener
                }
                ocupacion = snapshot?.documents.orEmpty()
                    .filter { it.getString("status") !in setOf("ENTREGADO", "CANCELADO") }
                    .groupingBy { (it.getLong("tableNumber") ?: 0L).toInt() }.eachCount()
                ocupacionCargada = true
                dibujar()
            }
    }

    private fun dibujar() {
        if (_binding == null) return
        val ocupadas = mesas.count { (ocupacion[it] ?: 0) > 0 }
        binding.tvTotalMesasAdmin.text = "${mesas.size}\nMesas"
        binding.tvMesasLibresAdmin.text = "${if (ocupacionCargada) mesas.size - ocupadas else "—"}\nLibres"
        binding.tvMesasOcupadasAdmin.text = "${if (ocupacionCargada) ocupadas else "—"}\nOcupadas"
        binding.btnAgregarMesaAdmin.isEnabled = cargado && !operando && mesas.size < 200
        if (::adaptador.isInitialized) adaptador.mostrar(mesas.map { MesaAdminVista(it, ocupacion[it] ?: 0) },
            operando || !ocupacionCargada)
        binding.tvMensajeMesasAdmin.visibility = if (!cargado || mesas.isEmpty()) View.VISIBLE else View.GONE
        binding.tvMensajeMesasAdmin.text = errorMesas ?: if (!cargado) "Cargando mesas…" else
            "Aún no hay mesas. Usa Agregar mesa para preparar el salón."
    }

    private fun mostrarDialogoAgregar() {
        val contenedor = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val margen = (20 * resources.displayMetrics.density).toInt()
            setPadding(margen, margen / 2, margen, 0)
        }
        val campo = TextInputLayout(requireContext()).apply {
            hint = "Número de mesa (1 a 200)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val entrada = TextInputEditText(campo.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((1..200).firstOrNull { it !in mesas }?.toString().orEmpty())
            selectAll()
        }
        campo.addView(entrada)
        contenedor.addView(campo)
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Agregar mesa al salón")
            .setMessage("El mesero podrá usarla para nuevas comandas en cuanto se guarde.")
            .setView(contenedor)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Agregar", null)
            .show()
        dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val numero = entrada.text?.toString()?.trim()?.toIntOrNull()
            when {
                numero == null || numero !in 1..200 -> campo.error = "Escribe un número entre 1 y 200"
                numero in mesas -> campo.error = "La mesa $numero ya existe"
                else -> {
                    campo.error = null
                    operando = true
                    dibujar()
                    RepositorioMesas.agregar(numero) { resultado ->
                        if (_binding == null) return@agregar
                        operando = false
                        dibujar()
                        resultado.onSuccess { dialogo.dismiss(); mostrarAviso("Mesa $numero agregada") }
                            .onFailure { campo.error = it.localizedMessage ?: "No se pudo agregar la mesa" }
                    }
                }
            }
        }
    }

    private fun confirmarQuitar(numero: Int) {
        if (operando || (ocupacion[numero] ?: 0) > 0) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quitar mesa $numero")
            .setMessage("Dejará de aparecer para nuevas comandas. Los pedidos anteriores conservarán su número.")
            .setNegativeButton("Conservar", null)
            .setPositiveButton("Quitar") { _, _ -> verificarYQuitar(numero) }
            .show()
    }

    private fun verificarYQuitar(numero: Int) {
        operando = true
        dibujar()
        // Consulta al servidor para no retirar una mesa que otra pantalla acaba de ocupar.
        ServiciosFirebase.firestore.collection("orders").whereEqualTo("serviceType", "MESA")
            .get(Source.SERVER).addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val enUso = snapshot.documents.any {
                    it.getLong("tableNumber") == numero.toLong() &&
                        it.getString("status") !in setOf("ENTREGADO", "CANCELADO")
                }
                if (enUso) {
                    operando = false
                    dibujar()
                    mostrarAviso("La mesa $numero tiene una comanda en curso.", true)
                    return@addOnSuccessListener
                }
                RepositorioMesas.quitar(numero) { resultado ->
                    if (_binding == null) return@quitar
                    operando = false
                    dibujar()
                    resultado.onSuccess { mostrarAviso("Mesa $numero retirada del salón") }
                        .onFailure { mostrarAviso(it.localizedMessage ?: "No se pudo quitar la mesa", true) }
                }
            }.addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                operando = false
                dibujar()
                mostrarAviso("No se pudo comprobar si la mesa está libre.", true)
            }
    }

    private fun mostrarAviso(mensaje: String, error: Boolean = false) {
        if (_binding == null) return
        Snackbar.make(binding.root, mensaje, Snackbar.LENGTH_LONG).apply {
            setBackgroundTint(ContextCompat.getColor(requireContext(),
                if (error) R.color.cocina_aviso_error else R.color.admin_ui_button_secondary))
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            show()
        }
    }

    override fun onDestroyView() {
        escuchaMesas?.remove()
        escuchaPedidos?.remove()
        escuchaMesas = null
        escuchaPedidos = null
        _binding = null
        super.onDestroyView()
    }
}
