package com.yalazas.app.model

import kotlin.math.floor

/** Las cantidades se guardan en unidades, gramos o mililitros. */
data class Insumo(
    var id: String = "",
    var name: String = "",
    var unit: String = "u",
    var stock: Double = 0.0,
    var lowThreshold: Double = 0.0,
    var pauseThreshold: Double = 0.0
) {
    // Solo los gramos se convierten al registrar compras en kg; los líquidos permanecen en ml.
    val factorCompra: Double get() = if (unit == "g") 1000.0 else 1.0
    val unidadCompra: String get() = when (unit) { "g" -> "kg"; "ml" -> "ml"; else -> "unid." }
}

data class IngredienteReceta(var ingredientId: String = "", var quantity: Double = 0.0)

/** Documento `recipes/{productId}`; una receta vacía nunca habilita el producto. */
data class RecetaProducto(
    var productId: String = "",
    var ingredients: List<IngredienteReceta> = emptyList(),
    /** Volumen de cada salsa permitida; las extras usan el volumen del último campo. */
    var sauceSlotsMl: List<Double> = emptyList(),
    /** Nombre que ve el cliente -> insumo líquido que consume esa elección. */
    var sauceIngredients: Map<String, String> = emptyMap()
)

data class DisponibilidadReceta(
    val porciones: Int,
    val disponible: Boolean,
    val cuelloDeBotella: String,
    val faltantes: List<String>
)

/** Calcula porciones físicas y aplica el umbral rojo sin modificar el producto. */
object CalculadoraInsumos {
    fun evaluar(receta: RecetaProducto, insumos: Map<String, Insumo>): DisponibilidadReceta {
        if (receta.ingredients.isEmpty()) return DisponibilidadReceta(0, false, "Sin ingredientes", listOf("Receta vacía"))
        val capacidades = receta.ingredients.map { linea ->
            val insumo = insumos[linea.ingredientId]
            val capacidad = if (insumo == null || linea.quantity <= 0.0 || !linea.quantity.isFinite()) 0
                else floor(insumo.stock.coerceAtLeast(0.0) / linea.quantity).toInt().coerceAtLeast(0)
            Triple(linea, insumo, capacidad)
        }
        val minimo = capacidades.minOf { it.third }
        val cuello = capacidades.first { it.third == minimo }.second?.name ?: "Insumo faltante"
        val faltantes = capacidades.filter { (_, insumo, capacidad) ->
            insumo == null || capacidad < 1 || insumo.stock < insumo.pauseThreshold
        }.map { it.second?.name ?: "Insumo faltante" }
        return DisponibilidadReceta(minimo, faltantes.isEmpty(), cuello, faltantes)
    }

    /** Opciones cuya salsa elegida tiene volumen suficiente y no está en pausa roja. */
    fun salsasDisponibles(receta: RecetaProducto, producto: Product, insumos: Map<String, Insumo>): List<String> {
        if (receta.sauceSlotsMl.isEmpty()) return producto.availableSauces.ifEmpty { Product.DEFAULT_YALAZA_SAUCES }
        if (!producto.allowsSauces) return emptyList()
        val opciones = producto.availableSauces.ifEmpty { Product.DEFAULT_YALAZA_SAUCES }
        val cantidad = receta.sauceSlotsMl.firstOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return emptyList()
        return opciones.filter { nombre ->
            val insumo = receta.sauceIngredients[nombre]?.let(insumos::get)
            insumo?.unit == "ml" && insumo.stock >= cantidad && insumo.stock >= insumo.pauseThreshold
        }
    }

    /** Combina insumos fijos y salsas elegibles cuando el producto admite esa opción. */
    fun evaluarProducto(receta: RecetaProducto, producto: Product, insumos: Map<String, Insumo>): DisponibilidadReceta {
        val base = if (receta.ingredients.isEmpty() && receta.sauceSlotsMl.isNotEmpty())
            DisponibilidadReceta(Int.MAX_VALUE, true, "Salsas", emptyList()) else evaluar(receta, insumos)
        if (receta.sauceSlotsMl.isEmpty()) return base
        val opciones = salsasDisponibles(receta, producto, insumos)
        if (opciones.isEmpty()) return DisponibilidadReceta(0, false, "Salsas disponibles", base.faltantes + "Salsas disponibles")
        val cantidad = receta.sauceSlotsMl.first()
        val mejor = opciones.mapNotNull { nombre -> receta.sauceIngredients[nombre]?.let(insumos::get) }
            .maxByOrNull { it.stock / cantidad } ?: return DisponibilidadReceta(0, false, "Salsas disponibles", base.faltantes + "Salsas disponibles")
        val porcionesSalsa = floor(mejor.stock / cantidad).toInt().coerceAtLeast(0)
        val porciones = minOf(base.porciones, porcionesSalsa)
        return DisponibilidadReceta(porciones, base.disponible && porciones > 0,
            if (porcionesSalsa < base.porciones) mejor.name else base.cuelloDeBotella, base.faltantes)
    }

    /** Demanda de una línea de pedido con las salsas reales elegidas por el cliente. */
    fun demandaArticulo(receta: RecetaProducto, producto: Product, salsas: List<String>): List<IngredienteReceta>? {
        if (receta.sauceSlotsMl.isEmpty()) return receta.ingredients
        if (salsas.isEmpty() || salsas.size != salsas.distinct().size) return null
        val permitidas = producto.availableSauces.ifEmpty { Product.DEFAULT_YALAZA_SAUCES }
        if (salsas.any { it !in permitidas }) return null
        val uso = receta.ingredients.toMutableList()
        salsas.forEachIndexed { indice, nombre ->
            val id = receta.sauceIngredients[nombre] ?: return null
            val ml = receta.sauceSlotsMl.getOrElse(indice) { receta.sauceSlotsMl.last() }
            if (!ml.isFinite() || ml <= 0.0) return null
            uso += IngredienteReceta(id, ml)
        }
        return uso
    }
}
