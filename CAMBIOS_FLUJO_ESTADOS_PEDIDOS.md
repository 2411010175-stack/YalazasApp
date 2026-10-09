# 📋 Guía y Cambios Pendientes: Flujo Secuencial de Pedidos y Restricción de Cancelación

Este documento contiene la planificación, lógica de negocio y código exacto para implementar cuando desees:
1. **Flujo Secuencial de Estados (Administrador)**: Evitar que el administrador se salte etapas operativas (ej. no saltar de `PENDIENTE` a `ENTREGADO`).
2. **Restricción de Cancelación para Clientes**: Permitir que el usuario cancele su pedido **únicamente cuando está en `PENDIENTE`**, y bloquear la cancelación una vez pase a `EN_PROCESO` (Cocina / Preparación) o posterior.

---

## 🎯 1. Reglas de Negocio y Máquina de Estados

### 🔄 Flujo Operativo del Pedido
```text
[ 🟡 PENDIENTE ] ───────► [ 👨‍🍳 EN PROCESO (Cocina) ] ───────► [ 🛵 EN CAMINO ] ───────► [ ✅ ENTREGADO ]
       │                                │                              │
       ▼                                ▼                              ▼
[ ❌ CANCELADO ]                 [ ❌ CANCELADO ]               [ ❌ CANCELADO ]
```

* **`PENDIENTE` (Esperando confirmación)**:
  * **Siguiente paso del Admin**: Avanzar a `EN_PROCESO` (Cocina) o `CANCELADO`.
  * **Acción del Cliente**: **PUEDE CANCELAR** el pedido desde su app.
* **`EN_PROCESO` (En preparación / Cocina)**:
  * **Siguiente paso del Admin**: Avanzar a `EN_CAMINO` (Delivery) o `CANCELADO` (por incidencia interna).
  * **Acción del Cliente**: **BLOQUEADO**. Ya no puede cancelar porque los insumos ya se están cocinando.
* **`EN_CAMINO` (En ruta con repartidor)**:
  * **Siguiente paso del Admin**: Avanzar a `ENTREGADO` o `CANCELADO`.
  * **Acción del Cliente**: **BLOQUEADO**. El repartidor ya está en camino.
* **`ENTREGADO` / `CANCELADO`**:
  * **Estado Terminal**: El pedido ya finalizó, no se permiten transiciones posteriores.

---

## 💻 2. Código de Soporte: `OrderStatus.kt`

**Ubicación:** `app/src/main/java/com/yalazas/app/model/OrderStatus.kt`

```kotlin
package com.yalazas.app.model

import java.util.Locale

enum class OrderStatus(
    val key: String,
    val label: String,
    val actionText: String,
    val emoji: String
) {
    PENDIENTE(
        key = "PENDIENTE",
        label = "Pendiente de Confirmación",
        actionText = "Pasar a Cocina / Preparación",
        emoji = "🟡"
    ),
    EN_PROCESO(
        key = "EN_PROCESO",
        label = "En Cocina (Preparación)",
        actionText = "Despachar a Repartidor / Delivery",
        emoji = "👨‍🍳"
    ),
    EN_CAMINO(
        key = "EN_CAMINO",
        label = "En Camino (Delivery)",
        actionText = "Marcar como Entregado al Cliente",
        emoji = "🛵"
    ),
    ENTREGADO(
        key = "ENTREGADO",
        label = "Entregado (Completado)",
        actionText = "Pedido Finalizado",
        emoji = "✅"
    ),
    CANCELADO(
        key = "CANCELADO",
        label = "Cancelado (Anulado)",
        actionText = "Cancelar Pedido",
        emoji = "❌"
    );

    companion object {
        fun fromString(status: String?): OrderStatus {
            val upper = status?.uppercase(Locale.getDefault()) ?: ""
            return when {
                upper.contains("PENDIENTE") -> PENDIENTE
                upper.contains("PROCESO") || upper.contains("COCIN") || upper.contains("PREPAR") -> EN_PROCESO
                upper.contains("CAMINO") || upper.contains("REPART") || upper.contains("DELIVERY") -> EN_CAMINO
                upper.contains("ENTREG") -> ENTREGADO
                upper.contains("CANCEL") -> CANCELADO
                else -> PENDIENTE
            }
        }

        /**
         * Retorna las transiciones estrictamente válidas y secuenciales para el Administrador.
         * Evita que se salten etapas del proceso operativo de pedidos.
         */
        fun getAllowedTransitions(currentStatusStr: String?): List<OrderStatus> {
            val current = fromString(currentStatusStr)
            return when (current) {
                PENDIENTE -> listOf(EN_PROCESO, CANCELADO)
                EN_PROCESO -> listOf(EN_CAMINO, CANCELADO)
                EN_CAMINO -> listOf(ENTREGADO, CANCELADO)
                ENTREGADO -> emptyList() // Pedido completado (Terminal)
                CANCELADO -> emptyList() // Pedido cancelado (Terminal)
            }
        }

        /**
         * REGLA DE NEGOCIO: El cliente solo puede cancelar el pedido mientras esté PENDIENTE.
         * En el momento que pasa a EN_PROCESO (en cocina/preparación) o posteriores,
         * el usuario YA NO PUEDE cancelar el pedido.
         */
        fun canCustomerCancel(statusStr: String?): Boolean {
            val status = fromString(statusStr)
            return status == PENDIENTE
        }
    }
}
```

---

## 🛠️ 3. Cambios en la Vista del Administrador

### En `AdminSaleDetailsBottomSheet.kt`
Reemplazar `showChangeStatusDialog()` por una versión que utilice `OrderStatus.getAllowedTransitions()`:

```kotlin
    private fun showChangeStatusDialog() {
        val currentEnum = OrderStatus.fromString(status)
        val allowedTransitions = OrderStatus.getAllowedTransitions(status)

        if (allowedTransitions.isEmpty()) {
            val message = if (currentEnum == OrderStatus.ENTREGADO) {
                "Este pedido ya se encuentra completado (ENTREGADO) y no puede ser modificado."
            } else {
                "Este pedido ya ha sido CANCELADO y se encuentra cerrado."
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pedido Finalizado")
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }

        val items = allowedTransitions.map { nextStatus ->
            when (nextStatus) {
                OrderStatus.EN_PROCESO -> "👨‍🍳 Avanzar a: EN COCINA (Preparación)"
                OrderStatus.EN_CAMINO -> "🛵 Avanzar a: EN CAMINO (Repartidor en ruta)"
                OrderStatus.ENTREGADO -> "✅ Avanzar a: ENTREGADO (Pedido completado)"
                OrderStatus.CANCELADO -> "❌ Cancelar Pedido (Anular orden)"
                else -> "${nextStatus.emoji} ${nextStatus.label}"
            }
        }.toTypedArray()

        var selectedIndex = 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Actualizar Flujo del Pedido")
            .setSingleChoiceItems(items, 0) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Confirmar") { _, _ ->
                val targetStatus = allowedTransitions[selectedIndex]
                if (targetStatus == OrderStatus.CANCELADO) {
                    confirmCancelOrder(targetStatus.key)
                } else {
                    applyStatusUpdate(targetStatus.key)
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun confirmCancelOrder(cancelKey: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Confirmar Cancelación?")
            .setMessage("¿Estás seguro de cancelar este pedido? Esta acción anulará la orden.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Sí, Cancelar Pedido") { _, _ ->
                applyStatusUpdate(cancelKey)
            }
            .setNegativeButton("No, Volver", null)
            .show()
    }
```

### En `AdminSalesFragment.kt`
Aplicar la misma lógica a `showChangeStatusDialog(order: Order)` para que el botón directo en la tarjeta del listado respete las mismas reglas secuenciales.

---

## 📱 4. Cambios en la Vista del Cliente (`TrackingFragment.kt`)

Para la vista de cliente en `TrackingFragment.kt`:
1. Consultar en tiempo real los pedidos del usuario autenticado (`userId == AuthRepository.currentProfile?.uid`).
2. Para cada tarjeta de pedido:
   * **Si el estado es `PENDIENTE`**: Mostrar el botón **"Cancelar Pedido"**.
   * **Si el estado es `EN_PROCESO`, `EN_CAMINO` o `ENTREGADO`**:
     * Ocultar el botón de cancelar.
     * Mostrar una nota informativa:
       * Para `EN_PROCESO`: *"👨‍🍳 Tu pedido ya está preparándose en cocina. Ya no es posible cancelarlo."*
       * Para `EN_CAMINO`: *"🛵 Tu pedido está en camino con el repartidor."*
   * **Acción de Cancelar (en `PENDIENTE`)**:
     * Diálogo de confirmación: *"¿Estás seguro de cancelar tu orden? Esta acción no se puede deshacer."*
     * Si confirma: Actualizar Firestore `orders/$orderId` con `status: "CANCELADO"` y registrar `updatedAt: System.currentTimeMillis()`.

---
*Archivo guardado para su posterior implementación a solicitud del usuario.*
