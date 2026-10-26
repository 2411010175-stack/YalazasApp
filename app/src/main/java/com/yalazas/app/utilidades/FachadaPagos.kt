package com.yalazas.app.utilidades

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.util.Locale

/** Reúne los datos de pago, el cálculo de vuelto y la copia de cuentas al portapapeles. */
object FachadaPagos {

    // Datos visibles en la confirmación del pedido. Los códigos de pago siguen iguales.
    const val TELEFONO_YAPE = "922 385 572"
    const val TITULAR_YAPE = "Yalazas Fast Food - Alex"

    const val TELEFONO_PLIN = "922 385 572"
    const val TITULAR_PLIN = "Yalazas Fast Food - Alex"

    const val CUENTA_BCP = "191-98765432-0-12"
    const val CUENTA_INTERBANK = "200-3001234567"

    /** Métodos ofrecidos al cliente; `codigo` es el valor que se guarda en el pedido. */
    enum class TipoPago(val codigo: String, val nombreVisible: String) {
        EFECTIVO("EFECTIVO", "Efectivo"),
        YAPE("YAPE", "Yape"),
        PLIN("PLIN", "Plin"),
        TRANSFERENCIA("TRANSFERENCIA", "Transferencia")
    }

    /** Resultado que la pantalla usa para mostrar si el efectivo alcanza y cuánto devolver. */
    data class ResultadoCalculoEfectivo(
        val esSuficiente: Boolean,
        val vuelto: Double,
        val icono: String,
        val mensaje: String,
        val esAdvertencia: Boolean = false,
        val esInformativo: Boolean = false
    )
    /** Evalúa el monto ingresado y prepara el mensaje de vuelto o de monto insuficiente. */
    fun calcularVuelto(montoIngresado: Double, totalPagar: Double): ResultadoCalculoEfectivo {
        return when {
            montoIngresado <= 0.0 -> ResultadoCalculoEfectivo(
                esSuficiente = false,
                vuelto = 0.0,
                icono = "ℹ️",
                mensaje = "Ingresa el monto con el que pagarás para calcular tu vuelto",
                esInformativo = true
            )
            montoIngresado < totalPagar -> {
                val missing = totalPagar - montoIngresado
                ResultadoCalculoEfectivo(
                    esSuficiente = false,
                    vuelto = 0.0,
                    icono = "⚠️",
                    mensaje = "Monto insuficiente (Faltan S/ ${String.format(Locale.US, "%.2f", missing)})",
                    esAdvertencia = true
                )
            }
            montoIngresado == totalPagar -> ResultadoCalculoEfectivo(
                esSuficiente = true,
                vuelto = 0.0,
                icono = "✅",
                mensaje = "Pago con monto exacto (Sin vuelto)"
            )
            else -> {
                val vuelto = montoIngresado - totalPagar
                ResultadoCalculoEfectivo(
                    esSuficiente = true,
                    vuelto = vuelto,
                    icono = "💵",
                    mensaje = "Tu vuelto será: S/ ${String.format(Locale.US, "%.2f", vuelto)}"
                )
            }
        }
    }

    /** Copia un teléfono o cuenta sin espacios ni guiones y devuelve el mensaje para la pantalla. */
    fun copiarAlPortapapeles(context: Context, label: String, textoCopiar: String, mensajeExito: String): String {
        // Se eliminan separadores para que el número pueda pegarse directamente.
        val textoLimpio = textoCopiar.replace(" ", "").replace("-", "")

        val portapapeles = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, textoLimpio)
        portapapeles?.setPrimaryClip(clip)

        return mensajeExito
    }
}
