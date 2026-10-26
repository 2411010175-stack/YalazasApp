const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment
} = require('@firebase/rules-unit-testing');
const {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where
} = require('firebase/firestore');
const { getMetadata, ref, uploadBytes } = require('firebase/storage');

const projectRoot = path.resolve(__dirname, '..');

function order(userId, isDelivery = true) {
  return {
    userId,
    customerName: 'Cliente de prueba',
    phone: '999999999',
    address: 'Dirección de prueba',
    total: 20,
    items: [{ productId: 'p1', name: 'Alitas', quantity: 1, subtotal: 20 }],
    status: 'PENDIENTE',
    paymentMethod: 'EFECTIVO',
    paymentAmount: 20,
    paymentChange: 0,
    paymentStatus: 'PENDIENTE',
    isDelivery,
    deliveryDistanceKm: isDelivery ? 1 : 0,
    deliveryFee: isDelivery ? 3 : 0,
    timestamp: serverTimestamp(),
    deliveryDriverId: '',
    deliveryDriverName: '',
    deliveryDriverPhone: '',
    routeOrderIndex: 0
  };
}

test('permisos de perfiles, productos, promociones y pedidos', async () => {
  const env = await initializeTestEnvironment({
    projectId: 'demo-yalazas',
    firestore: {
      rules: fs.readFileSync(path.join(projectRoot, 'firestore.rules'), 'utf8')
    },
    storage: {
      rules: fs.readFileSync(path.join(projectRoot, 'storage.rules'), 'utf8')
    }
  });

  try {
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      for (const [uid, role, active] of [
        ['admin', 'ADMINISTRADOR', true],
        ['customer', 'CLIENTE', true],
        ['other', 'CLIENTE', true],
        ['driver', 'REPARTIDOR', true],
        ['driver2', 'REPARTIDOR', true],
        ['blocked', 'CLIENTE', false]
      ]) {
        await setDoc(doc(db, 'users', uid), { uid, role, active, name: uid });
      }
      await setDoc(doc(db, 'products', 'p1'), { name: 'Alitas', price: 20 });
      await setDoc(doc(db, 'orders', 'existing'), {
        ...order('customer'), timestamp: new Date()
      });
      await setDoc(doc(db, 'orders', 'pickup'), {
        ...order('other', false), timestamp: new Date()
      });
    });

    const admin = env.authenticatedContext('admin').firestore();
    const customer = env.authenticatedContext('customer').firestore();
    const other = env.authenticatedContext('other').firestore();
    const driver = env.authenticatedContext('driver').firestore();
    const driver2 = env.authenticatedContext('driver2').firestore();
    const blocked = env.authenticatedContext('blocked').firestore();
    const anonymous = env.unauthenticatedContext().firestore();

    await assertSucceeds(getDoc(doc(anonymous, 'products', 'p1')));
    await assertFails(setDoc(doc(customer, 'products', 'p2'), { name: 'Falso' }));
    await assertSucceeds(setDoc(doc(admin, 'products', 'p2'), { name: 'Oferta' }));

    const promocion = { productId: 'p1', offerPrice: 17, offerTag: 'Oferta del día', active: true };
    await assertSucceeds(setDoc(doc(admin, 'promotions', 'p1'), promocion));
    await assertSucceeds(getDoc(doc(anonymous, 'promotions', 'p1')));
    await assertFails(setDoc(doc(customer, 'promotions', 'p1'), { ...promocion, offerPrice: 5 }));
    await assertFails(setDoc(doc(admin, 'promotions', 'p1'), { ...promocion, offerPrice: 20 }));
    assert.equal((await getDoc(doc(admin, 'products', 'p1'))).data().price, 20);
    await assertSucceeds(deleteDoc(doc(admin, 'promotions', 'p1')));

    await assertSucceeds(getDocs(collection(admin, 'users')));
    await assertFails(getDocs(collection(customer, 'users')));
    await assertFails(updateDoc(doc(customer, 'users', 'customer'), { role: 'ADMINISTRADOR' }));
    const guest = env.authenticatedContext('guest').firestore();
    const guestProfile = {
      uid: 'guest', email: '', role: 'CLIENTE', active: true,
      name: 'Invitado', phone: '', address: '', reference: '',
      latitude: 0, longitude: 0, createdAt: 1, updatedAt: 1
    };
    await assertSucceeds(setDoc(doc(guest, 'users', 'guest'), guestProfile));
    await assertSucceeds(setDoc(doc(guest, 'users', 'guest'), {
      ...guestProfile, name: 'Cliente', phone: '999999999', updatedAt: 2
    }));
    await assertFails(setDoc(doc(env.authenticatedContext('intruder').firestore(), 'users', 'intruder'), {
      uid: 'intruder', role: 'ADMINISTRADOR', active: true
    }));

    await assertSucceeds(getDoc(doc(customer, 'orders', 'existing')));
    await assertFails(getDoc(doc(other, 'orders', 'existing')));
    await assertSucceeds(getDocs(query(collection(customer, 'orders'), where('userId', '==', 'customer'))));
    await assertFails(getDocs(collection(customer, 'orders')));
    await assertSucceeds(getDocs(query(collection(driver, 'orders'), where('isDelivery', '==', true))));
    await assertFails(getDocs(collection(driver, 'orders')));
    await assertFails(getDoc(doc(driver, 'orders', 'pickup')));
    await assertSucceeds(updateDoc(doc(admin, 'users', 'other'), {
      role: 'REPARTIDOR', active: true, updatedAt: Date.now()
    }));

    await assertSucceeds(setDoc(doc(customer, 'orders', 'new'), order('customer')));
    await assertFails(setDoc(doc(customer, 'orders', 'forged'), {
      ...order('other'), status: 'ENTREGADO'
    }));
    await assertFails(setDoc(doc(blocked, 'orders', 'blocked-order'), order('blocked')));
    await assertSucceeds(updateDoc(doc(customer, 'orders', 'new'), {
      status: 'CANCELADO', updatedAt: Date.now()
    }));
    await assertFails(updateDoc(doc(customer, 'orders', 'new'), { status: 'PENDIENTE' }));
    await assertFails(setDoc(doc(customer, 'auditoria', 'fake'), { accion: 'CAMBIO_ROL' }));

    await assertFails(updateDoc(doc(admin, 'orders', 'existing'), {
      status: 'ENTREGADO', updatedAt: Date.now()
    }));
    await assertSucceeds(updateDoc(doc(admin, 'orders', 'existing'), {
      status: 'EN_PROCESO', updatedAt: Date.now()
    }));
    await assertFails(updateDoc(doc(customer, 'orders', 'existing'), {
      status: 'CANCELADO', updatedAt: Date.now()
    }));
    await assertSucceeds(updateDoc(doc(driver, 'orders', 'existing'), {
      status: 'EN_CAMINO', deliveryDriverId: 'driver',
      deliveryDriverName: 'driver', deliveryDriverPhone: '', routeOrderIndex: 1
    }));
    await assertFails(updateDoc(doc(driver2, 'orders', 'existing'), {
      deliveryDriverId: 'driver2'
    }));
    await assertSucceeds(updateDoc(doc(driver, 'orders', 'existing'), {
      status: 'ENTREGADO', paymentStatus: 'PAGADO'
    }));

    const adminImage = ref(env.authenticatedContext('admin').storage(), 'products/test.jpg');
    const customerImage = ref(env.authenticatedContext('customer').storage(), 'products/forged.jpg');
    await assertSucceeds(uploadBytes(adminImage, new Uint8Array([1, 2, 3]), {
      contentType: 'image/jpeg'
    }));
    await assertSucceeds(getMetadata(ref(env.authenticatedContext('customer').storage(), 'products/test.jpg')));
    await assertFails(uploadBytes(customerImage, new Uint8Array([1]), {
      contentType: 'image/jpeg'
    }));
    await assertFails(uploadBytes(ref(env.authenticatedContext('admin').storage(), 'products/not-image.txt'),
      new Uint8Array([1]), { contentType: 'text/plain' }));
  } finally {
    await env.cleanup();
  }
});
