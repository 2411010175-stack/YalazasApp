# Tarifa de entrega versionada

La política **Strategy** `TarifaPorDistancia` recibe la configuración vigente de
`settings/deliveryTariff`. Los importes se guardan en céntimos y la distancia incluida
en metros. La fórmula es: tarifa base + un recargo por cada kilómetro adicional iniciado.
Si el documento aún no existe, la versión 0 conserva S/ 3 hasta 1,5 km y S/ 1 por
kilómetro adicional. El retiro y las comandas de mesa cuestan S/ 0 de envío.

Administración puede editar los tres valores en **Ajustes → Tarifa de reparto**, tanto
en Android como en AppWebAdmin. Cada guardado incrementa la versión en una transacción
y crea `delivery_tariff_versions/{version}` con los mismos importes, fecha y UID del
administrador. Las reglas impiden escrituras de otros roles, saltos de versión y
cambios sin la entrada histórica correspondiente.

La confirmación del cliente escucha el documento vigente. Muestra el número de versión
y no envía pedidos de reparto mientras solo dispone de una copia local sin confirmar.
El pedido conserva `deliveryTariffVersion` y `deliveryFee`. Al registrar directamente
en Firestore, el repositorio relee la tarifa en una transacción y las reglas comprueban
que versión e importe correspondan a la distancia declarada. Si se activa `OrderApi`,
el servidor calcula de nuevo la distancia desde las coordenadas, lee la tarifa en la
misma transacción que el inventario y rechaza un total anterior.

## Activación y comprobación

1. Publicar `firestore.rules` en `yalazas-5fc9a` **antes** de distribuir la APK o el
   panel web actualizados. Las reglas publicadas anteriormente no permiten leer
   `settings/deliveryTariff`; la confirmación nueva esperaría sin poder cobrar delivery.
2. Abrir Ajustes como administrador. Verificar versión 0 y valores 3,00 / 1,5 / 1,00.
   Cambiar, por ejemplo, la base a S/ 4,00 y guardar. En Firestore deben existir
   `settings/deliveryTariff.version = 1` y `delivery_tariff_versions/1`.
   Distribuir primero los clientes actualizados: una APK antigua seguirá calculando
   la versión 0 y su pedido será rechazado cuando se publique la versión 1.
3. En un cliente con distancia hasta 1,5 km debe verse S/ 4,00; apenas por encima,
   S/ 5,00. Retiro y mesa conservan S/ 0. Un pedido nuevo debe guardar
   `deliveryTariffVersion = 1` y el importe mostrado.
4. Intentar confirmar con la versión anterior: debe rechazarse sin crear el pedido.
   Repetir desde el panel web para comprobar que ambos actualizan la misma versión.
5. Ejecutar las pruebas del emulador de reglas y `npm test` en `OrderApi`. La versión
   alternativa `OrderApi/firestore.atomic.rules` conserva estos permisos para cuando
   se active el servidor de pedidos.

La validación directa en reglas usa la distancia informada por el teléfono; Firestore
Rules no puede comprobar que esa distancia corresponda a las coordenadas. La defensa
completa es `OrderApi`, que todavía requiere un servidor HTTPS para entrar en servicio.
