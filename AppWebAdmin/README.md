# Panel web administrativo de Yalaza's

Aplicación independiente para escritorio y móvil, construida con React, TypeScript, Bootstrap 5 y Firebase. Comparte las colecciones de la aplicación Android; no crea una base de datos aparte.

Vistas de referencia: [escritorio](preview-dashboard.png), [menú móvil](preview-mobile.png) y [acceso](preview-login-logo.png). Las capturas del panel usan datos de ejemplo.

## Abrir el panel

1. Instala Node.js **24.12 o posterior**.
2. En esta carpeta ejecuta `npm install` y `npm run dev`.
3. Abre `http://127.0.0.1:5173/` e inicia sesión con una cuenta de Firebase Authentication cuyo documento `users/{uid}` tenga `role: "ADMIN"` o `role: "ADMINISTRADOR"` y `active` distinto de `false`.

Ya se registró la aplicación web **Yalazas Admin Web** en el proyecto Firebase `yalazas-5fc9a`. Sus datos públicos de configuración están en `.env.local`, un archivo local ignorado por Git. Si clonas el proyecto en otro equipo, copia `.env.example` a `.env.local` y completa `VITE_FIREBASE_API_KEY` y `VITE_FIREBASE_APP_ID` desde Firebase Console → Configuración del proyecto → Tus aplicaciones → Web.

Si falta la configuración, el panel presenta una **vista de ejemplo** con datos ficticios y sin botones de guardado. Con la configuración real, exige inicio de sesión y muestra las colecciones en tiempo real. La sesión solo abre el panel si el perfil tiene un rol administrativo; las reglas de Firestore validan las lecturas y escrituras.

## Secciones

| Sección | Acciones disponibles |
| --- | --- |
| Resumen | Ventas completadas y pagadas de hoy, pedidos, ticket promedio, mesas ocupadas, alertas, gráficos por hora y canal, productos vendidos y accesos rápidos. |
| Venta directa / POS | Seleccionar productos y salsas, cambiar cantidades, registrar el cobro presencial y enviar el pedido a cocina. Vuelve a verificar precios e insumos contra el servidor antes de confirmar. |
| Ventas y filtros | Ordenar pagos por revisar primero; filtrar por fechas, canal, pago y estado; revisar comprobantes privados, aprobar o rechazar pagos, ver e imprimir ticket, cambiar estados permitidos y exportar Excel o PDF. |
| Clientes | Consultar contacto e historial de pedidos; activar o desactivar cuentas. |
| Inventario y almacén | Gestionar productos, ofertas, fotos públicas, insumos, entradas, ajustes, alertas y cantidades consumidas por receta. |
| Repartidores | Consultar datos, vehículo y ruta activa; editar vehículo y rol administrativo. “Sin ruta” no implica disponibilidad para nuevas entregas. |
| Salón y mesas | Consultar ocupación; añadir o retirar mesas sin comanda abierta. |
| Ajustes | Consultar perfil y administrar roles y activación de usuarios. |

La sucursal **San Antonio** se muestra como sede actual. El modelo de datos aún no incluye un identificador de sucursal para filtrar operaciones entre locales. La búsqueda global usa nombre, correo, teléfono y código de pedido; los perfiles actuales no tienen campo DNI.

## Conectar imágenes de productos

Las imágenes de productos pueden guardarse mediante URL, o subirse a Cloudinary desde Inventario. Para habilitar la subida, crea en Cloudinary un **upload preset sin firma** llamado `yalazas_productos` para fotos públicas y limita formato, tamaño y carpeta desde ese preset. En `.env.local` ya están el `cloud name` y el nombre esperado del preset; ajústalo si utilizas otro nombre. **No pongas el API Secret de Cloudinary en el código web.** Los comprobantes de pago continúan en la ruta privada de Firestore `orders/{id}/privado/comprobante` y solo se leen para la revisión administrativa.

## Reglas y publicación

Las reglas locales de `../firestore.rules` incluyen la venta presencial pagada por el administrador. Para que el POS funcione contra el proyecto real, primero revisa y publica esas reglas con:

```bash
firebase deploy --only firestore:rules --project yalazas-5fc9a
```

La aplicación web **no está publicada en Hosting**. Antes de publicarla, agrega el dominio elegido a los dominios autorizados de Firebase Authentication. Construye los archivos de producción con `npm run build`; la salida queda en `dist/`.

El botón **Imprimir ticket** imprime un resumen interno del pedido. La emisión de boletas fiscales y su reimpresión requieren un sistema de facturación externo; se muestra un número de comprobante únicamente si ya existe en el documento del pedido.

## Verificación

Ejecuta `npm test` para revisar los cálculos de ventas, prioridad de pagos y pausa por stock. Ejecuta `npm run build` para comprobar tipos y empaquetado. Las pruebas de permisos Firestore del repositorio principal se encuentran en `../firebase-rules-tests/`.
