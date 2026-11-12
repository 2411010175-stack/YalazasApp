const test = require('node:test');
const assert = require('node:assert/strict');
const {randomUUID} = require('node:crypto');
const {createOrderService} = require('../lib/orderService');

function fakeDatabase(initial) {
  const records = new Map(Object.entries(initial));
  let queue = Promise.resolve();
  const ref = (collection, id) => ({key: `${collection}/${id}`, id});
  const db = {
    collection: name => ({doc: id => ref(name, id || randomUUID())}),
    runTransaction(fn) {
      const job = queue.then(async () => {
        const writes = [];
        const tx = {
          async get(reference) {
            const value = records.get(reference.key);
            return {id: reference.id, exists: value !== undefined,
              data: () => value, get: field => value?.[field]};
          },
          update(reference, changes) { writes.push(['update', reference.key, changes]); },
          create(reference, value) { writes.push(['create', reference.key, value]); },
          set(reference, value) { writes.push(['set', reference.key, value]); },
        };
        const result = await fn(tx);
        for (const [operation, key, value] of writes) {
          if (operation === 'create' && records.has(key)) throw new Error('Pedido duplicado');
          if (operation === 'update') {
            const current = records.get(key);
            records.set(key, {...current, ...Object.fromEntries(Object.entries(value).map(([field, next]) =>
              [field, next?.increment === undefined ? next : current[field] + next.increment]))});
          } else records.set(key, value);
        }
        return result;
      });
      queue = job.catch(() => {});
      return job;
    },
  };
  return {db, records};
}

const firestore = {FieldValue: {serverTimestamp: () => new Date(), increment: n => ({increment: n})}};
const product = {name: 'Alitas BBQ', price: 20, isAvailable: true, allowsSauces: false};
const recipe = {ingredients: [{ingredientId: 'alitas', quantity: 6}], sauceSlotsMl: [], sauceIngredients: {}};
const initial = () => ({
  'users/cliente': {role: 'CLIENTE', active: true, name: 'Cliente'},
  'products/bbq': product, 'recipes/bbq': recipe,
  'ingredients/alitas': {name: 'Alitas', unit: 'u', stock: 60, pauseThreshold: 0},
});
const order = () => ({requestId: randomUUID().replaceAll('-', ''), items: [{productId: 'bbq', quantity: 1, selectedSauces: []}],
  serviceType: 'RETIRO', isDelivery: false, address: 'Recoge en el local',
  paymentMethod: 'YAPE', expectedTotal: 20});

test('20 pedidos simultáneos aceptan solo las 10 porciones existentes', async () => {
  const {db, records} = fakeDatabase(initial());
  const service = createOrderService(db, firestore);
  const results = await Promise.allSettled(Array.from({length: 20}, () => service.create('cliente', order())));
  assert.equal(results.filter(x => x.status === 'fulfilled').length, 10);
  assert.equal(results.filter(x => x.status === 'rejected').length, 10);
  assert.equal(records.get('ingredients/alitas').stock, 0);
  assert.equal([...records.keys()].filter(x => x.startsWith('orders/')).length, 10);
});

test('repetir la solicitud no duplica el pedido ni descuenta dos veces', async () => {
  const {db, records} = fakeDatabase(initial());
  const service = createOrderService(db, firestore), request = order();
  const first = await service.create('cliente', request);
  const retry = await service.create('cliente', request);
  assert.equal(first.id, retry.id);
  assert.equal(retry.reused, true);
  assert.equal(records.get('ingredients/alitas').stock, 54);
});

test('cancelar un pedido pendiente devuelve los insumos una sola vez', async () => {
  const {db, records} = fakeDatabase(initial());
  const service = createOrderService(db, firestore);
  const created = await service.create('cliente', order());
  await service.cancel('cliente', created.id);
  const retry = await service.cancel('cliente', created.id);
  assert.equal(retry.reused, true);
  assert.equal(records.get('ingredients/alitas').stock, 60);
  assert.equal(records.get(`orders/${created.id}`).inventoryRestored, true);
});

test('cancelar después de empezar la preparación no devuelve ingredientes consumidos', async () => {
  const {db, records} = fakeDatabase(initial());
  const service = createOrderService(db, firestore);
  records.set('users/admin', {role: 'ADMINISTRADOR', active: true});
  const created = await service.create('cliente', order());
  records.set(`orders/${created.id}`, {...records.get(`orders/${created.id}`), status: 'EN_PROCESO'});
  await service.cancel('admin', created.id);
  assert.equal(records.get('ingredients/alitas').stock, 54);
  assert.equal(records.get(`orders/${created.id}`).inventoryRestored, false);
});

test('salsas elegidas se suman a la demanda total del carrito', async () => {
  const data = initial();
  data['products/bbq'] = {...product, allowsSauces: true, maxSauces: 1,
    availableSauces: ['BBQ Clásica']};
  data['recipes/bbq'] = {...recipe, sauceSlotsMl: [25], sauceIngredients: {'BBQ Clásica': 'salsa'}};
  data['ingredients/salsa'] = {name: 'Salsa BBQ', unit: 'ml', stock: 40, pauseThreshold: 0};
  const {db, records} = fakeDatabase(data);
  const service = createOrderService(db, firestore), request = order();
  request.items[0].selectedSauces = ['BBQ Clásica'];
  await service.create('cliente', request);
  assert.equal(records.get('ingredients/salsa').stock, 15);
  const second = order();
  second.items[0].selectedSauces = ['BBQ Clásica'];
  await assert.rejects(service.create('cliente', second), /no alcanza/);
  assert.equal(records.get('ingredients/salsa').stock, 15);
});

test('un precio cambiado rechaza el pedido sin tocar existencias', async () => {
  const {db, records} = fakeDatabase(initial());
  const service = createOrderService(db, firestore), request = order();
  request.expectedTotal = 19;
  await assert.rejects(service.create('cliente', request), /precio o la tarifa cambió/);
  assert.equal(records.get('ingredients/alitas').stock, 60);
  assert.equal(records.has(`orders/${request.requestId}`), false);
});

test('el servidor cobra la tarifa versionada y rechaza una confirmación antigua', async () => {
  const data = initial();
  data['settings/deliveryTariff'] = {version: 1, baseCents: 400,
    includedMeters: 2000, extraKmCents: 200};
  const {db, records} = fakeDatabase(data);
  const service = createOrderService(db, firestore);
  const solicitar = (version, total) => ({...order(),
    requestId: randomUUID().replaceAll('-', ''), serviceType: 'DELIVERY', isDelivery: true,
    address: 'Casa', latitude: -13.042851, longitude: -76.352075,
    deliveryTariffVersion: version, expectedTotal: total});

  await assert.rejects(service.create('cliente', solicitar(0, 23)), /tarifa cambió/);
  await assert.rejects(service.create('cliente', solicitar(1, 25)), /precio o la tarifa cambió/);
  const creado = await service.create('cliente', solicitar(1, 26));
  assert.equal(records.get(`orders/${creado.id}`).deliveryFee, 6);
  assert.equal(records.get(`orders/${creado.id}`).deliveryTariffVersion, 1);

  records.set('settings/deliveryTariff', {...data['settings/deliveryTariff'], version: 2, baseCents: 500});
  await assert.rejects(service.create('cliente', solicitar(1, 26)), /tarifa cambió/);
  const nuevo = await service.create('cliente', solicitar(2, 27));
  assert.equal(records.get(`orders/${nuevo.id}`).deliveryFee, 7);
  assert.equal(records.get('ingredients/alitas').stock, 48);
});
