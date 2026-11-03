# Reglas de Firebase para Yalazas

`firebase.json` vincula estas reglas al emulador y a futuros comandos de despliegue. Guardarlas en este repositorio no cambia las reglas del proyecto en la nube. El propietario indicó que publicó una versión anterior de las reglas de **Firestore** en la consola; esa publicación no se ha comprobado desde este entorno. Las secciones nuevas de `promotions` y cocina, incluido `LISTO_PARA_ENTREGA`, deben publicarse para que funcionen las ofertas y el nuevo flujo. No se ha confirmado la publicación de las reglas de **Storage**.

## Acceso previsto

| Recurso | Cliente | Cocinero | Repartidor | Administrador |
| --- | --- | --- | --- | --- |
| `users` | Su propio perfil; no puede asignarse roles | Su propio perfil | Su propio perfil | Lee perfiles y cambia rol/estado de otras cuentas |
| `products` | Lee el catálogo | Lee el catálogo | Lee el catálogo | Crea, edita y elimina productos |
| `promotions` | Lee las ofertas aplicadas | Lee las ofertas aplicadas | Lee las ofertas aplicadas | Crea, edita y retira ofertas sin modificar el producto base |
| `orders` | Crea y consulta los suyos; cancela solo en `PENDIENTE` | Ve pedidos activos, avanza `PENDIENTE → EN_PROCESO → LISTO_PARA_ENTREGA` y puede reabrir uno listo antes de que reparto lo tome | Consulta pedidos de delivery, toma solo los `LISTO_PARA_ENTREGA` y finaliza los asignados | Consulta todos y avanza estados secuencialmente |
| `orders/{orderId}/tracking/actual` | Lee solo la ubicación de su pedido mientras está `EN_CAMINO` | Sin acceso | Publica GPS solo para pedidos que tiene asignados y están `EN_CAMINO` | Puede leer |
| `auditoria` | Sin acceso directo | Sin acceso directo | Sin acceso directo | Lee y crea registros |
| Storage `products/` | Lee imágenes | Lee imágenes | Lee imágenes | Sube imágenes de hasta 5 MB y las elimina |

El formulario actual de productos guarda únicamente una URL pública HTTPS en `products.imageUrl`; ya no ofrece subir fotos desde el dispositivo. Las reglas de Storage permanecen en el proyecto para archivos anteriores o un uso futuro, pero este flujo de la app no las utiliza.

Las ofertas sobre productos ya existentes se guardan en `promotions/{productId}` con `productId`, `offerPrice`, `offerTag` y `active`. El documento original de `products` conserva su precio. Las nuevas ofertas o combos independientes siguen siendo productos nuevos en `products`.

Las pantallas de repartidor consultan `orders` con `isDelivery == true`; cocina consulta solo los estados activos permitidos. El catálogo ya no intenta sembrar productos desde una sesión de cliente: un administrador debe crear el primer producto.

Las escrituras de estado de cliente, cocina, repartidor y administrador pasan por `RepositorioEstadosPedido.kt`, que comprueba el estado remoto en una transacción antes de escribir. La toma de varios pedidos es atómica; el cambio administrativo y su registro en `auditoria` se guardan juntos. Las reglas de Firestore siguen siendo la autorización final y deben publicarse por separado.

La ubicación del repartidor se guarda en un documento `tracking/actual` dentro de cada pedido activo, separado del campo `status` para no alterar State. El servicio de ubicación se inicia desde la pantalla de entregas al haber pedidos `EN_CAMINO`, pide permiso de ubicación al repartidor y muestra una notificación permanente. Se detiene al terminar todas sus entregas o cerrar sesión. La pantalla del cliente escucha el documento de su pedido y señala cuándo el dato tiene más de dos minutos. La distancia mostrada es geográfica en línea recta y no representa la ruta por calles. Si el GPS, Internet o las reglas publicadas no están disponibles, la pantalla indica que está esperando señal.

## Pruebas locales

Se necesita Node.js y Java. Dentro de `firebase-rules-tests`, ejecutar `npm ci`. Desde la raíz del proyecto:

```powershell
npx firebase-tools@13.35.1 emulators:exec --only firestore,storage --project demo-yalazas "npm --prefix firebase-rules-tests test"
```

El ID `demo-yalazas` es exclusivo del emulador; el comando no utiliza la base de datos real.

## Antes de publicar

1. Antes de una futura publicación, comparar estas reglas con las reglas vigentes en la consola Firebase. Este repositorio no puede verificar automáticamente su versión desplegada.
2. Verificar que exista un documento `users/{uid}` de administrador con `role: "ADMINISTRADOR"` o `"ADMIN"` y `active: true`. El alta normal solo crea perfiles `CLIENTE`.
3. Revisar pedidos antiguos: las consultas de reparto requieren el campo booleano `isDelivery`; las reglas de transición esperan los estados normalizados `PENDIENTE`, `EN_PROCESO`, `LISTO_PARA_ENTREGA`, `EN_CAMINO`, `ENTREGADO` y `CANCELADO`. Los pedidos antiguos en `EN_PROCESO` deben pasar por cocina antes de que reparto pueda tomarlos.
4. Para que Storage consulte el rol en Firestore, Firebase puede requerir habilitar la integración de permisos entre ambos servicios al publicar las reglas.
5. Probar registro, perfil, compra, cancelación, administración y reparto con cuentas de prueba antes de desplegar a producción.

Las reglas limitan acceso y transiciones, pero no pueden verificar de forma confiable que el total enviado por la app corresponda al precio vigente de todos los productos ni convertir los registros de auditoría escritos desde clientes en evidencia confiable. Para cobrar y auditar en producción hará falta una operación de servidor que calcule el total y registre los eventos. La cancelación del cliente deja de escribir directamente en `auditoria`; su estado permanece en `orders`.
