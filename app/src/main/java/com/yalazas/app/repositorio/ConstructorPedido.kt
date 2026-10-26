package com.yalazas.app.repositorio

import com.yalazas.app.model.ArticuloCarrito
import com.yalazas.app.model.UserProfile

/** Arma una solicitud de pedido por etapas antes de enviarla a Firestore. */
class ConstructorPedido private constructor(private val perfil: UserProfile) {
    private val articulos = mutableListOf<ArticuloCarrito>()
    private var entrega: DatosEntrega? = null
    private var pago: DatosPago? = null
    private var comentario: String = ""

    /** Agrega una variante de producto sin depender de cambios posteriores en el carrito. */
    fun agregarArticulo(articulo: ArticuloCarrito) = apply {
        articulos.add(copiarArticulo(articulo))
    }

    /** Permite construir pedidos con tantos productos como haya seleccionado el cliente. */
    fun agregarArticulos(nuevosArticulos: List<ArticuloCarrito>) = apply {
        nuevosArticulos.forEach { agregarArticulo(it) }
    }

    /** Configura entrega a domicilio o retiro en el local. */
    fun configurarEntrega(
        direccion: String,
        referencia: String,
        latitud: Double,
        longitud: Double,
        esDelivery: Boolean,
        distanciaKm: Double,
        tarifa: Double
    ) = apply {
        entrega = DatosEntrega(direccion, referencia, latitud, longitud, esDelivery, distanciaKm, tarifa)
    }

    fun agregarComentario(nuevoComentario: String) = apply {
        comentario = nuevoComentario.trim()
    }

    /** En pagos digitales se usa el total calculado; en efectivo se conserva el monto entregado. */
    fun configurarPago(metodo: String, montoEfectivo: Double = 0.0) = apply {
        pago = DatosPago(metodo, montoEfectivo)
    }

    /** Calcula el total desde los artículos y la tarifa, y entrega una solicitud independiente. */
    fun construir(): SolicitudPedido {
        require(articulos.isNotEmpty()) { "El pedido debe contener al menos un producto." }
        val datosEntrega = requireNotNull(entrega) { "Configura la entrega o el retiro del pedido." }
        val datosPago = requireNotNull(pago) { "Selecciona un método de pago." }
        val items = articulos.map(::copiarArticulo)
        val total = items.sumOf { it.subtotal } + datosEntrega.tarifa

        return SolicitudPedido(
            items = items,
            profile = perfil,
            address = datosEntrega.direccion,
            reference = datosEntrega.referencia,
            latitude = datosEntrega.latitud,
            longitude = datosEntrega.longitud,
            comment = comentario,
            totalAmount = total,
            paymentMethod = datosPago.metodo,
            paymentAmount = if (datosPago.metodo == "EFECTIVO") datosPago.montoEfectivo else total,
            isDelivery = datosEntrega.esDelivery,
            deliveryDistanceKm = datosEntrega.distanciaKm,
            deliveryFee = datosEntrega.tarifa
        )
    }

    private fun copiarArticulo(articulo: ArticuloCarrito) = articulo.copy(
        product = articulo.product.copy(availableSauces = articulo.product.availableSauces.toList()),
        selectedSauces = articulo.selectedSauces.toList()
    )

    private data class DatosEntrega(
        val direccion: String,
        val referencia: String,
        val latitud: Double,
        val longitud: Double,
        val esDelivery: Boolean,
        val distanciaKm: Double,
        val tarifa: Double
    )

    private data class DatosPago(val metodo: String, val montoEfectivo: Double)

    companion object {
        fun paraCliente(perfil: UserProfile) = ConstructorPedido(perfil)
    }
}
