# Gestión de mesas del local

En la app, entra como administrador a **Resumen > Ajustes > Mesas del local**. Allí se ven las mesas configuradas, cuántas están libres u ocupadas y sus comandas en curso. **Agregar mesa** acepta números del 1 al 200. **Quitar** solo se habilita para mesas sin comandas activas y pide confirmación.

Las cinco mesas que ya existían siguen apareciendo mientras no se haya guardado una configuración nueva. La primera adición o retirada guarda la lista en `settings/salon.tableNumbers`. Si el administrador retira todas, la lista queda vacía y las cinco iniciales no reaparecen. Los pedidos históricos conservan el número de la mesa que usaron.

El mesero ve la misma lista en tiempo real en **Salón y mesas** y al crear una comanda. Si una mesa seleccionada se retira, debe elegir otra. El registro del pedido vuelve a comprobar la lista en Firestore antes de guardarlo.

## Antes de probar en dos teléfonos

Publica las reglas nuevas desde la raíz del proyecto y luego instala la app actualizada en los dispositivos:

```powershell
firebase deploy --only firestore:rules --project yalazas-5fc9a
```

Las reglas permiten leer la configuración al mesero y al administrador; solo este último puede cambiarla. Además, impiden registrar comandas nuevas para mesas retiradas. La consulta de ocupación previa a retirar una mesa se hace contra el servidor; si otra comanda entra justo después de esa comprobación, la interfaz puede necesitar refrescarse para mostrarla. Por eso conviene retirar mesas cuando no se estén tomando comandas en ellas.
