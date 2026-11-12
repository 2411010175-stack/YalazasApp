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

Los productos sin receta conservan su disponibilidad manual hasta que se registren sus cantidades reales. La disponibilidad manual del producto también se respeta cuando hay receta.

## Despliegue y límite actual

Hay que publicar `firestore.rules` para habilitar las colecciones `ingredients` y `recipes`. La aplicación puede compilar antes de publicar las reglas, pero el panel de insumos no podrá leer ni guardar datos hasta hacerlo.

El saldo todavía **no se descuenta automáticamente al registrar ventas**. El administrador debe ajustar el conteo físico; de lo contrario, la disponibilidad calculada quedará desactualizada. Antes de grabar una comanda, la aplicación comprueba en el servidor que los insumos registrados alcancen para la cesta, pero esta comprobación no reserva stock ni evita por sí sola pedidos simultáneos que consuman la misma existencia. Para ese paso hace falta una transacción de inventario en un backend confiable, junto con la reversión al cancelar.
