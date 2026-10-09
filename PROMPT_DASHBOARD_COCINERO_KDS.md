# 👨‍🍳 Prompt de Desarrollo: Dashboard de Cocina (KDS) y Flujo de Estados

Copia y pega el siguiente prompt cuando desees iniciar el desarrollo completo del módulo de Cocina:

```markdown
Hola Claude, por favor implementa el módulo completo del **Rol Cocinero / Sistema de Cocina (KDS - Kitchen Display System)** y ajusta el ciclo de vida de los estados de pedidos con las siguientes especificaciones:

---

### 🎯 1. Ciclo de Vida y Transición de Estados
Actualizar la máquina de estados en `OrderStatus.kt` y Firestore con el flujo secuencial:
1. **`PENDIENTE` / `EN_PROCESO`**: El cliente envía el pedido y entra automáticamente a la pantalla de cocina.
2. **`LISTO_PARA_ENTREGA`**: El cocinero termina de preparar y empaqueta el pedido. Presiona "¡Listo para Entrega!".
3. **`EN_CAMINO`**: El repartidor toma el pedido (el repartidor SOLO puede ver pedidos con estado `LISTO_PARA_ENTREGA`).
4. **`ENTREGADO`**: El repartidor cobra y entrega al cliente.

---

### 👨‍🍳 2. Dashboard de Cocina / KDS (Kitchen Display System)
1. **Nuevo Rol `COCINERO`**:
   - Agregar `UserRole.COCINERO` / `rol = "cocinero"` en `UserProfile.kt` y selector en `MainActivity` / `ProfileFragment`.
2. **Interfaz de Comandas de Cocina (`KitchenDashboardFragment.kt`)**:
   - Diseño estilo KDS profesional con temática oscura optimizado para tablets y móviles en horizontal/vertical.
   - Grid / lista de tarjetas de comanda en tiempo real conectadas a Firestore.
   - **Header de Comanda**: Código de pedido (ej. `#A4B92C`), tipo de servicio (🛵 Delivery o 🛍️ Retiro en Local) y hora de creación.
   - **Cronómetro en Vivo**: Contador de minutos transcurridos desde que se hizo el pedido (Verde: < 10 min, Amarillo: 10-20 min, Rojo: > 20 min).
   - **Lista de Platos e Ingredientes**: Cantidad en badge grande, nombre del producto, salsas elegidas y notas especiales del cliente.
   - **Alerta Sonora**: Sonido/campana automática cuando ingresa una nueva comanda en tiempo real.
   - **Botón de Despacho**: Botón "¡Pedido Listo! ✅" con diálogo/animación de confirmación que actualiza el estado a `LISTO_PARA_ENTREGA`.
3. **Pestañas del KDS**:
   - *"En Preparación"* (comandas activas pendientes).
   - *"Historial / Listos"* (comandas despachadas recientemente con opción de reabrir en caso de error).

---

### 🛵 3. Ajuste en Repartidor y Cliente
1. **Repartidor (`DeliveryOrdersFragment.kt`)**:
   - Filtrar los pedidos disponibles para que SOLO aparezcan aquellos con estado `LISTO_PARA_ENTREGA`.
2. **Cliente (`TrackingFragment.kt`)**:
   - Reflejar la etapa intermedia: *"👨‍🍳 Cocinando tu pedido"* y *"📦 Empacado y listo para salir"*.

Por favor revisa el código actual, implementa los archivos necesarios y asegúrate de que `./gradlew compileDebugSources` compile exitosamente.
```
