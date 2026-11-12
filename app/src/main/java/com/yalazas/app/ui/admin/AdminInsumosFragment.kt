package com.yalazas.app.ui.admin

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.firestore.ListenerRegistration
import com.yalazas.app.R
import com.yalazas.app.model.CalculadoraInsumos
import com.yalazas.app.model.IngredienteReceta
import com.yalazas.app.model.Insumo
import com.yalazas.app.model.Product
import com.yalazas.app.model.RecetaProducto
import com.yalazas.app.repositorio.RepositorioInsumos
import com.yalazas.app.repositorio.ServiciosFirebase
import java.util.Locale
import java.text.Normalizer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.min

/** Almacén del administrador: el cálculo de stock sigue en el modelo y en Firestore. */
class AdminInsumosFragment : Fragment(R.layout.fragment_admin_insumos) {
    private val db = ServiciosFirebase.firestore
    private val registros = mutableListOf<ListenerRegistration>()
    private var insumos = emptyList<Insumo>()
    private var recetas = emptyList<RecetaProducto>()
    private var productos = emptyList<Product>()
    private var vistaActiva: View? = null
    private var insumosCargados = false
    private var filtro = Filtro.TODOS

    private enum class Filtro { TODOS, BAJOS, AGOTADOS }
    private enum class Estado { NORMAL, BAJO, CRITICO }
    private data class Hoja(val dialogo: BottomSheetDialog, val cuerpo: LinearLayout, val pie: LinearLayout)
    private data class Campo(val contenedor: TextInputLayout, val entrada: TextInputEditText)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vistaActiva = view
        view.findViewById<MaterialButton>(R.id.btnRegistrarEntrada).setOnClickListener { elegirInsumoParaEntrada() }
        view.findViewById<MaterialButton>(R.id.btnNuevoInsumo).setOnClickListener { dialogoNuevoInsumo() }
        view.findViewById<MaterialButton>(R.id.btnReceta).setOnClickListener { elegirProducto() }
        view.findViewById<MaterialButton>(R.id.btnEjemploBbq).setOnClickListener { confirmarEjemploBbq() }
        val filtros = view.findViewById<MaterialButtonToggleGroup>(R.id.grupoFiltrosInsumos)
        filtros.check(R.id.filtroTodosInsumos)
        filtros.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    filtro = when (checkedId) {
                        R.id.filtroBajosInsumos -> Filtro.BAJOS
                        R.id.filtroAgotadosInsumos -> Filtro.AGOTADOS
                        else -> Filtro.TODOS
                    }
                    dibujarInsumos()
                }
            }
        registros += RepositorioInsumos.escucharInsumos(
            { insumos = it.sortedBy(Insumo::name); insumosCargados = true; dibujar() }, ::mostrarError
        )
        registros += RepositorioInsumos.escucharRecetas(
            { recetas = it; dibujar() }, ::mostrarError
        )
        registros += db.collection("products").addSnapshotListener { snapshot, error ->
            if (error != null) mostrarError(error)
            else if (snapshot != null) {
                productos = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Product::class.java)?.apply { id = doc.id }
                }.sortedBy(Product::name)
                dibujar()
            }
        }
    }

    private fun dibujar() {
        dibujarInsumos()
        dibujarRecetas()
    }

    private fun dibujarInsumos() {
        val root = vistaActiva ?: return
        val contenedor = root.findViewById<LinearLayout>(R.id.contenedorInsumos)
        contenedor.removeAllViews()
        val bajos = insumos.count { estado(it) == Estado.BAJO }
        val agotados = insumos.count { estado(it) == Estado.CRITICO }
        // Hasta recibir el primer cambio de Firestore, los indicadores no muestran ceros ficticios.
        root.findViewById<TextView>(R.id.tvKpiTotalInsumos)?.text = if (insumosCargados) insumos.size.toString() else "—"
        root.findViewById<TextView>(R.id.tvKpiBajosInsumos)?.text = if (insumosCargados) bajos.toString() else "—"
        root.findViewById<TextView>(R.id.tvKpiAgotadosInsumos)?.text = if (insumosCargados) agotados.toString() else "—"
        root.findViewById<TextView>(R.id.tvResumenInsumos).text = when {
            !insumosCargados -> "Cargando existencias…"
            filtro == Filtro.TODOS -> "${insumos.size} insumos registrados · $bajos bajos · $agotados críticos"
            filtro == Filtro.BAJOS -> "$bajos insumos para reabastecer pronto"
            else -> "$agotados insumos que requieren atención"
        }
        if (!insumosCargados) return
        val visibles = insumos.filter {
            when (filtro) {
                Filtro.TODOS -> true
                Filtro.BAJOS -> estado(it) == Estado.BAJO
                Filtro.AGOTADOS -> estado(it) == Estado.CRITICO
            }
        }
        if (visibles.isEmpty()) {
            val mensaje = when (filtro) {
                Filtro.TODOS -> "Aún no hay insumos. Crea uno para comenzar."
                Filtro.BAJOS -> "No hay insumos con stock bajo."
                Filtro.AGOTADOS -> "No hay insumos críticos."
            }
            contenedor.addView(tarjetaMensaje(mensaje))
        } else visibles.forEach { contenedor.addView(tarjetaInsumo(it)) }
    }

    private fun tarjetaInsumo(insumo: Insumo): View {
        val estado = estado(insumo)
        val card = tarjeta(estado)
        val cuerpo = columna(16, 13)
        cuerpo.addView(texto(insumo.name, 18f, R.color.white, true))

        val lineaStock = fila().apply { gravity = Gravity.CENTER_VERTICAL }
        lineaStock.addView(texto(cantidadLegible(insumo, insumo.stock), 19f, R.color.white, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        val (etiqueta, textoColor, fondoColor) = when (estado) {
            Estado.NORMAL -> Triple("Normal", R.color.insumos_verde, R.color.insumos_verde_fondo)
            Estado.BAJO -> Triple("Stock bajo", R.color.insumos_amarillo, R.color.insumos_amarillo_fondo)
            Estado.CRITICO -> Triple("Crítico", R.color.insumos_rojo, R.color.insumos_rojo_fondo)
        }
        lineaStock.addView(pastilla(etiqueta, textoColor, fondoColor))
        cuerpo.addView(lineaStock)
        cuerpo.addView(texto(rendimiento(insumo), 13f, R.color.insumos_texto_secundario))
        cuerpo.addView(texto(
            "Alerta: ${cantidadLegible(insumo, insumo.lowThreshold)}  ·  Pausa: < ${cantidadLegible(insumo, insumo.pauseThreshold)}",
            12f, R.color.insumos_texto_suave
        ))

        val divisor = View(requireContext()).apply { setBackgroundColor(color(R.color.insumos_borde)) }
        cuerpo.addView(divisor, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(11); bottomMargin = dp(9) })
        val acciones = fila().apply { gravity = Gravity.CENTER_VERTICAL }
        val entrada = MaterialButton(requireContext()).apply {
            text = if (estado == Estado.CRITICO) "+ Reponer" else "+ Entrada"
            isAllCaps = false
            textSize = 13f
            minHeight = dp(42)
            minimumWidth = 0
            cornerRadius = dp(13)
            if (estado == Estado.CRITICO) {
                backgroundTintList = ColorStateList.valueOf(color(R.color.insumos_rosa))
                setTextColor(color(R.color.white))
            } else {
                backgroundTintList = ColorStateList.valueOf(color(R.color.insumos_detalle))
                strokeColor = ColorStateList.valueOf(color(R.color.insumos_borde_verde))
                strokeWidth = dp(1)
                setTextColor(color(R.color.white))
            }
            setOnClickListener { dialogoIngreso(insumo) }
        }
        acciones.addView(entrada, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        val editar = MaterialButton(requireContext()).apply {
            text = "Ajustar / alertas"
            isAllCaps = false
            textSize = 12f
            minimumWidth = 0
            cornerRadius = dp(13)
            setTextColor(color(R.color.insumos_rosa_claro))
            backgroundTintList = ColorStateList.valueOf(color(R.color.insumos_detalle))
            strokeColor = ColorStateList.valueOf(color(R.color.insumos_borde))
            strokeWidth = dp(1)
            setOnClickListener { opcionesInsumo(insumo) }
        }
        acciones.addView(editar, LinearLayout.LayoutParams(0, dp(44), 1.25f))
        cuerpo.addView(acciones)
        card.addView(cuerpo)
        return card
    }

    private fun dibujarRecetas() {
        val root = vistaActiva ?: return
        val contenedor = root.findViewById<LinearLayout>(R.id.contenedorRecetas)
        contenedor.removeAllViews()
        if (recetas.isEmpty()) {
            contenedor.addView(tarjetaMensaje("Aún no hay recetas configuradas. Cada receta permitirá calcular porciones y pausar platos."))
            return
        }
        val porId = insumos.associateBy(Insumo::id)
        recetas.sortedBy { receta -> productos.firstOrNull { it.id == receta.productId }?.name.orEmpty() }.forEach { receta ->
            val producto = productos.firstOrNull { it.id == receta.productId }
            val resultado = if (producto == null) CalculadoraInsumos.evaluar(receta, porId)
                else CalculadoraInsumos.evaluarProducto(receta, producto, porId)
            val visible = resultado.disponible && producto?.isAvailable == true
            val card = tarjeta(if (visible) Estado.NORMAL else Estado.CRITICO)
            val cuerpo = columna(16, 14)
            val titulo = fila().apply { gravity = Gravity.CENTER_VERTICAL }
            titulo.addView(texto(producto?.name ?: "Producto eliminado", 17f, R.color.white, true),
                LinearLayout.LayoutParams(0, -2, 1f))
            titulo.addView(pastilla(if (visible) "Visible" else "Pausado",
                if (visible) R.color.insumos_verde else R.color.insumos_rojo,
                if (visible) R.color.insumos_verde_fondo else R.color.insumos_rojo_fondo))
            cuerpo.addView(titulo)
            cuerpo.addView(texto("${resultado.porciones} porciones disponibles", 17f,
                if (visible) R.color.insumos_verde else R.color.insumos_rojo, true))
            cuerpo.addView(texto("Límite: ${resultado.cuelloDeBotella}", 13f))
            if (resultado.faltantes.isNotEmpty()) cuerpo.addView(texto(
                "Revisar: ${resultado.faltantes.joinToString()}", 12f, R.color.insumos_rojo))
            val detalle = columna(11, 8).apply {
                background = fondoRedondeado(R.color.insumos_detalle, 12)
            }
            detalle.addView(texto("INSUMOS POR PORCIÓN", 11f, R.color.insumos_texto_secundario, true))
            detalle.addView(texto(receta.ingredients.joinToString("  ·  ") { linea ->
                val insumo = porId[linea.ingredientId]
                if (insumo == null) "Insumo faltante" else "${insumo.name}: ${cantidadLegible(insumo, linea.quantity)}"
            }.ifBlank { "Solo salsas configuradas" }, 13f, R.color.white))
            if (receta.sauceSlotsMl.isNotEmpty()) {
                detalle.addView(texto(receta.sauceSlotsMl.mapIndexed { indice, ml ->
                    "Salsa ${indice + 1}: ${formato(ml)} ml"
                }.joinToString("  ·  "), 12f, R.color.insumos_rosa_claro))
                detalle.addView(texto("${receta.sauceIngredients.size} variedades vinculadas al stock", 12f))
            }
            cuerpo.addView(detalle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
            val editar = MaterialButton(requireContext()).apply {
                text = "Editar receta"
                isAllCaps = false
                textSize = 13f
                setTextColor(color(R.color.insumos_rosa_claro))
                backgroundTintList = ColorStateList.valueOf(color(R.color.insumos_detalle))
                strokeColor = ColorStateList.valueOf(color(R.color.insumos_rosa))
                strokeWidth = dp(1)
                cornerRadius = dp(12)
                setOnClickListener { if (producto != null) dialogoReceta(producto) }
            }
            cuerpo.addView(editar, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(9) })
            card.addView(cuerpo)
            contenedor.addView(card)
        }
    }

    /** Muestra un formulario desplazable con acciones fijas, incluso con el teclado abierto. */
    private fun hoja(titulo: String, subtitulo: String, fraccionAltura: Float = 0.84f): Hoja {
        val dialogo = BottomSheetDialog(requireContext())
        val raiz = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = fondoRedondeado(R.color.insumos_tarjeta, 24)
        }
        val alto = (resources.displayMetrics.heightPixels * fraccionAltura).toInt()
        raiz.layoutParams = ViewGroup.LayoutParams(-1, alto)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { _, insets ->
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val barras = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            val disponible = (resources.displayMetrics.heightPixels - teclado - barras.top - barras.bottom - dp(12))
                .coerceAtLeast(dp(260))
            val altoDeseado = min(alto, disponible)
            if (raiz.layoutParams.height != altoDeseado) {
                raiz.layoutParams.height = altoDeseado
                raiz.requestLayout()
            }
            insets
        }
        val asa = View(requireContext()).apply { background = fondoRedondeado(R.color.insumos_borde, 8) }
        val asaFila = fila().apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(6)) }
        asaFila.addView(asa, LinearLayout.LayoutParams(dp(42), dp(4)))
        raiz.addView(asaFila)
        raiz.addView(texto(titulo, 22f, R.color.white, true).apply { setPadding(dp(20), dp(3), dp(20), 0) })
        raiz.addView(texto(subtitulo, 13f, R.color.insumos_texto_secundario).apply {
            setPadding(dp(20), dp(2), dp(20), dp(11))
        })
        val scroll = ScrollView(requireContext()).apply { clipToPadding = false }
        val cuerpo = columna(20, 12)
        scroll.addView(cuerpo)
        raiz.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val pie = fila().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(16))
        }
        raiz.addView(pie)
        dialogo.setContentView(raiz)
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return Hoja(dialogo, cuerpo, pie)
    }

    private fun mostrar(hoja: Hoja) {
        hoja.dialogo.show()
        hoja.dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        hoja.dialogo.behavior.skipCollapsed = true
    }

    private fun campo(contenedor: LinearLayout, etiqueta: String, valor: String = "", numerico: Boolean = true): Campo {
        val caja = TextInputLayout(requireContext()).apply {
            hint = etiqueta
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = color(R.color.insumos_rosa_claro)
            defaultHintTextColor = ColorStateList.valueOf(color(R.color.insumos_texto_secundario))
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        }
        val entrada = TextInputEditText(caja.context).apply {
            setText(valor)
            setTextColor(color(R.color.white))
            setSingleLine(true)
            textSize = 16f
            if (numerico) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        caja.addView(entrada)
        contenedor.addView(caja)
        return Campo(caja, entrada)
    }

    private fun botonHoja(texto: String, principal: Boolean = true, accion: (MaterialButton) -> Unit): MaterialButton =
        MaterialButton(requireContext()).apply {
            text = texto
            isAllCaps = false
            textSize = 15f
            setTextColor(color(if (principal) R.color.white else R.color.insumos_rosa_claro))
            backgroundTintList = ColorStateList.valueOf(color(if (principal) R.color.insumos_rosa else R.color.insumos_tarjeta))
            if (!principal) {
                strokeColor = ColorStateList.valueOf(color(R.color.insumos_borde)); strokeWidth = dp(1)
            }
            cornerRadius = dp(14)
            setOnClickListener { accion(this) }
        }

    private fun piePrincipal(hoja: Hoja, titulo: String, accion: (MaterialButton) -> Unit) {
        hoja.pie.addView(botonHoja(titulo, accion = accion), LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun guardar(boton: MaterialButton, hoja: Hoja, operacion: ((Result<Unit>) -> Unit) -> Unit) {
        boton.isEnabled = false
        val titulo = boton.text
        boton.text = "Guardando…"
        operacion { resultado ->
            resultado.onSuccess { hoja.dialogo.dismiss() }
                .onFailure { aviso(it.message ?: "No se pudieron guardar los cambios") }
            boton.isEnabled = true
            boton.text = titulo
        }
    }

    private fun dialogoNuevoInsumo() {
        val hoja = hoja("Nuevo insumo", "Registra la unidad de compra, el saldo inicial y las alertas.")
        val nombre = campo(hoja.cuerpo, "Nombre del insumo", numerico = false)
        hoja.cuerpo.addView(texto("Se compra en", 13f, R.color.insumos_texto_secundario, true))
        val tipos = listOf("u", "g", "ml")
        val etiquetas = listOf("Unid.", "kg", "ml")
        val grupo = MaterialButtonToggleGroup(requireContext()).apply { isSingleSelection = true; isSelectionRequired = true }
        val ids = etiquetas.map { etiqueta ->
            val idNuevo = View.generateViewId()
            grupo.addView(MaterialButton(requireContext()).apply {
                id = idNuevo; text = etiqueta; isAllCaps = false; textSize = 12f
                isCheckable = true
                setTextColor(color(R.color.white))
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.insumos_filtro_fondo)
                strokeColor = ColorStateList.valueOf(color(R.color.insumos_borde))
                strokeWidth = dp(1)
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            idNuevo
        }
        hoja.cuerpo.addView(grupo, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(15) })
        grupo.check(ids[0])
        val compra = campo(hoja.cuerpo, "Cantidad inicial (unidades)")
        val amarillo = campo(hoja.cuerpo, "Alerta amarilla (unidades)")
        val rojo = campo(hoja.cuerpo, "Pausa roja por debajo de (unidades)")
        grupo.addOnButtonCheckedListener { _, id, marcado ->
            if (marcado) {
                val unidad = when (ids.indexOf(id)) { 1 -> "kg"; 2 -> "ml"; else -> "unidades" }
                compra.contenedor.hint = "Cantidad inicial ($unidad)"
                amarillo.contenedor.hint = "Alerta amarilla ($unidad)"
                rojo.contenedor.hint = "Pausa roja por debajo de ($unidad)"
            }
        }
        hoja.cuerpo.addView(nota("El umbral rojo pausa los platos vinculados; el amarillo avisa antes de agotarse."))
        piePrincipal(hoja, "Guardar insumo") { boton ->
            val tipo = tipos[ids.indexOf(grupo.checkedButtonId).coerceAtLeast(0)]
            val factor = if (tipo == "g") 1000.0 else 1.0
            val n = nombre.entrada.text.toString().trim()
            val cantidad = numero(compra.entrada)
            val bajo = numero(amarillo.entrada)
            val pausa = numero(rojo.entrada)
            if (n.isBlank() || cantidad == null || bajo == null || pausa == null ||
                cantidad < 0.0 || bajo < pausa || pausa < 0.0 ||
                (tipo == "u" && listOf(cantidad, bajo, pausa).any { it % 1.0 != 0.0 })) {
                aviso("Revisa nombre, cantidades y umbrales. El amarillo debe ser al menos igual al rojo.")
            } else guardar(boton, hoja) { fin ->
                RepositorioInsumos.guardarInsumo(Insumo(name = n, unit = tipo,
                    stock = cantidad * factor, lowThreshold = bajo * factor,
                    pauseThreshold = pausa * factor), fin)
            }
        }
        mostrar(hoja)
    }

    private fun elegirInsumoParaEntrada() {
        if (insumos.isEmpty()) { dialogoNuevoInsumo(); return }
        val hoja = hoja("Registrar entrada", "Elige qué insumo acabas de recibir.", 0.72f)
        insumos.forEach { insumo ->
            hoja.cuerpo.addView(filaSeleccion(insumo.name,
                "Saldo: ${cantidadLegible(insumo, insumo.stock)}") {
                hoja.dialogo.dismiss(); dialogoIngreso(insumo)
            })
        }
        piePrincipal(hoja, "+ Crear insumo nuevo") {
            hoja.dialogo.dismiss(); dialogoNuevoInsumo()
        }
        mostrar(hoja)
    }

    private fun dialogoIngreso(insumo: Insumo) {
        val hoja = hoja("Entrada de ${insumo.name}", "Suma una compra al saldo registrado.", 0.65f)
        hoja.cuerpo.addView(resumenSaldo("Saldo actual", cantidadLegible(insumo, insumo.stock)))
        val cantidad = campo(hoja.cuerpo, "Cantidad recibida (${insumo.unidadCompra})")
        hoja.cuerpo.addView(nota("Se añadirá al saldo actual. El estado de los platos se recalculará al guardar."))
        piePrincipal(hoja, "Registrar entrada") { boton ->
            val valor = numero(cantidad.entrada)
            if (valor == null || valor <= 0.0 || (insumo.unit == "u" && valor % 1.0 != 0.0)) {
                aviso("Ingresa una cantidad mayor que cero.")
            } else guardar(boton, hoja) { fin ->
                RepositorioInsumos.registrarIngreso(insumo.id, valor * insumo.factorCompra, fin)
            }
        }
        mostrar(hoja)
    }

    private fun opcionesInsumo(insumo: Insumo) {
        val hoja = hoja("Editar ${insumo.name}", "Selecciona qué dato deseas actualizar.", 0.48f)
        hoja.cuerpo.addView(filaSeleccion("Ajustar saldo físico",
            "Corrige el saldo después del conteo o una merma.") {
            hoja.dialogo.dismiss(); dialogoAjuste(insumo)
        })
        hoja.cuerpo.addView(filaSeleccion("Configurar alertas",
            "Modifica los niveles amarillo y rojo.") {
            hoja.dialogo.dismiss(); dialogoUmbrales(insumo)
        })
        mostrar(hoja)
    }

    private fun dialogoAjuste(insumo: Insumo) {
        val hoja = hoja("Ajustar ${insumo.name}", "Introduce el saldo físico real.", 0.65f)
        hoja.cuerpo.addView(resumenSaldo("Saldo registrado", cantidadLegible(insumo, insumo.stock)))
        val saldo = campo(hoja.cuerpo, "Nuevo saldo (${insumo.unidadCompra})",
            formato(insumo.stock / insumo.factorCompra))
        hoja.cuerpo.addView(nota("Este valor reemplaza el saldo anterior; úsalo tras contar el almacén."))
        piePrincipal(hoja, "Guardar ajuste") { boton ->
            val valor = numero(saldo.entrada)
            if (valor == null || valor < 0.0 || (insumo.unit == "u" && valor % 1.0 != 0.0)) {
                aviso("Ingresa un saldo válido.")
            } else guardar(boton, hoja) { fin ->
                RepositorioInsumos.ajustarStock(insumo.id, valor * insumo.factorCompra, fin)
            }
        }
        mostrar(hoja)
    }

    private fun dialogoUmbrales(insumo: Insumo) {
        val hoja = hoja("Alertas de ${insumo.name}", "Define cuándo avisar y cuándo pausar platos.", 0.72f)
        hoja.cuerpo.addView(resumenSaldo("Saldo actual", cantidadLegible(insumo, insumo.stock)))
        val amarillo = campo(hoja.cuerpo, "Stock bajo (${insumo.unidadCompra})",
            formato(insumo.lowThreshold / insumo.factorCompra))
        val rojo = campo(hoja.cuerpo, "Auto-pausa por debajo de (${insumo.unidadCompra})",
            formato(insumo.pauseThreshold / insumo.factorCompra))
        hoja.cuerpo.addView(nota("El umbral amarillo debe ser igual o superior al rojo."))
        piePrincipal(hoja, "Guardar alertas") { boton ->
            val bajo = numero(amarillo.entrada)
            val pausa = numero(rojo.entrada)
            if (bajo == null || pausa == null || bajo < pausa || pausa < 0.0 ||
                (insumo.unit == "u" && (bajo % 1.0 != 0.0 || pausa % 1.0 != 0.0))) {
                aviso("Revisa los umbrales: amarillo debe ser igual o mayor que rojo.")
            } else guardar(boton, hoja) { fin ->
                RepositorioInsumos.actualizarUmbrales(insumo.id,
                    bajo * insumo.factorCompra, pausa * insumo.factorCompra, fin)
            }
        }
        mostrar(hoja)
    }

    private fun elegirProducto() {
        if (productos.isEmpty()) { aviso("Primero registra productos en la carta."); return }
        val hoja = hoja("Configurar receta", "Elige el plato para definir sus insumos.", 0.76f)
        productos.forEach { producto ->
            val configurada = recetas.any { it.productId == producto.id }
            hoja.cuerpo.addView(filaSeleccion(producto.name,
                if (configurada) "Receta configurada · Toca para editar" else "Sin receta · Toca para configurar") {
                hoja.dialogo.dismiss(); dialogoReceta(producto)
            })
        }
        mostrar(hoja)
    }

    private fun dialogoReceta(producto: Product) {
        if (insumos.isEmpty()) { aviso("Primero registra los insumos."); return }
        val actual = recetas.firstOrNull { it.productId == producto.id }
        val numeroSalsas = cantidadSalsasConfigurables(producto)
        val admiteSalsas = numeroSalsas > 0
        val hoja = hoja("Receta: ${producto.name}", "Cantidad consumida por una porción de este plato.", 0.9f)
        hoja.cuerpo.addView(texto("Insumos fijos", 18f, R.color.white, true))
        hoja.cuerpo.addView(nota("Escribe 0 para excluirlos. Las salsas elegidas se configuran debajo."))
        val opcionesSalsa = if (admiteSalsas) opcionesSalsa(producto) else emptyList()
        val insumosFijos = if (admiteSalsas) insumos.filterNot { esInsumoDeSalsa(it, opcionesSalsa) }
            else insumos
        val campos = insumosFijos.map { insumo ->
            val card = tarjeta(Estado.NORMAL)
            val contenido = columna(13, 9)
            contenido.addView(texto(insumo.name, 15f, R.color.white, true))
            contenido.addView(texto("Stock: ${cantidadLegible(insumo, insumo.stock)} · Receta en ${unidadBase(insumo)}", 12f))
            val previa = actual?.ingredients?.firstOrNull { it.ingredientId == insumo.id }?.quantity ?: 0.0
            val cantidad = campo(contenido, "Cantidad por porción (${unidadBase(insumo)})", formato(previa))
            card.addView(contenido)
            hoja.cuerpo.addView(card)
            insumo to cantidad.entrada
        }
        val insumosLiquidos = insumos.filter { it.unit == "ml" }
        val camposSalsa = mutableListOf<Campo>()
        val vinculaciones = mutableListOf<Pair<String, MaterialAutoCompleteTextView>>()
        if (admiteSalsas) {
            hoja.cuerpo.addView(texto("Salsas a elección del cliente", 18f, R.color.white, true))
            hoja.cuerpo.addView(nota("Aparece un campo por cada salsa que admite este producto. Cada elección consume 25 ml por defecto; las adicionales usan el volumen del último campo."))
            repeat(numeroSalsas) { indice ->
                camposSalsa += campo(hoja.cuerpo, "Salsa ${indice + 1} (ml)",
                    formato(actual?.sauceSlotsMl?.getOrNull(indice) ?: 25.0))
            }
            hoja.cuerpo.addView(texto("Vincular variedades con el almacén", 15f, R.color.white, true))
            hoja.cuerpo.addView(nota("Las variedades sin insumo vinculado no se ofrecerán en la carta hasta configurarlas."))
            if (insumosLiquidos.isEmpty()) hoja.cuerpo.addView(nota("Registra primero las salsas como insumos en ml."))
            opcionesSalsa.forEach { nombre ->
                val idPrevio = actual?.sauceIngredients?.get(nombre)
                val previo = insumosLiquidos.firstOrNull { it.id == idPrevio }
                    ?: insumosLiquidos.firstOrNull { esCorrespondenciaSalsa(nombre, it.name) }
                vinculaciones += nombre to selectorSalsa(hoja.cuerpo, nombre, insumosLiquidos, previo)
            }
        }
        if (actual != null) {
            hoja.pie.addView(botonHoja("Quitar", principal = false) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Quitar receta")
                    .setMessage("El producto volverá a usar solo su disponibilidad manual.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Quitar") { _, _ ->
                        RepositorioInsumos.eliminarReceta(producto.id) { r ->
                            r.onSuccess { hoja.dialogo.dismiss() }
                                .onFailure { aviso(it.message ?: "No se pudo quitar la receta") }
                        }
                    }.show()
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        }
        hoja.pie.addView(botonHoja("Guardar receta") { boton ->
            val cantidades = campos.map { (insumo, entrada) -> insumo to numero(entrada) }
            if (cantidades.any { it.second == null || it.second!! < 0.0 }) {
                aviso("Todas las cantidades deben ser válidas y no negativas.")
                return@botonHoja
            }
            val lineas = cantidades.mapNotNull { (insumo, valor) ->
                if (valor != null && valor > 0.0) IngredienteReceta(insumo.id, valor) else null
            }
            val volumenes = camposSalsa.map { numero(it.entrada) }
            if (admiteSalsas && volumenes.any { it == null || it <= 0.0 }) {
                aviso("Cada salsa configurada debe tener una cantidad de ml mayor que cero.")
                return@botonHoja
            }
            val mapeo = vinculaciones.mapNotNull { (nombre, selector) ->
                insumosLiquidos.firstOrNull { it.name == selector.text.toString() }?.let { nombre to it.id }
            }.toMap()
            if (lineas.isEmpty() && mapeo.isEmpty()) {
                aviso("Agrega insumos fijos o vincula al menos una salsa.")
            } else if (admiteSalsas && mapeo.isEmpty()) {
                aviso("Vincula al menos una salsa con un insumo en ml.")
            } else guardar(boton, hoja) { fin ->
                RepositorioInsumos.guardarReceta(RecetaProducto(producto.id, lineas,
                    volumenes.filterNotNull(), mapeo), fin, producto)
            }
        }, LinearLayout.LayoutParams(0, dp(52), if (actual == null) 1f else 1.45f))
        mostrar(hoja)
    }

    private fun selectorSalsa(contenedor: LinearLayout, nombre: String, disponibles: List<Insumo>,
                             seleccionado: Insumo?): MaterialAutoCompleteTextView {
        val caja = TextInputLayout(requireContext()).apply {
            hint = nombre
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = color(R.color.insumos_rosa_claro)
            defaultHintTextColor = ColorStateList.valueOf(color(R.color.insumos_texto_secundario))
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        val opciones = listOf("Sin vincular") + disponibles.map(Insumo::name)
        val entrada = MaterialAutoCompleteTextView(caja.context).apply {
            inputType = InputType.TYPE_NULL
            setTextColor(color(R.color.white))
            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opciones))
            setText(seleccionado?.name ?: "Sin vincular", false)
        }
        caja.addView(entrada)
        contenedor.addView(caja)
        return entrada
    }

    private fun opcionesSalsa(producto: Product): List<String> =
        producto.availableSauces.ifEmpty { Product.DEFAULT_YALAZA_SAUCES }

    /** Dos campos solo para La Previa o combos con salsas; el resto sigue su propia configuración. */
    private fun cantidadSalsasConfigurables(producto: Product): Int {
        val esPrevia = Regex("\\bprevia\\b", RegexOption.IGNORE_CASE).containsMatchIn(producto.name)
        val esCombo = producto.name.contains("combo", ignoreCase = true) ||
            producto.category.contains("combo", ignoreCase = true)
        return when {
            esPrevia -> 2
            esCombo && producto.allowsSauces -> maxOf(2, producto.maxSauces).coerceAtMost(8)
            producto.allowsSauces && producto.maxSauces > 0 -> producto.maxSauces.coerceAtMost(8)
            else -> 0
        }
    }

    private fun esInsumoDeSalsa(insumo: Insumo, opciones: List<String>): Boolean =
        insumo.unit == "ml" && (insumo.name.trim().startsWith("Salsa", ignoreCase = true) ||
            opciones.any { esCorrespondenciaSalsa(it, insumo.name) })

    private fun esCorrespondenciaSalsa(opcion: String, insumo: String): Boolean {
        fun normalizar(valor: String) = Normalizer.normalize(valor.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\b(salsa|clasica|especial)\\b"), "")
            .trim().replace(Regex("\\s+"), " ")
        val a = normalizar(opcion)
        val b = normalizar(insumo)
        return a.isNotBlank() && b.isNotBlank() && (a == b || a.contains(b) || b.contains(a))
    }

    private fun confirmarEjemploBbq() {
        val producto = productos.firstOrNull { it.name.trim().equals("Alitas BBQ", ignoreCase = true) }
        if (producto == null) { aviso("No existe un producto llamado Alitas BBQ."); return }
        if (recetas.any { it.productId == producto.id }) { dialogoReceta(producto); return }
        val mensaje = if (cantidadSalsasConfigurables(producto) > 0)
            "Se configurarán 6 alitas y 200 g de papa como base. Cada salsa elegida consumirá 25 ml. La salsa BBQ se vinculará al insumo Salsa BBQ; las demás variedades podrán vincularse al editar la receta."
        else
            "Se configurarán 6 alitas, 200 g de papa y 50 ml de salsa BBQ como insumos fijos. Este producto no está configurado para elegir varias salsas."
        MaterialAlertDialogBuilder(requireContext()).setTitle("Preparar Alitas BBQ")
            .setMessage(mensaje)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Preparar") { _, _ -> prepararEjemploBbq(producto) }.show()
    }

    private fun prepararEjemploBbq(producto: Product) {
        val ejemplos = listOf(
            Insumo("alitas", "Alitas", "u", 0.0, 24.0, 6.0),
            Insumo("papa", "Papa", "g", 0.0, 3000.0, 200.0),
            Insumo("salsa_bbq", "Salsa BBQ", "ml", 0.0, 1000.0, 200.0)
        )
        val actuales = insumos.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val seleccion = ejemplos.map { ejemplo ->
            actuales[ejemplo.name.lowercase(Locale.ROOT)] ?: ejemplo
        }
        val numeroSalsas = cantidadSalsasConfigurables(producto)
        val opcionBbq = opcionesSalsa(producto).firstOrNull { it.contains("BBQ", ignoreCase = true) }
        if (numeroSalsas > 0 && opcionBbq == null) {
            aviso("Añade BBQ a las salsas disponibles del producto antes de preparar la receta.")
            return
        }
        val batch = db.batch()
        seleccion.filter { actual -> insumos.none { it.id == actual.id } }.forEach { nuevo ->
            batch.set(db.collection("ingredients").document(nuevo.id), nuevo)
        }
        val fijos = mutableListOf(
            IngredienteReceta(seleccion[0].id, 6.0),
            IngredienteReceta(seleccion[1].id, 200.0)
        )
        if (numeroSalsas == 0) fijos += IngredienteReceta(seleccion[2].id, 50.0)
        batch.set(db.collection("recipes").document(producto.id), RecetaProducto(
            producto.id, fijos,
            sauceSlotsMl = if (numeroSalsas > 0) List(numeroSalsas) { 25.0 } else emptyList(),
            sauceIngredients = if (numeroSalsas > 0 && opcionBbq != null)
                mapOf(opcionBbq to seleccion[2].id) else emptyMap()
        ))
        if (numeroSalsas > 0 && (!producto.allowsSauces ||
                producto.maxSauces < numeroSalsas || producto.availableSauces.isEmpty())) {
            batch.update(db.collection("products").document(producto.id), mapOf(
                "allowsSauces" to true,
                "maxSauces" to maxOf(producto.maxSauces, numeroSalsas),
                "availableSauces" to opcionesSalsa(producto)
            ))
        }
        batch.commit().addOnSuccessListener { if (isAdded) aviso("Receta creada. Registra el stock para publicar el plato.") }
            .addOnFailureListener { if (isAdded) mostrarError(it) }
    }

    private fun rendimiento(insumo: Insumo): String {
        val usos = recetas.mapNotNull { receta ->
            val fija = receta.ingredients.firstOrNull { it.ingredientId == insumo.id }?.quantity
            val salsa = if (insumo.id in receta.sauceIngredients.values) receta.sauceSlotsMl.firstOrNull() else null
            val cantidad = fija ?: salsa ?: return@mapNotNull null
            if (cantidad <= 0.0) return@mapNotNull null
            val nombre = productos.firstOrNull { it.id == receta.productId }?.name ?: "una receta"
            nombre to floor(insumo.stock.coerceAtLeast(0.0) / cantidad).toInt()
        }
        return when (usos.size) {
            0 -> "Rendimiento: sin receta vinculada"
            1 -> "Rendimiento: ~${usos[0].second} porciones de ${usos[0].first}"
            else -> "Rendimiento por receta: ${usos.minOf { it.second }}–${usos.maxOf { it.second }} porciones · ${usos.size} platos"
        }
    }

    private fun estado(insumo: Insumo): Estado = when {
        insumo.stock <= 0.0 || insumo.stock < insumo.pauseThreshold -> Estado.CRITICO
        insumo.stock <= insumo.lowThreshold -> Estado.BAJO
        else -> Estado.NORMAL
    }

    private fun tarjeta(estado: Estado): MaterialCardView = MaterialCardView(requireContext()).apply {
        radius = dp(17).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(color(when (estado) {
            Estado.NORMAL -> R.color.insumos_tarjeta
            Estado.BAJO -> R.color.insumos_kpi_bajo
            Estado.CRITICO -> R.color.insumos_kpi_critico
        }))
        strokeColor = color(when (estado) {
            Estado.NORMAL -> R.color.insumos_borde_verde
            Estado.BAJO -> R.color.insumos_borde_amarillo
            Estado.CRITICO -> R.color.insumos_borde_rojo
        })
        strokeWidth = dp(1)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) }
    }

    private fun tarjetaMensaje(mensaje: String): View = tarjeta(Estado.NORMAL).apply {
        addView(texto(mensaje, 14f).apply { setPadding(dp(18), dp(18), dp(18), dp(18)) })
    }

    private fun filaSeleccion(titulo: String, subtitulo: String, accion: () -> Unit): View {
        val card = tarjeta(Estado.NORMAL)
        val contenido = columna(14, 10)
        contenido.addView(texto(titulo, 16f, R.color.white, true))
        contenido.addView(texto(subtitulo, 12f))
        card.addView(contenido)
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { accion() }
        return card
    }

    private fun resumenSaldo(etiqueta: String, valor: String): View = tarjeta(Estado.NORMAL).apply {
        val contenido = columna(14, 10)
        contenido.addView(texto(etiqueta, 12f))
        contenido.addView(texto(valor, 22f, R.color.white, true))
        addView(contenido)
    }

    private fun nota(mensaje: String) = texto(mensaje, 12f, R.color.insumos_texto_suave).apply {
        setPadding(0, dp(3), 0, dp(13))
    }

    private fun pastilla(valor: String, textoColor: Int, fondoColor: Int) = texto(valor, 12f, textoColor, true).apply {
        setPadding(dp(9), dp(3), dp(9), dp(3))
        background = fondoRedondeado(fondoColor, 16)
    }

    private fun fondoRedondeado(colorId: Int, radioDp: Int) = GradientDrawable().apply {
        setColor(color(colorId))
        cornerRadius = dp(radioDp).toFloat()
    }

    private fun columna(lado: Int = 0, vertical: Int = 0) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(lado), dp(vertical), dp(lado), dp(vertical))
    }

    private fun fila() = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }

    private fun texto(valor: String, size: Float = 13f, colorId: Int = R.color.insumos_texto_secundario,
                      negrita: Boolean = false) = TextView(requireContext()).apply {
        text = valor; textSize = size; setTextColor(color(colorId))
        if (negrita) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun cantidadLegible(insumo: Insumo, cantidadBase: Double): String = when (insumo.unit) {
        "g" -> if (cantidadBase < 1000.0) "${formato(cantidadBase)} g" else "${formato(cantidadBase / 1000.0)} kg"
        "ml" -> "${formato(cantidadBase)} ml"
        else -> "${formato(cantidadBase)} unidades"
    }

    private fun unidadBase(insumo: Insumo): String = when (insumo.unit) { "g" -> "g"; "ml" -> "ml"; else -> "unidades" }
    private fun numero(campo: TextInputEditText): Double? = campo.text.toString().trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun formato(cantidad: Double): String = BigDecimal.valueOf(cantidad)
        .setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()
    private fun aviso(mensaje: String) { if (isAdded) Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show() }
    private fun mostrarError(error: Exception) = aviso("No se pudieron cargar los insumos: ${error.message}")

    override fun onDestroyView() {
        registros.forEach(ListenerRegistration::remove)
        registros.clear()
        vistaActiva = null
        super.onDestroyView()
    }
}
