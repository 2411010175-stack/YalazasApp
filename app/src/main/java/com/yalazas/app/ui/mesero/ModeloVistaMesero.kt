package com.yalazas.app.ui.mesero

import androidx.lifecycle.ViewModel
import com.yalazas.app.model.ArticuloCarrito

/** Conserva la comanda en edición cuando Android recrea la pantalla, por ejemplo al girarla. */
class ModeloVistaMesero : ViewModel() {
    val borrador = mutableListOf<ArticuloCarrito>()
}
