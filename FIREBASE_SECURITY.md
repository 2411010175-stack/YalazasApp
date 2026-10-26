# Reglas de Firebase para Yalazas

`firebase.json` vincula estas reglas al emulador y a futuros comandos de despliegue. Guardarlas en este repositorio no cambia las reglas del proyecto en la nube. El propietario indicó que publicó una versión anterior de las reglas de **Firestore** en la consola; esa publicación no se ha comprobado desde este entorno. La nueva sección `promotions` añadida después debe publicarse para que funcionen las ofertas sobre productos existentes. No se ha confirmado la publicación de las reglas de **Storage**.

## Acceso previsto

| Recurso | Cliente | Repartidor | Administrador |
| --- | --- | --- | --- |
| `users` | Su propio perfil; no puede asignarse roles | Su propio perfil | Lee perfiles y cambia rol/estado de otras cuentas |
| `products` | Lee el catálogo | Lee el catálogo | Crea, edita y elimina productos |
| `promotions` | Lee las ofertas aplicadas | Lee las ofertas aplicadas | Crea, edita y retira ofertas sin modificar el producto base |
| `orders` | Crea y consulta los suyos; cancela solo en `PENDIENTE` | Consulta pedidos de delivery, toma pedidos en `EN_PROCESO` y finaliza los asignados | Consulta todos y avanza estados secuencialmente |
| `auditoria` | Sin acceso directo | Sin acceso directo | Lee y crea registros |
| Storage `products/` | Lee imágenes | Lee imágenes | Sube imágenes de hasta 5 MB y las elimina |

El formulario actual de productos guarda únicamente una URL pública HTTPS en `products.imageUrl`; ya no ofrece subir fotos desde el dispositivo. Las reglas de Storage permanecen en el proyecto para archivos anteriores o un uso futuro, pero este flujo de la app no las utiliza.

Las ofertas sobre productos ya existentes se guardan en `promotions/{productId}` con `productId`, `offerPrice`, `offerTag` y `active`. El documento original de `products` conserva su precio. Las nuevas ofertas o combos independientes siguen siendo productos nuevos en `products`.

Las pantallas de repartidor ahora consultan `orders` con `isDelivery == true`, como exigen las reglas. El catálogo ya no intenta sembrar productos desde una sesión de cliente: un administrador debe crear el primer producto.

## Pruebas locales

Se necesita Node.js y Java. Dentro de `firebase-rules-tests`, ejecutar `npm ci`. Desde la raíz del proyecto:

```powershell
npx firebase-tools@13.35.1 emulators:exec --only firestore,storage --project demo-yalazas "npm --prefix firebase-rules-tests test"
```

El ID `demo-yalazas` es exclusivo del emulador; el comando no utiliza la base de datos real.

## Antes de publicar

1. Antes de una futura publicación, comparar estas reglas con las reglas vigentes en la consola Firebase. Este repositorio no puede verificar automáticamente su versión desplegada.
2. Verificar que exista un documento `users/{uid}` de administrador con `role: "ADMINISTRADOR"` o `"ADMIN"` y `active: true`. El alta normal solo crea perfiles `CLIENTE`.
3. Revisar pedidos antiguos: las consultas de reparto requieren el campo booleano `isDelivery`; las reglas de transición esperan los estados normalizados `PENDIENTE`, `EN_PROCESO`, `EN_CAMINO`, `ENTREGADO` y `CANCELADO`.
4. Para que Storage consulte el rol en Firestore, Firebase puede requerir habilitar la integración de permisos entre ambos servicios al publicar las reglas.
5. Probar registro, perfil, compra, cancelación, administración y reparto con cuentas de prueba antes de desplegar a producción.

Las reglas limitan acceso y transiciones, pero no pueden verificar de forma confiable que el total enviado por la app corresponda al precio vigente de todos los productos ni convertir los registros de auditoría escritos desde clientes en evidencia confiable. Para cobrar y auditar en producción hará falta una operación de servidor que calcule el total y registre los eventos. La cancelación del cliente deja de escribir directamente en `auditoria`; su estado permanece en `orders`.
