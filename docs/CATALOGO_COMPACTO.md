# Catálogo compacto e imágenes de tarjeta

La carta del cliente observa `catalog_cards` cuando `catalog_cards/_meta.ready` vale `true`.
Cada tarjeta contiene nombre, precio, categoría, URL, los primeros 120 caracteres de
la descripción e información para elegir salsas. Al abrir un plato o añadirlo al carrito, Android lee
su documento completo en `products`. La carta sigue calculando la disponibilidad con
`ingredients` y `recipes`: los platos sin insumos suficientes no se muestran.

## Activación con el catálogo existente

1. Publica `firestore.rules` en Firebase.
2. Abre `AppWebAdmin` con una cuenta de administrador conectada a Firebase.
   Al recibir la lista de `products`, el panel sincroniza las tarjetas y escribe
   `catalog_cards/_meta.ready = true` al terminar. Si falla, el panel muestra el error
   y reintenta al volver a cargar.
3. Comprueba en Firestore que el número de tarjetas, sin `_meta`, coincida con el de
   productos. Comprueba que la carta del cliente y la del mesero oculten platos agotados.

Hasta que la migración termine, la carta del cliente usa `products` como respaldo.
Las altas, cambios y eliminaciones hechos por las versiones nuevas de Android y del
panel web escriben `products` y `catalog_cards` en un mismo lote de Firestore.
Conviene actualizar ambos clientes administrativos antes de activar la vista compacta;
una versión antigua podría cambiar `products` sin actualizar su tarjeta.

Las tarjetas de Android y la web solicitan miniaturas a Cloudinary con dimensiones,
calidad y formato adaptados. Las URL externas se usan tal cual. El detalle conserva la
foto original. Para verificar la mejora, compara los bytes descargados y el tiempo de
carga inicial con Android Studio Network Profiler en la misma red, antes y después de
la activación. Firestore Usage permite comparar las lecturas de documentos.
