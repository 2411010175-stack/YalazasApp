# Imágenes de Yalazas sin activar Blaze

## Qué se guarda dónde

- **Fotos de productos:** públicas en Cloudinary. El producto guarda solo la URL HTTPS en `products.imageUrl`.
- **Comprobantes de Yape, Plin y transferencia:** privados en `orders/{pedidoId}/privado/comprobante` como imagen JPEG comprimida. No se publican en Cloudinary ni se guardan en el documento principal del pedido.
- **Efectivo:** el repartidor confirma el dinero recibido; no requiere foto.

El proyecto usa el plan gratuito Spark de Firebase. No se necesita `API secret`, Cloud Functions ni una tarjeta para este flujo. El código de `functions/` pertenece al diseño anterior y no se usa en la app actual.

## Configurar Cloudinary para las fotos de productos

1. En Cloudinary, abre **Settings > Upload > Upload presets** y crea un preset llamado `yalazas_productos`.
2. Ponlo en modo **Unsigned**. Selecciona únicamente imágenes, limita los formatos a JPG, PNG y WebP, desactiva la sobrescritura y asigna la carpeta `yalazas/productos` si deseas ordenar los archivos. Cloudinary no ofrece un límite de tamaño por preset: la app limita la imagen a 5 MB.
3. Comprueba en el archivo local `local.properties` estas dos líneas (ya preparadas en esta computadora):

```properties
CLOUDINARY_CLOUD_NAME=x8zv7nos
CLOUDINARY_UPLOAD_PRESET=yalazas_productos
```

4. Sincroniza Gradle, instala una versión nueva de la app y, como administrador, prueba **Subir foto desde el teléfono** al editar un producto. Luego guarda el producto. Su URL aparecerá en el menú.

**No pongas `CLOUDINARY_API_SECRET` ni `CLOUDINARY_API_KEY` en Android.** En una subida sin firma solo se necesitan el *cloud name* y el nombre del preset. El preset queda visible para quien inspeccione la app; otra persona podría usarlo para subir archivos a tu cuenta. Estas fotos son públicas y esta configuración conviene para pruebas o escala pequeña. Revisa el uso de Cloudinary y desactiva o cambia el preset si detectas abusos. Para impedir subidas ajenas de forma fiable hará falta un servidor de firma en el futuro.

## Activar la protección de los comprobantes

Publica **solo las reglas de Firestore**, desde la raíz del proyecto:

```powershell
firebase deploy --only firestore:rules --project yalazas-5fc9a
```

Esto no despliega Functions ni exige Blaze. Antes de publicar, revisa el `project ID` de Firebase Console si es distinto. Luego instala la app nueva en ambos teléfonos.

La app reduce cada comprobante a JPEG de máximo 700 KiB y lo envía junto con el cambio interno a `EN_REVISION` en un mismo lote. Las reglas solo permiten que el cliente propietario escriba o reenvíe su imagen para un pedido digital no pagado. Solo ese cliente y el administrador pueden leerla. El repartidor y otros clientes no tienen acceso. El administrador debe **comprobar en su cuenta que el dinero llegó**; al aprobar, el pago pasa a `PAGADO`. Si rechaza, el cliente ve un aviso dentro de **Rastreo** y puede subir otra foto. El estado de cocina/entrega del pedido no cambia por esta revisión.

El aviso funciona cuando el cliente tiene la app abierta y conectada. No es una notificación push de fondo. Las imágenes ocupan la cuota gratuita de Firestore; un documento no puede superar 1 MiB. El límite de 700 KiB deja margen para los demás campos. Conviene probar la legibilidad real de comprobantes fotografiados con varios teléfonos. En equipos con acceso al comprobante, el SDK de Firestore puede mantener caché local: protege también el acceso a esos dispositivos. La ventana de revisión bloquea capturas normales de pantalla, pero eso no sustituye la seguridad física del teléfono.

## Prueba mínima

1. Cliente: crea un pedido Yape y adjunta foto. Debe ver «Comprobante recibido».
2. Administrador: abre el detalle, visualiza la imagen y recházala. El cliente debe ver la solicitud de una nueva imagen en **Rastreo**.
3. Cliente: sube otra imagen. Administrador: verifica el dinero y aprueba. El pago debe quedar `PAGADO`.
4. Otro cliente y repartidor: no deben poder leer el documento privado ni validar el pago.

La compilación de Android y las pruebas locales de reglas verifican el código, pero el flujo con Cloudinary y teléfonos reales requiere crear el preset, publicar las reglas e instalar la app nueva.
