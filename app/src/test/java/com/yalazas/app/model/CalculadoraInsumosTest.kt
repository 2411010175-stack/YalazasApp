package com.yalazas.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadoraInsumosTest {
    private val receta = RecetaProducto("bbq", listOf(
        IngredienteReceta("alitas", 6.0),
        IngredienteReceta("papa", 200.0),
        IngredienteReceta("bbq", 50.0)
    ))

    @Test fun porcionesLimitadasPorCuelloDeBotella() {
        val resultado = CalculadoraInsumos.evaluar(receta, mapOf(
            "alitas" to Insumo("alitas", "Alitas", "u", 30.0, 24.0, 6.0),
            "papa" to Insumo("papa", "Papa", "g", 800.0, 3000.0, 200.0),
            "bbq" to Insumo("bbq", "Salsa BBQ", "ml", 500.0, 1000.0, 200.0)
        ))
        assertEquals(4, resultado.porciones)
        assertEquals("Papa", resultado.cuelloDeBotella)
        assertTrue(resultado.disponible)
    }

    @Test fun umbralRojoPausaAunqueQuedenPorcionesFisicas() {
        val resultado = CalculadoraInsumos.evaluar(receta, mapOf(
            "alitas" to Insumo("alitas", "Alitas", "u", 30.0, 24.0, 6.0),
            "papa" to Insumo("papa", "Papa", "g", 800.0, 3000.0, 200.0),
            "bbq" to Insumo("bbq", "Salsa BBQ", "ml", 199.0, 1000.0, 200.0)
        ))
        assertEquals(3, resultado.porciones)
        assertFalse(resultado.disponible)
        assertEquals(listOf("Salsa BBQ"), resultado.faltantes)
    }

    @Test fun faltaDeInsumoPausaPlato() {
        assertFalse(CalculadoraInsumos.evaluar(receta, emptyMap()).disponible)
    }

    @Test fun productoSinRecetaNoSePublica() {
        val producto = Product(id = "bbq", name = "Alitas BBQ")
        assertFalse(CalculadoraInsumos.evaluarProducto(null, producto, emptyMap()).disponible)
    }

    @Test fun recetaCompletaConStockMantieneVisibleElPlato() {
        val producto = Product(id = "bbq", name = "Alitas BBQ")
        val insumos = mapOf(
            "alitas" to Insumo("alitas", "Alitas", "u", 30.0),
            "papa" to Insumo("papa", "Papa", "g", 800.0),
            "bbq" to Insumo("bbq", "Salsa BBQ", "ml", 500.0)
        )
        assertTrue(CalculadoraInsumos.evaluarProducto(receta, producto, insumos).disponible)
    }

    @Test fun recetaIncompletaNoSePublicaAunqueHayaStock() {
        val producto = Product(id = "bbq", name = "Alitas BBQ")
        val insumos = mapOf("alitas" to Insumo("alitas", "Alitas", "u", 30.0))
        val incompleta = RecetaProducto("bbq", listOf(
            IngredienteReceta("alitas", 6.0), IngredienteReceta("papa", 200.0)))
        assertFalse(CalculadoraInsumos.evaluarProducto(incompleta, producto, insumos).disponible)
    }

    @Test fun salsasAnunciadasExigenConsumoConfigurado() {
        val producto = Product(id = "bbq", allowsSauces = true, maxSauces = 2)
        val insumos = mapOf("alitas" to Insumo("alitas", "Alitas", "u", 30.0))
        val incompleta = RecetaProducto("bbq", listOf(IngredienteReceta("alitas", 6.0)))
        assertFalse(CalculadoraInsumos.evaluarProducto(incompleta, producto, insumos).disponible)
    }

    @Test fun noAceptaSalsasSiLaRecetaNoLasDescuenta() {
        val producto = Product(id = "bbq")
        assertEquals(null, CalculadoraInsumos.demandaArticulo(receta, producto, listOf("BBQ Clásica")))
    }

    @Test fun salsaElegidaConsumeVeinticincoMlYLaSegundaEsOpcional() {
        val producto = Product(id = "bbq", name = "Alitas BBQ", allowsSauces = true,
            maxSauces = 2, availableSauces = listOf("BBQ Clásica", "Acevichada"))
        val dinamica = RecetaProducto("bbq", listOf(IngredienteReceta("alitas", 6.0)),
            listOf(25.0, 25.0), mapOf("BBQ Clásica" to "bbq", "Acevichada" to "acevichada"))
        val una = CalculadoraInsumos.demandaArticulo(dinamica, producto, listOf("BBQ Clásica"))!!
        assertEquals(25.0, una.last().quantity, 0.0)
        assertEquals("bbq", una.last().ingredientId)
        val dos = CalculadoraInsumos.demandaArticulo(dinamica, producto,
            listOf("BBQ Clásica", "Acevichada"))!!
        assertEquals(3, dos.size)
        assertEquals("acevichada", dos.last().ingredientId)
        assertEquals(25.0, dos.last().quantity, 0.0)
    }

    @Test fun salsaSinInsumoVinculadoNoSeOfrece() {
        val producto = Product(id = "bbq", allowsSauces = true,
            availableSauces = listOf("BBQ Clásica", "Acevichada"))
        val dinamica = RecetaProducto("bbq", listOf(IngredienteReceta("alitas", 6.0)),
            listOf(25.0, 25.0), mapOf("BBQ Clásica" to "bbq"))
        val disponibles = CalculadoraInsumos.salsasDisponibles(dinamica, producto, mapOf(
            "bbq" to Insumo("bbq", "Salsa BBQ", "ml", 250.0, 1000.0, 200.0)
        ))
        assertEquals(listOf("BBQ Clásica"), disponibles)
        assertEquals(null, CalculadoraInsumos.demandaArticulo(dinamica, producto, listOf("Acevichada")))
    }
}
