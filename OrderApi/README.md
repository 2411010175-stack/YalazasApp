# Pedidos con descuento atómico de insumos

`OrderApi` recibe pedidos autenticados desde Android y el POS web. Usa Firebase Admin SDK en un servidor privado. En una transacción de Firestore lee productos, recetas, promociones, insumos y mesa; comprueba el precio y la demanda total; descuenta existencias; y crea `orders/{requestId}`. Si la transacción falla, no se guarda el pedido ni se descuenta stock. Firestore reintenta la transacción cuando cambia alguno de los documentos leídos. El `requestId` permite repetir una solicitud tras un corte de red sin duplicarla.

La cancelación de un pedido **pendiente** devuelve exactamente `inventoryUsage`. Si ya pasó a preparación, la cancelación administrativa no devuelve insumos consumidos. Las comandas de mesa siguen confirmando el pago al entregarse. Las ventas POS registran el pago como confirmado por administración. La carta mantiene el cálculo de disponibilidad existente.

## Estado de activación

El código está preparado, **pero no hay un servidor publicado**. Con `ORDER_API_URL` y `VITE_ORDER_API_URL` vacíos, Android y el panel web conservan el flujo anterior: comprueban stock, pero no lo descuentan. `firestore.rules` tampoco se ha sustituido. No publiques aún `firestore.atomic.rules`: rechaza las creaciones directas y las versiones antiguas de la app dejarían de poder registrar pedidos.

## Preparar el servidor

1. Instala Node.js 22 o posterior y ejecuta `npm ci` en `OrderApi`.
2. Consigue una cuenta de servicio de **Firebase Admin SDK** en Firebase Console → Configuración del proyecto → Cuentas de servicio. Guarda la clave fuera del repositorio. No es la API key de Cloudinary ni la API key pública de Firebase.
3. Configura en el alojamiento privado `FIREBASE_PROJECT_ID=yalazas-5fc9a`. Para desarrollo local, configura `GOOGLE_APPLICATION_CREDENTIALS` con la ruta absoluta de la clave. En un alojamiento que use variables secretas, configura `FIREBASE_SERVICE_ACCOUNT_JSON` con el JSON completo. No publiques estos valores en Android, Vite, GitHub ni en una página web.
4. Configura `API_ALLOWED_ORIGINS` con la dirección exacta del panel web publicado, por ejemplo `https://panel.ejemplo.com`. Se admiten varias direcciones separadas por comas. Las peticiones Android autenticadas no envían `Origin`.
5. Publica `OrderApi` en un alojamiento Node.js con HTTPS. El punto de entrada de una función Node compatible con `api/` es `api/orders.js`; `npm start` ejecuta un servidor Node local en el puerto `PORT` (8080 por defecto). El endpoint es `https://tu-servidor/api/orders`.

La API exige un Firebase ID token válido. El cliente solo envía IDs de producto, cantidades, salsas y datos de entrega/pago. El servidor consulta los productos vigentes, calcula los precios, la tarifa versionada en `settings/deliveryTariff` y el consumo, y verifica el rol del usuario. Una versión de tarifa antigua se rechaza. Los cambios en `ingredients` y `orders` se hacen juntos.

## Activar Android y el panel web

1. Configura las recetas reales y el stock actual en **Administrador → Insumos**.
2. En el `local.properties` privado del proyecto Android agrega `ORDER_API_URL=https://tu-servidor/api/orders`, sincroniza Gradle y distribuye la nueva versión de la app.
3. En `AppWebAdmin/.env.local` agrega `VITE_ORDER_API_URL=https://tu-servidor/api/orders`, vuelve a compilar y publica el panel web. Vite incluye esta URL pública en el navegador; solo la credencial del servidor es secreta.
4. Prueba un pedido de cliente, una comanda de mesero, una venta POS, una cancelación pendiente y un rechazo por falta de stock. Comprueba `orders/{id}.inventoryUsage`, `inventoryVersion` y el saldo de `ingredients`.
5. Cuando **todos los clientes usen la nueva versión**, publica `OrderApi/firestore.atomic.rules` como reglas activas. `OrderApi/firebase.atomic.json` deja preparado ese archivo para Firebase CLI. Estas reglas prohíben que un teléfono o navegador cree pedidos directamente y que cancele un pedido con inventario gestionado sin la API. El servidor Admin SDK sigue pudiendo escribir. Conserva una copia de las reglas actuales antes de publicar.

Las versiones antiguas pueden leer sus pedidos después del cambio de reglas, pero no podrán crear pedidos nuevos. Si la API está configurada y falla, la app muestra un error; no vuelve al flujo directo. Eso evita registrar una venta sin descuento de stock.

## Pruebas

- `npm test` en `OrderApi` prueba demanda agregada, 20 solicitudes concurrentes en un almacén simulado, reintentos, cancelación y precios cambiados.
- `firebase-rules-tests/atomic.test.cjs` comprueba con Firestore Emulator que la regla de activación bloquea las escrituras directas y permite seguir preparando pedidos.
- Con Firestore Emulator activo, `npm test` también ejecuta una transacción real con 20 solicitudes simultáneas: como máximo se aceptan 10, el saldo corresponde exactamente a los pedidos guardados y nunca es negativo. Firestore puede rechazar temporalmente alguna solicitud por alta contención; repetirla con el mismo `requestId` mantiene la seguridad y puede completar las porciones restantes. Repite esta prueba en un entorno de ensayo antes de publicar.

## Límites

La API necesita alojamiento HTTPS y una credencial de Firebase Admin guardada solo en el servidor. No se ha desplegado ni activado porque aún no hay alojamiento elegido. La tarifa de delivery se recalcula con la distancia geográfica entre el local y las coordenadas enviadas; la veracidad física de la dirección sigue siendo una validación operativa aparte. La devolución se hace al cancelar antes de cocinar; después de iniciar preparación, el inventario ya se considera consumido.
