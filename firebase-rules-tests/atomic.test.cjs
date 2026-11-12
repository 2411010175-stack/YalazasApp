const {test} = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {assertFails, assertSucceeds, initializeTestEnvironment} = require('@firebase/rules-unit-testing');
const {doc, getDoc, runTransaction, serverTimestamp, setDoc, updateDoc} = require('firebase/firestore');

test('las reglas de activación cierran el pedido directo y la cancelación sin devolución', async () => {
  const env = await initializeTestEnvironment({
    projectId: 'demo-yalazas-atomic',
    firestore: {rules: fs.readFileSync(path.join(__dirname, '..', 'OrderApi', 'firestore.atomic.rules'), 'utf8')},
  });
  try {
    await env.withSecurityRulesDisabled(async context => {
      const db = context.firestore();
      await setDoc(doc(db, 'users', 'cliente'), {uid: 'cliente', role: 'CLIENTE', active: true});
      await setDoc(doc(db, 'users', 'cocinero'), {uid: 'cocinero', role: 'COCINERO', active: true});
      await setDoc(doc(db, 'users', 'admin'), {uid: 'admin', role: 'ADMINISTRADOR', active: true});
      await setDoc(doc(db, 'orders', 'pedido-gestionado'), {
        userId: 'cliente', status: 'PENDIENTE', serviceType: 'DELIVERY', inventoryVersion: 1,
      });
    });
    const db = env.authenticatedContext('cliente').firestore();
    const admin = env.authenticatedContext('admin').firestore();
    await assertSucceeds(runTransaction(admin, async tx => {
      const tarifa = {version: 1, baseCents: 400, includedMeters: 1500,
        extraKmCents: 100, updatedAt: serverTimestamp(), updatedBy: 'admin'};
      tx.set(doc(admin, 'settings', 'deliveryTariff'), tarifa);
      tx.set(doc(admin, 'delivery_tariff_versions', '1'), tarifa);
    }));
    await assertSucceeds(getDoc(doc(db, 'settings', 'deliveryTariff')));
    await assertFails(setDoc(doc(db, 'settings', 'deliveryTariff'), {
      version: 2, baseCents: 0, includedMeters: 1500, extraKmCents: 0,
      updatedAt: serverTimestamp(), updatedBy: 'cliente'
    }));
    await assertFails(setDoc(doc(db, 'orders', 'nuevo-directo'), {
      userId: 'cliente', status: 'PENDIENTE', items: [{productId: 'bbq', quantity: 1}],
    }));
    await assertFails(updateDoc(doc(db, 'orders', 'pedido-gestionado'), {
      status: 'CANCELADO', updatedAt: Date.now(),
    }));
    const cocina = env.authenticatedContext('cocinero').firestore();
    await assertSucceeds(updateDoc(doc(cocina, 'orders', 'pedido-gestionado'), {
      status: 'EN_PROCESO', updatedAt: Date.now(),
    }));
  } finally { await env.cleanup(); }
});
