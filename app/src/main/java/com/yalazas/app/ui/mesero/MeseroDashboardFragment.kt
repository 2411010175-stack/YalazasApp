package com.yalazas.app.ui.mesero

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.databinding.DialogoCobroMesaBinding
import com.yalazas.app.databinding.DialogoProductoMesaBinding
import com.yalazas.app.databinding.FragmentMeseroBinding
import com.yalazas.app.databinding.ItemBorradorMesaBinding
import com.yalazas.app.model.ArticuloCarrito
import com.yalazas.app.model.Order
import com.yalazas.app.model.OrderStatus
import com.yalazas.app.model.Product
import com.yalazas.app.model.Role
import com.yalazas.app.ui.dialogos.DialogoCerrarSesion
import com.yalazas.app.repositorio.ConstructorPedido
import com.yalazas.app.repositorio.RepositorioAutenticacion
import com.yalazas.app.repositorio.RepositorioPedidos
import com.yalazas.app.repositorio.RepositorioMesas
import com.yalazas.app.repositorio.RepositorioProductos
import com.yalazas.app.repositorio.ServiciosFirebase
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/** Salón: toma pedidos sin usar el carrito del cliente y sigue las mesas en tiempo real. */
class MeseroDashboardFragment : Fragment(R.layout.fragment_mesero) {
    private var _binding: FragmentMeseroBinding? = null
    private val binding get() = _binding!!
    private val repositorioProductos = RepositorioProductos()
    private val modeloVista: ModeloVistaMesero by viewModels()
    private var escuchaPedidos: ListenerRegistration? = null
    private var escuchaMesas: ListenerRegistration? = null
    private val borrador get() = modeloVista.borrador
    private var catalogo = emptyList<Product>()
    private var pedidos = emptyList<Order>()
    private var mesasActivas = emptyList<Int>()
    private enum class Seccion { MESAS, POR_SERVIR, HISTORIAL }
    private enum class FiltroMesas { TODAS, LIBRES, OCUPADAS }
    private var seccion = Seccion.MESAS
    private var filtroMesas = FiltroMesas.TODAS
    private var mostrarNuevaComanda = false
    private lateinit var adaptadorCatalogo: AdaptadorProductosMesa
    private lateinit var adaptadorPedidos: AdaptadorPedidosMesa
    private lateinit var adaptadorMesas: AdaptadorMesasSalon
    private lateinit var adaptadorSelectorMesas: AdaptadorMesasSalon

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMeseroBinding.bind(view)
        val perfil = RepositorioAutenticacion.currentProfile
        if (perfil == null || Role.desdeTexto(perfil.role) != Role.MESERO || !perfil.active) {
            findNavController().navigate(R.id.nav_login)
            return
        }
        seccion = savedInstanceState?.getString(CLAVE_SECCION)?.let { runCatching { Seccion.valueOf(it) }.getOrNull() }
            ?: Seccion.MESAS
        filtroMesas = savedInstanceState?.getString(CLAVE_FILTRO)?.let { runCatching { FiltroMesas.valueOf(it) }.getOrNull() }
            ?: FiltroMesas.TODAS
        mostrarNuevaComanda = arguments?.getBoolean(CLAVE_NUEVA_COMANDA) == true
        mesaSeleccionada = savedInstanceState?.getInt(CLAVE_MESA_SELECCIONADA)
            ?: arguments?.getInt(CLAVE_MESA_SELECCIONADA) ?: 0
        pasoNuevaComanda = savedInstanceState?.getInt(CLAVE_PASO_COMANDA) ?: 1
        if (mostrarNuevaComanda && pasoNuevaComanda == 3 && borrador.isEmpty()) pasoNuevaComanda = 2
        binding.tvSaludoMesero.text = "Hola, ${perfil.name.ifBlank { "mesero" }} · Comandas del local en tiempo real"
        adaptadorCatalogo = AdaptadorProductosMesa(::configurarProducto)
        adaptadorPedidos = AdaptadorPedidosMesa(::confirmarCobro)
        adaptadorMesas = AdaptadorMesasSalon()
        adaptadorSelectorMesas = AdaptadorMesasSalon { numero ->
            mesaSeleccionada = numero
            actualizarEstadoVisualMesas()
        }
        binding.listaMesasSalon.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.listaMesasSalon.adapter = adaptadorMesas
        binding.listaSelectorMesas.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.listaSelectorMesas.adapter = adaptadorSelectorMesas
        binding.listaProductosMesa.layoutManager = LinearLayoutManager(requireContext())
        binding.listaProductosMesa.adapter = adaptadorCatalogo
        binding.listaPedidosMesa.layoutManager = LinearLayoutManager(requireContext())
        binding.listaPedidosMesa.adapter = adaptadorPedidos

        binding.btnNuevaComandaMesero.setOnClickListener {
            findNavController().navigate(R.id.nav_mesero_nueva_comanda, bundleOf(CLAVE_NUEVA_COMANDA to true))
        }
        binding.btnCerrarNuevaComandaMesero.setOnClickListener { volverAMesas() }
        binding.navMesasMesero.setOnClickListener { cambiarSeccion(Seccion.MESAS) }
        binding.navServirMesero.setOnClickListener { cambiarSeccion(Seccion.POR_SERVIR) }
        binding.navHistorialMesero.setOnClickListener { cambiarSeccion(Seccion.HISTORIAL) }
        binding.btnTodasMesero.setOnClickListener { cambiarFiltro(FiltroMesas.TODAS) }
        binding.btnLibresMesero.setOnClickListener { cambiarFiltro(FiltroMesas.LIBRES) }
        binding.btnOcupadasMesero.setOnClickListener { cambiarFiltro(FiltroMesas.OCUPADAS) }
        binding.inputBuscarProductoMesa.addTextChangedListener { filtrarCatalogo() }
        binding.btnGuardarComandaMesero.setOnClickListener { confirmarComanda() }
        binding.btnSalirMesero.setOnClickListener {
            DialogoCerrarSesion.crear("¿Deseas salir del panel de mesero?")
                .definirAccionConfirmacion {
                    RepositorioAutenticacion.cerrarSesion()
                    findNavController().navigate(
                        R.id.nav_login, null,
                        NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                    )
                }
                .show(parentFragmentManager, DialogoCerrarSesion.TAG)
        }
        configurarSelectorMesas()
        configurarNavegacionPasos()
        mostrarPanel(mostrarNuevaComanda)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, mostrarNuevaComanda) {
            if (pasoNuevaComanda > 1) irAPaso(pasoNuevaComanda - 1) else volverAMesas()
        }
        if (!mostrarNuevaComanda) {
            val resultado = findNavController().currentBackStackEntry?.savedStateHandle
            resultado?.getLiveData<String>(CLAVE_MENSAJE_COMANDA)?.observe(viewLifecycleOwner) { mensaje ->
                aviso(mensaje)
                resultado.remove<String>(CLAVE_MENSAJE_COMANDA)
            }
        }
        mostrarBorrador()
        escucharMesas()
        escucharPedidos()
        if (mostrarNuevaComanda) observarCarta()
    }

    private var mesaSeleccionada: Int = 0
    private var pasoNuevaComanda: Int = 1

    private fun configurarNavegacionPasos() {
        binding.btnIrACartaPaso2.setOnClickListener {
            if (mesaSeleccionada !in mesasActivas) {
                aviso("Selecciona una mesa antes de continuar.", true)
                return@setOnClickListener
            }
            irAPaso(2)
        }
        binding.btnCambiarMesaPaso2.setOnClickListener {
            irAPaso(1)
        }
        binding.btnRevisarComandaPaso2.setOnClickListener {
            if (borrador.isEmpty()) {
                aviso("Agrega al menos un plato de la carta antes de continuar.", true)
                return@setOnClickListener
            }
            irAPaso(3)
        }
        binding.btnVolverACartaPaso3.setOnClickListener {
            irAPaso(2)
        }
    }

    private fun irAPaso(paso: Int) {
        pasoNuevaComanda = paso
        binding.subpanelPaso1Mesa.visibility = if (paso == 1) View.VISIBLE else View.GONE
        binding.subpanelPaso2Carta.visibility = if (paso == 2) View.VISIBLE else View.GONE
        binding.subpanelPaso3Resumen.visibility = if (paso == 3) View.VISIBLE else View.GONE

        if (paso == 2) {
            binding.tvPaso2MesaActual.text = "Atendiendo Mesa $mesaSeleccionada"
        } else if (paso == 3) {
            val nombre = binding.inputClienteMesa.text?.toString()?.trim().orEmpty()
            binding.tvPaso3ResumenHeader.text = "Mesa $mesaSeleccionada" + if (nombre.isNotBlank()) " · Cliente: $nombre" else ""
            actualizarAvisoMesaOcupada()
        }
        if (paso == 1) binding.subpanelPaso1Mesa.post { binding.subpanelPaso1Mesa.scrollTo(0, 0) }
        if (paso == 3) binding.subpanelPaso3Resumen.post { binding.subpanelPaso3Resumen.scrollTo(0, 0) }
    }

    private fun actualizarAvisoMesaOcupada() {
        val mesaOcupada = pedidos.any {
            it.tableNumber == mesaSeleccionada && it.status !in listOf("ENTREGADO", "CANCELADO")
        }
        binding.tvAvisoMesaOcupada.visibility = if (mesaOcupada) View.VISIBLE else View.GONE
        if (mesaOcupada) binding.tvAvisoMesaOcupada.text =
            "La mesa $mesaSeleccionada ya tiene una comanda activa. Este pedido se enviará por separado."
    }

    private fun volverAMesas() {
        if (borrador.isEmpty()) {
            findNavController().navigateUp()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Salir del nuevo pedido")
            .setMessage("Los platos agregados a esta comanda se perderán.")
            .setNegativeButton("Continuar pedido", null)
            .setPositiveButton("Salir") { _, _ -> findNavController().navigateUp() }
            .show()
    }

    private fun configurarSelectorMesas() {
        actualizarEstadoVisualMesas()
    }

    private fun actualizarEstadoVisualMesas() {
        if (_binding == null) return
        adaptadorSelectorMesas.mostrar(mesasActivas.map(::estadoMesa), mesaSeleccionada)
        binding.tvMesaSeleccionadaMensaje.text = if (mesaSeleccionada == 0)
            if (mesasActivas.isEmpty()) "No hay mesas configuradas. Solicita ayuda a administración."
            else "Selecciona la mesa donde tomarás el pedido" else
            "Mesa $mesaSeleccionada seleccionada para este pedido"
    }

    /** La carta y el selector del mesero cambian cuando administración agrega o retira mesas. */
    private fun escucharMesas() {
        escuchaMesas?.remove()
        escuchaMesas = RepositorioMesas.escuchar({ numeros ->
            if (_binding == null) return@escuchar
            val seleccionAnterior = mesaSeleccionada
            mesasActivas = numeros
            if (mesaSeleccionada != 0 && mesaSeleccionada !in mesasActivas) {
                mesaSeleccionada = 0
                if (mostrarNuevaComanda) irAPaso(1)
                aviso("La mesa $seleccionAnterior fue retirada. Selecciona otra.", true)
            }
            actualizarEstadoVisualMesas()
            mostrarPedidos()
        }, {
            if (_binding == null) return@escuchar
            mesasActivas = emptyList()
            mesaSeleccionada = 0
            actualizarEstadoVisualMesas()
            mostrarPedidos()
            aviso("No se pudo cargar la lista de mesas. Revisa la conexión.", true)
        })
    }

    private fun mostrarPanel(nuevaComanda: Boolean) {
        mostrarNuevaComanda = nuevaComanda
        binding.contenedorDashboardMesero.visibility = if (nuevaComanda) View.GONE else View.VISIBLE
        binding.panelNuevaComandaMesero.visibility = if (nuevaComanda) View.VISIBLE else View.GONE
        if (nuevaComanda) {
            irAPaso(pasoNuevaComanda)
        }
    }

    /** La carta y sus promociones se actualizan sin volver a entrar a la pantalla. */
    private fun observarCarta() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repositorioProductos.observarProductos().collect { resultado ->
                    if (_binding == null) return@collect
                    resultado.onSuccess { catalogo = it; filtrarCatalogo() }
                        .onFailure { binding.tvSinProductosMesa.text = "No se pudo cargar la carta. Revisa tu conexión." }
                }
            }
        }
    }

    private fun filtrarCatalogo() {
        val consulta = binding.inputBuscarProductoMesa.text?.toString()?.trim().orEmpty()
        val visibles = catalogo.filter {
            it.isAvailable && it.price > 0.0 &&
                (consulta.isBlank() || it.name.contains(consulta, ignoreCase = true) ||
                    it.category.contains(consulta, ignoreCase = true))
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
        adaptadorCatalogo.mostrar(visibles)
        binding.tvSinProductosMesa.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
        if (visibles.isEmpty()) binding.tvSinProductosMesa.text =
            if (consulta.isBlank()) "No hay productos disponibles." else "No hay platos que coincidan."
    }

    /** El mesero configura cantidad, notas y salsas sin alterar el producto de la carta. */
    private fun configurarProducto(producto: Product) {
        val vista = DialogoProductoMesaBinding.inflate(layoutInflater)
        vista.tvTituloProductoMesa.text = producto.name
        vista.tvPrecioUnitarioMesa.text = "Precio base ${dinero(producto.price)}"
        var cantidad = 1
        val salsas = mutableListOf<String>()
        val requiereSalsas = producto.allowsSauces && producto.maxSauces > 0
        if (requiereSalsas) {
            vista.tvAyudaSalsasMesa.visibility = View.VISIBLE
            vista.tvAyudaSalsasMesa.text =
                "${producto.maxSauces} salsa(s) incluida(s); S/ 1 por cada salsa adicional."
            val disponibles = producto.availableSauces.ifEmpty { Product.DEFAULT_YALAZA_SAUCES }
            disponibles.forEach { salsa ->
                val chip = Chip(requireContext()).apply {
                    text = salsa
                    isCheckable = true
                    setOnCheckedChangeListener { _, seleccionado ->
                        if (seleccionado) salsas.add(salsa) else salsas.remove(salsa)
                        actualizarTotalProducto(vista, producto, cantidad, salsas)
                    }
                }
                vista.grupoSalsasMesa.addView(chip)
            }
        }
        vista.btnMenosProductoMesa.setOnClickListener {
            cantidad = (cantidad - 1).coerceAtLeast(1)
            vista.tvCantidadProductoMesa.text = cantidad.toString()
            actualizarTotalProducto(vista, producto, cantidad, salsas)
        }
        vista.btnMasProductoMesa.setOnClickListener {
            cantidad = (cantidad + 1).coerceAtMost(50)
            vista.tvCantidadProductoMesa.text = cantidad.toString()
            actualizarTotalProducto(vista, producto, cantidad, salsas)
        }
        actualizarTotalProducto(vista, producto, cantidad, salsas)
        val dialogo = Dialog(requireContext())
        dialogo.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialogo.setContentView(vista.root)
        dialogo.show()
        dialogo.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(R.color.yalaza_bg)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        vista.btnCancelarProductoMesa.setOnClickListener { dialogo.dismiss() }
        vista.btnAgregarProductoConfiguradoMesa.setOnClickListener {
            if (requiereSalsas && salsas.isEmpty()) {
                vista.tvAyudaSalsasMesa.text = "Elige al menos una salsa para este plato."
                vista.tvAyudaSalsasMesa.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.admin_status_cancelled)
                )
                return@setOnClickListener
            }
            val articulo = ArticuloCarrito(
                producto, cantidad, vista.inputNotaProductoMesa.text?.toString()?.trim().orEmpty(), salsas.toList()
            )
            val existente = borrador.find {
                it.product.id == articulo.product.id && it.notes == articulo.notes &&
                    it.selectedSauces == articulo.selectedSauces
            }
            if (existente == null) borrador.add(articulo) else existente.quantity += cantidad
            mostrarBorrador()
            dialogo.dismiss()
        }
    }

    private fun actualizarTotalProducto(
        vista: DialogoProductoMesaBinding, producto: Product, cantidad: Int, salsas: List<String>
    ) {
        vista.tvTotalProductoMesa.text = "Subtotal  ${dinero(ArticuloCarrito(producto, cantidad, selectedSauces = salsas).subtotal)}"
    }

    private fun mostrarBorrador() {
        binding.contenedorBorradorMesero.removeAllViews()
        borrador.forEach { articulo ->
            val fila = ItemBorradorMesaBinding.inflate(LayoutInflater.from(requireContext()), binding.contenedorBorradorMesero, false)
            fila.tvDetalleBorradorMesa.text = "${articulo.quantity} × ${articulo.descripcionFinal}" +
                if (articulo.notes.isBlank()) "" else "\nNota: ${articulo.notes}"
            fila.tvSubtotalBorradorMesa.text = dinero(articulo.subtotal)
            fila.btnQuitarBorradorMesa.setOnClickListener { borrador.remove(articulo); mostrarBorrador() }
            binding.contenedorBorradorMesero.addView(fila.root)
        }
        val total = borrador.sumOf { it.subtotal }
        val cantidadTotal = borrador.sumOf { it.quantity }
        binding.tvBorradorVacioMesero.visibility = if (borrador.isEmpty()) View.VISIBLE else View.GONE
        binding.tvTotalMesero.text = "Total  ${dinero(total)}"
        binding.tvTotalPaso2.text = dinero(total)
        binding.tvCantidadPlatosBorrador.text = "$cantidadTotal plato(s) en comanda"
    }

    private fun confirmarComanda() {
        val mesa = mesaSeleccionada
        if (mesa !in mesasActivas) {
            aviso("Esta mesa ya no está disponible. Selecciona otra.", true)
            irAPaso(1)
            return
        }
        if (borrador.isEmpty()) {
            aviso("Agrega al menos un plato a la comanda.", true)
            return
        }
        // El resumen de pantalla completa es la confirmación final de la comanda.
        guardarComanda(mesa)
    }

    private fun guardarComanda(mesa: Int) {
        val perfil = RepositorioAutenticacion.currentProfile ?: return
        val solicitud = try {
            ConstructorPedido.paraMesa(
                perfil, mesa, binding.inputClienteMesa.text?.toString().orEmpty()
            ).agregarArticulos(borrador)
                .agregarComentario(binding.inputNotaMesa.text?.toString().orEmpty())
                .construir()
        } catch (error: Exception) {
            aviso(error.message ?: "La comanda no es válida.", true)
            return
        }
        binding.btnGuardarComandaMesero.isEnabled = false
        RepositorioPedidos.registrarPedido(solicitud) { resultado ->
            if (_binding == null) return@registrarPedido
            binding.btnGuardarComandaMesero.isEnabled = true
            resultado.onSuccess {
                borrador.clear()
                mostrarBorrador()
                binding.inputClienteMesa.text?.clear()
                binding.inputNotaMesa.text?.clear()
                findNavController().previousBackStackEntry?.savedStateHandle?.set(
                    CLAVE_MENSAJE_COMANDA, "Comanda enviada a cocina para mesa $mesa"
                )
                findNavController().navigateUp()
            }.onFailure { aviso(it.message ?: "No se pudo guardar la comanda.", true) }
        }
    }

    /** Una consulta de mesa permite ver las comandas de todos los meseros activos. */
    private fun escucharPedidos() {
        escuchaPedidos?.remove()
        escuchaPedidos = ServiciosFirebase.firestore.collection("orders")
            .whereEqualTo("serviceType", "MESA")
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener
                binding.progresoMesero.visibility = View.GONE
                if (error != null) {
                    binding.tvVacioMesero.text = "No se pudieron cargar las mesas. Revisa la conexión y los permisos."
                    binding.tvVacioMesero.visibility = View.VISIBLE
                    return@addSnapshotListener
                }
                pedidos = snapshot?.documents?.mapNotNull { documento ->
                    documento.toObject(Order::class.java)?.copy(id = documento.id)
                }.orEmpty()
                mostrarPedidos()
                actualizarEstadoVisualMesas()
                if (mostrarNuevaComanda && pasoNuevaComanda == 3) actualizarAvisoMesaOcupada()
            }
    }

    private fun cambiarSeccion(nueva: Seccion) {
        seccion = nueva
        binding.panelMesasMesero.scrollTo(0, 0)
        binding.barraSalonMesero.setExpanded(true, false)
        mostrarPedidos()
    }

    private fun cambiarFiltro(nuevo: FiltroMesas) {
        filtroMesas = nuevo
        mostrarPedidos()
    }

    private fun estadoMesa(numero: Int): EstadoMesaSalon {
        val deMesa = pedidos.filter { it.tableNumber == numero &&
            it.status in listOf("PENDIENTE", "EN_PROCESO", "LISTO_PARA_ENTREGA") }
        val listos = deMesa.count { it.status == OrderStatus.LISTO_PARA_ENTREGA.key }
        val cocina = deMesa.count { it.status == OrderStatus.EN_PROCESO.key }
        return when {
            listos > 0 -> EstadoMesaSalon(numero, "lista para servir", "¡Listo para servir!", R.color.yalaza_pink)
            cocina > 0 -> EstadoMesaSalon(numero, "en cocina", "$cocina en cocina", R.color.yalaza_yellow)
            deMesa.isNotEmpty() -> EstadoMesaSalon(numero, "comanda nueva", "${deMesa.size} comanda nueva", R.color.yalaza_orange)
            else -> EstadoMesaSalon(numero, "libre", "Libre", R.color.yalaza_lime_dark)
        }
    }

    private fun actualizarNavegacion() {
        val rosa = ContextCompat.getColor(requireContext(), R.color.yalaza_pink)
        val gris = ContextCompat.getColor(requireContext(), R.color.cocina_text_secondary)
        listOf(
            Triple(seccion == Seccion.MESAS, binding.textoNavMesasMesero, binding.iconoNavMesasMesero),
            Triple(seccion == Seccion.POR_SERVIR, binding.textoNavServirMesero, binding.iconoNavServirMesero),
            Triple(seccion == Seccion.HISTORIAL, binding.textoNavHistorialMesero, binding.iconoNavHistorialMesero)
        ).forEach { (activo, texto, icono) ->
            val color = if (activo) rosa else gris
            texto.setTextColor(color)
            icono.setColorFilter(color)
        }
    }

    private fun mostrarPedidos() {
        val activos = pedidos.filter { it.status in listOf("PENDIENTE", "EN_PROCESO", "LISTO_PARA_ENTREGA") }
        binding.tvMesasMesero.text = mesasActivas.size.toString()
        val listos = activos.filter { it.status == OrderStatus.LISTO_PARA_ENTREGA.key }
        binding.tvListosMesero.text = listos.size.toString()
        val hoy = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val entregadosHoy = pedidos.filter {
            it.status == OrderStatus.ENTREGADO.key &&
                (it.paymentVerifiedAt ?: it.timestamp)?.toDate()?.time?.let { tiempo -> tiempo >= hoy } == true
        }
        val visibles = when (seccion) {
            Seccion.MESAS -> activos.sortedBy { it.timestamp?.toDate()?.time ?: Long.MAX_VALUE }
            Seccion.POR_SERVIR -> listos.sortedBy { it.timestamp?.toDate()?.time ?: Long.MAX_VALUE }
            Seccion.HISTORIAL -> entregadosHoy.sortedByDescending {
                (it.paymentVerifiedAt ?: it.timestamp)?.toDate()?.time ?: 0L
            }
        }
        val mesasVisibles = mesasActivas.map(::estadoMesa).filter {
            when (filtroMesas) {
                FiltroMesas.TODAS -> true
                FiltroMesas.LIBRES -> it.estado == "libre"
                FiltroMesas.OCUPADAS -> it.estado != "libre"
            }
        }
        adaptadorMesas.mostrar(mesasVisibles)
        binding.tvVacioFiltroMesas.visibility = if (seccion == Seccion.MESAS && mesasVisibles.isEmpty())
            View.VISIBLE else View.GONE
        binding.tvVacioFiltroMesas.text = if (mesasActivas.isEmpty())
            "No hay mesas configuradas. Solicita al administrador que agregue mesas."
        else if (filtroMesas == FiltroMesas.LIBRES)
            "Todas las mesas están ocupadas." else "No hay mesas ocupadas."
        binding.contenedorEstadisticasMesero.visibility = if (seccion == Seccion.MESAS) View.VISIBLE else View.GONE
        binding.btnNuevaComandaMesero.visibility = if (seccion == Seccion.MESAS) View.VISIBLE else View.GONE
        binding.contenedorFiltrosMesa.visibility = if (seccion == Seccion.MESAS) View.VISIBLE else View.GONE
        binding.listaMesasSalon.visibility = if (seccion == Seccion.MESAS) View.VISIBLE else View.GONE
        binding.tvTituloSeccionMesero.text = when (seccion) {
            Seccion.MESAS -> "Salón y Mesas"
            Seccion.POR_SERVIR -> "Por servir"
            Seccion.HISTORIAL -> "Historial de hoy"
        }
        binding.tvSaludoMesero.text = when (seccion) {
            Seccion.MESAS -> "Hola, ${RepositorioAutenticacion.currentProfile?.name?.ifBlank { "mesero" } ?: "mesero"}"
            Seccion.POR_SERVIR -> "${listos.size} ${if (listos.size == 1) "pedido listo" else "pedidos listos"}"
            Seccion.HISTORIAL -> "${entregadosHoy.size} ${if (entregadosHoy.size == 1) "entrega" else "entregas"} confirmadas hoy"
        }
        binding.tvEncabezadoPedidosMesero.text = when (seccion) {
            Seccion.MESAS -> "Comandas en curso"
            Seccion.POR_SERVIR -> "Listos para entregar"
            Seccion.HISTORIAL -> "Entregas de hoy"
        }
        val uid = ServiciosFirebase.auth.currentUser?.uid.orEmpty()
        val nombre = RepositorioAutenticacion.currentProfile?.name?.ifBlank { "este mesero" } ?: "este mesero"
        adaptadorPedidos.mostrar(visibles, seccion == Seccion.HISTORIAL, uid, nombre)
        binding.tvVacioMesero.text = when (seccion) {
            Seccion.MESAS -> "No hay comandas activas. Crea un nuevo pedido."
            Seccion.POR_SERVIR -> "Aún no hay pedidos listos para servir."
            Seccion.HISTORIAL -> "Aún no hay pedidos entregados hoy."
        }
        binding.tvVacioMesero.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
        listOf(
            binding.btnTodasMesero to (filtroMesas == FiltroMesas.TODAS),
            binding.btnLibresMesero to (filtroMesas == FiltroMesas.LIBRES),
            binding.btnOcupadasMesero to (filtroMesas == FiltroMesas.OCUPADAS)
        ).forEach { (boton, elegido) ->
            boton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                if (elegido) R.color.yalaza_pink else R.color.cocina_surface))
            boton.setTextColor(ContextCompat.getColor(requireContext(),
                if (elegido) R.color.white else R.color.cocina_text_secondary))
        }
        actualizarNavegacion()
    }

    /** La confirmación manual queda registrada con UID y hora en la misma operación de entrega. */
    private fun confirmarCobro(pedido: Order) {
        val vista = DialogoCobroMesaBinding.inflate(layoutInflater)
        vista.tvTituloCobroMesa.text = "Cobrar y servir · Mesa ${pedido.tableNumber}"
        vista.tvResumenCobroMesa.text = "Total a comprobar: ${dinero(pedido.total)}"
        vista.inputMontoCobroMesa.setText(String.format(Locale.US, "%.2f", pedido.total))
        fun actualizarMetodo() {
            val efectivo = vista.grupoMetodoMesa.checkedRadioButtonId == R.id.opcionEfectivoMesa
            vista.layoutMontoCobroMesa.visibility = if (efectivo) View.VISIBLE else View.GONE
            vista.tvVueltoCobroMesa.visibility = if (efectivo) View.VISIBLE else View.GONE
            val recibido = vista.inputMontoCobroMesa.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
            vista.tvVueltoCobroMesa.text = if (efectivo && recibido != null && recibido >= pedido.total)
                "Vuelto: ${dinero(recibido - pedido.total)}" else ""
        }
        vista.grupoMetodoMesa.setOnCheckedChangeListener { _, _ ->
            vista.tvResumenCobroMesa.text = "Total a comprobar: ${dinero(pedido.total)}"
            vista.tvResumenCobroMesa.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.cocina_text_secondary))
            actualizarMetodo()
        }
        vista.inputMontoCobroMesa.addTextChangedListener { actualizarMetodo() }
        actualizarMetodo()
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setView(vista.root)
            .setNegativeButton("Volver", null)
            .setPositiveButton("Confirmar entrega", null)
            .show()
        dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(requireContext(), R.color.yalaza_lime_dark)
        )
        dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { boton ->
            val metodo = when (vista.grupoMetodoMesa.checkedRadioButtonId) {
                R.id.opcionEfectivoMesa -> "EFECTIVO"
                R.id.opcionYapeMesa -> "YAPE"
                R.id.opcionPlinMesa -> "PLIN"
                R.id.opcionTransferenciaMesa -> "TRANSFERENCIA"
                R.id.opcionTarjetaMesa -> "TARJETA"
                else -> ""
            }
            val recibido = if (metodo == "EFECTIVO")
                vista.inputMontoCobroMesa.text?.toString()?.replace(',', '.')?.toDoubleOrNull() else pedido.total
            if (metodo.isBlank()) {
                vista.tvResumenCobroMesa.text = "Selecciona cómo pagó el cliente antes de confirmar."
                vista.tvResumenCobroMesa.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.admin_status_cancelled))
                return@setOnClickListener
            }
            if (recibido == null || !recibido.isFinite() || recibido < pedido.total) {
                vista.layoutMontoCobroMesa.error = "El monto recibido debe cubrir el total."
                return@setOnClickListener
            }
            vista.layoutMontoCobroMesa.error = null
            if (!vista.checkConfirmarCobroMesa.isChecked) {
                vista.checkConfirmarCobroMesa.error = "Confirma el pago y la entrega."
                return@setOnClickListener
            }
            vista.checkConfirmarCobroMesa.error = null
            boton.isEnabled = false
            RepositorioPedidos.entregarPedidoMesa(pedido.id, metodo, recibido) { resultado ->
                if (_binding == null) return@entregarPedidoMesa
                resultado.onSuccess {
                    dialogo.dismiss()
                    aviso("Mesa ${pedido.tableNumber}: pago comprobado y pedido entregado")
                }.onFailure {
                    boton.isEnabled = true
                    vista.tvResumenCobroMesa.text = it.message ?: "No se pudo completar la entrega. Revisa la comanda."
                    vista.tvResumenCobroMesa.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.admin_status_cancelled)
                    )
                }
            }
        }
    }

    private fun aviso(texto: String, error: Boolean = false) {
        if (_binding == null) return
        Snackbar.make(binding.root, texto, Snackbar.LENGTH_LONG).apply {
            setBackgroundTint(ContextCompat.getColor(requireContext(),
                if (error) R.color.cocina_aviso_error else R.color.cocina_nav_bg))
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            show()
        }
    }

    private fun dinero(monto: Double) = String.format(Locale.getDefault(), "S/ %.2f", monto)

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(CLAVE_SECCION, seccion.name)
        outState.putString(CLAVE_FILTRO, filtroMesas.name)
        outState.putInt(CLAVE_MESA_SELECCIONADA, mesaSeleccionada)
        outState.putInt(CLAVE_PASO_COMANDA, pasoNuevaComanda)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        escuchaPedidos?.remove()
        escuchaMesas?.remove()
        escuchaPedidos = null
        escuchaMesas = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val CLAVE_SECCION = "seccion_mesero"
        private const val CLAVE_FILTRO = "filtro_mesas_mesero"
        private const val CLAVE_NUEVA_COMANDA = "nueva_comanda_mesero"
        private const val CLAVE_MESA_SELECCIONADA = "mesa_seleccionada_mesero"
        private const val CLAVE_PASO_COMANDA = "paso_comanda_mesero"
        private const val CLAVE_MENSAJE_COMANDA = "mensaje_comanda_mesero"
    }
}
