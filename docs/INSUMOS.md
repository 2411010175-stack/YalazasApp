# Insumos y disponibilidad de la carta

## Configuración

En **Administrador → Insumos** se registran unidades, kilogramos o mililitros. Los insumos líquidos, incluidas todas las salsas, se introducen, consultan y ajustan siempre en mililitros. Internamente se guardan unidades, gramos o mililitros para que las recetas utilicen cantidades precisas.

1. Registrar el insumo con saldo inicial y umbrales amarillo y rojo.
2. Añadir compras con **+ Compra** o corregir el saldo según el conteo físico con **Ajustar**.
3. Vincular cada producto con sus cantidades por porción mediante **Configurar receta de un producto**.
4. Para **Alitas BBQ**, el botón de ejemplo prepara 6 alitas y 200 g de papa. Si ese producto no ofrece elegir salsas, añade 50 ml de salsa BBQ como insumo fijo. Solo cuando el producto está configurado para elegir salsas usa campos de 25 ml por salsa y vincula «BBQ Clásica» con el insumo Salsa BBQ. Los umbrales iniciales son editables: alitas 24/6 unidades, papa 3/0,2 kg y salsa BBQ 1000/200 ml (amarillo/rojo). El stock inicial es cero para no inventar compras.

**La Previa** muestra Salsa 1 y Salsa 2, con 25 ml por salsa; la segunda es opcional. Los combos que permiten salsas y los demás productos configurados para varias salsas muestran tantos campos como salsas incluidas admite el producto. Las recetas comunes no añaden dos campos de salsa automáticamente. Las variedades sin insumo vinculado no se ofrecen hasta configurarlas.

## Cálculo

La cantidad de porciones es el menor valor de `entero(stock / cantidad_por_porción)` entre los insumos fijos y una salsa disponible. Es una capacidad máxima estimada: un pedido concreto se comprueba con las salsas elegidas y su cantidad total. El plato se pausa si faltan insumos fijos o si no queda ninguna salsa vinculada que alcance para una porción. Una salsa que cae por debajo de su umbral rojo deja de ofrecerse. El estado se actualiza en las cartas de cliente y mesero mediante escuchas de Firestore. La pestaña administrativa muestra las alertas y las porciones.

Un producto sin receta, con una receta incompleta o con insumos insuficientes permanece oculto en las cartas de cliente y mesero. También queda deshabilitado en el POS web. En **Administrador → Insumos** se muestran los productos pendientes de configurar; crear un producto en **Productos** no lo publica hasta completar su receta y registrar existencias. La pausa manual `isAvailable` sigue teniendo efecto aunque el stock alcance. Si no se pueden leer recetas o existencias, la carta informa del error y la confirmación del pedido no continúa.

## Despliegue y límite actual

Hay que publicar `firestore.rules` para habilitar las colecciones `ingredients` y `recipes`. La aplicación puede compilar antes de publicar las reglas, pero el panel de insumos no podrá leer ni guardar datos hasta hacerlo.

**El descuento atómico está preparado, pero aún no activado.** Mientras `ORDER_API_URL` y `VITE_ORDER_API_URL` estén vacíos, el administrador debe ajustar el conteo físico; la comprobación actual no reserva stock ni impide pedidos simultáneos. `OrderApi/README.md` explica cómo publicar el backend confiable, conectar Android y web, probar la transacción y activar las reglas que cierran la escritura directa. Con la API activa, cada pedido descuenta los insumos de toda la cesta en la misma transacción que crea `orders`, y cancelar un pedido pendiente devuelve su consumo una sola vez.
