const {test} = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {assertFails, assertSucceeds, initializeTestEnvironment} = require('@firebase/rules-unit-testing');
const {doc, getDoc, runTransaction, serverTimestamp, setDoc, updateDoc, deleteDoc} = require('firebase/firestore');

test('solo administración versiona la tarifa y el pedido usa la versión vigente', async () => {
  const env = await initializeTestEnvironment({
    projectId: 'demo-yalazas-tarifa',
    firestore: {rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8')},
  });
  try {
    await env.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      await setDoc(doc(db, 'users', 'admin'), {uid: 'admin', role: 'ADMINISTRADOR', active: true});
      await setDoc(doc(db, 'users', 'cliente'), {uid: 'cliente', role: 'CLIENTE', active: true});
    });
    const admin = env.authenticatedContext('admin').firestore();
    const cliente = env.authenticatedContext('cliente').firestore();
    const ref = db => doc(db, 'settings', 'deliveryTariff');
    const version = (n, baseCents) => ({version: n, baseCents, includedMeters: 2000,
      extraKmCents: 200, updatedAt: serverTimestamp(), updatedBy: 'admin'});

    await assertFails(setDoc(ref(cliente), version(1, 400)));
    await assertFails(setDoc(ref(admin), version(1, 400)));
    await assertSucceeds(runTransaction(admin, async tx => {
      tx.set(ref(admin), version(1, 400));
      tx.set(doc(admin, 'delivery_tariff_versions', '1'), version(1, 400));
    }));
    await assertSucceeds(getDoc(ref(cliente)));
    await assertFails(updateDoc(doc(admin, 'delivery_tariff_versions', '1'), {baseCents: 1}));
    await assertFails(deleteDoc(doc(admin, 'delivery_tariff_versions', '1')));
    await assertFails(updateDoc(ref(admin), version(3, 500)));
    await assertFails(updateDoc(ref(admin), version(2, 500)));

    const pedido = (id, fee, tarifaVersion) => ({
      userId: 'cliente', customerName: 'Cliente', phone: '999999999', address: 'Domicilio',
      total: 20 + fee, items: [{productId: 'bbq', name: 'Alitas', quantity: 1, subtotal: 20}],
      status: 'PENDIENTE', paymentMethod: 'EFECTIVO', paymentAmount: 20 + fee,
      paymentChange: 0, paymentStatus: 'PENDIENTE', isDelivery: true,
      deliveryDistanceKm: 2.1, deliveryFee: fee, deliveryTariffVersion: tarifaVersion,
      timestamp: serverTimestamp(), deliveryDriverId: '', deliveryDriverName: '',
      deliveryDriverPhone: '', routeOrderIndex: 0,
    });
    await assertFails(setDoc(doc(cliente, 'orders', 'tarifa-antigua'), pedido('a', 3, 0)));
    await assertFails(setDoc(doc(cliente, 'orders', 'importe-falso'), pedido('b', 3, 1)));
    await assertSucceeds(setDoc(doc(cliente, 'orders', 'tarifa-vigente'), pedido('c', 6, 1)));

    await assertSucceeds(runTransaction(admin, async tx => {
      await tx.get(ref(admin));
      tx.set(ref(admin), version(2, 500));
      tx.set(doc(admin, 'delivery_tariff_versions', '2'), version(2, 500));
    }));
    await assertFails(setDoc(doc(cliente, 'orders', 'revision-anterior'), pedido('d', 6, 1)));
    await assertSucceeds(setDoc(doc(cliente, 'orders', 'revision-nueva'), pedido('e', 7, 2)));
  } finally { await env.cleanup(); }
});
