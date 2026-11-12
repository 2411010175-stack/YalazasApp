const test = require('node:test');
const assert = require('node:assert/strict');
const {randomUUID} = require('node:crypto');
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
const {createOrderService} = require('../lib/orderService');

test('Firestore real de ensayo conserva stock y pedido bajo concurrencia',
  {skip: !process.env.FIRESTORE_EMULATOR_HOST, timeout: 60000}, async () => {
    const app = initializeApp({projectId: 'demo-yalazas-orderapi'}, `test-${randomUUID()}`);
    const db = getFirestore(app);
    await Promise.all([
      db.collection('users').doc('cliente').set({role: 'CLIENTE', active: true, name: 'Cliente'}),
      db.collection('products').doc('bbq').set({name: 'Alitas BBQ', price: 20, isAvailable: true, allowsSauces: false}),
      db.collection('recipes').doc('bbq').set({ingredients: [{ingredientId: 'alitas', quantity: 6}],
        sauceSlotsMl: [], sauceIngredients: {}}),
      db.collection('ingredients').doc('alitas').set({name: 'Alitas', unit: 'u', stock: 60, pauseThreshold: 0}),
    ]);
    const service = createOrderService(db, {FieldValue});
    const requests = Array.from({length: 20}, () => ({requestId: randomUUID().replaceAll('-', ''),
      items: [{productId: 'bbq', quantity: 1, selectedSauces: []}],
      serviceType: 'RETIRO', isDelivery: false, address: 'Recojo',
      paymentMethod: 'YAPE', expectedTotal: 20}));
    const results = await Promise.allSettled(requests.map(r => service.create('cliente', r)));
    const accepted = results.filter(r => r.status === 'fulfilled').length;
    const stock = (await db.collection('ingredients').doc('alitas').get()).get('stock');
    const saved = (await db.collection('orders').get()).size;
    assert.ok(accepted <= 10);
    assert.equal(stock, 60 - accepted * 6);
    assert.equal(saved, accepted);
    assert.ok(stock >= 0);
  });
