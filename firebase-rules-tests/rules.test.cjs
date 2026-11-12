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
  Bytes,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
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
        ['cook', 'COCINERO', true],
        ['waiter', 'MESERO', true],
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
    const cook = env.authenticatedContext('cook').firestore();
    const waiter = env.authenticatedContext('waiter').firestore();
    const blocked = env.authenticatedContext('blocked').firestore();
    const anonymous = env.unauthenticatedContext().firestore();

    await assertSucceeds(getDoc(doc(anonymous, 'products', 'p1')));
    await assertFails(setDoc(doc(customer, 'products', 'p2'), { name: 'Falso' }));
    await assertSucceeds(setDoc(doc(admin, 'products', 'p2'), { name: 'Oferta' }));
    await assertFails(setDoc(doc(customer, 'catalog_cards', 'p2'), { name: 'Falso' }));
    await assertSucceeds(setDoc(doc(admin, 'catalog_cards', 'p2'), { name: 'Oferta' }));
    await assertSucceeds(getDoc(doc(anonymous, 'catalog_cards', 'p2')));

    // Solo administración cambia las mesas; el mesero conserva las cinco iniciales hasta configurarlas.
    const salon = (db) => doc(db, 'settings', 'salon');
    const comanda = (numero) => ({
      ...order('waiter', false), serviceType: 'MESA', tableNumber: numero,
      paymentMethod: 'POR_DEFINIR', paymentAmount: 0, paymentChange: 0,
      waiterId: 'waiter', waiterName: 'Mesero', paymentVerifiedBy: ''
    });
    await assertSucceeds(getDoc(salon(waiter)));
    await assertFails(getDoc(salon(customer)));
    await assertSucceeds(setDoc(doc(waiter, 'orders', 'mesa-inicial'), comanda(1)));
    await assertFails(setDoc(doc(waiter, 'orders', 'mesa-inexistente'), comanda(6)));
    await assertSucceeds(setDoc(salon(admin), {
      tableNumbers: [1, 2, 6], updatedAt: serverTimestamp()
    }));
    await assertFails(updateDoc(salon(waiter), { tableNumbers: [1, 2, 3] }));
    await assertFails(updateDoc(salon(customer), { tableNumbers: [1, 2, 3] }));
    await assertSucceeds(getDoc(salon(waiter)));
    await assertFails(setDoc(doc(waiter, 'orders', 'mesa-retirada'), comanda(3)));
    await assertSucceeds(setDoc(doc(waiter, 'orders', 'mesa-nueva'), comanda(6)));
    await assertFails(deleteDoc(salon(admin)));

    const promocion = { productId: 'p1', offerPrice: 17, offerTag: 'Oferta del día', active: true };
    await assertSucceeds(setDoc(doc(admin, 'promotions', 'p1'), promocion));
    await assertSucceeds(getDoc(doc(anonymous, 'promotions', 'p1')));
    await assertFails(setDoc(doc(customer, 'promotions', 'p1'), { ...promocion, offerPrice: 5 }));
    await assertFails(setDoc(doc(admin, 'promotions', 'p1'), { ...promocion, offerPrice: 20 }));
    assert.equal((await getDoc(doc(admin, 'products', 'p1'))).data().price, 20);
    await assertSucceeds(deleteDoc(doc(admin, 'promotions', 'p1')));

    const insumo = {
      id: 'salsa_bbq', name: 'Salsa BBQ', unit: 'ml', stock: 1000,
      lowThreshold: 1000, pauseThreshold: 200
    };
    await assertSucceeds(setDoc(doc(admin, 'ingredients', 'salsa_bbq'), insumo));
    await assertSucceeds(getDoc(doc(customer, 'ingredients', 'salsa_bbq')));
    await assertFails(getDoc(doc(anonymous, 'ingredients', 'salsa_bbq')));
    await assertFails(updateDoc(doc(customer, 'ingredients', 'salsa_bbq'), { stock: 500 }));
    await assertFails(setDoc(doc(admin, 'ingredients', 'falso'), { ...insumo, stock: -1 }));
    const receta = { productId: 'p1', ingredients: [{ ingredientId: 'salsa_bbq', quantity: 50 }] };
    await assertSucceeds(setDoc(doc(admin, 'recipes', 'p1'), receta));
    await assertSucceeds(setDoc(doc(admin, 'recipes', 'p1'), {
      productId: 'p1',
      ingredients: [{ ingredientId: 'salsa_bbq', quantity: 25 }],
      sauceSlotsMl: [25, 25],
      sauceIngredients: { 'BBQ Clásica': 'salsa_bbq' }
    }));
    await assertSucceeds(getDoc(doc(customer, 'recipes', 'p1')));
    await assertFails(getDoc(doc(anonymous, 'recipes', 'p1')));
    await assertFails(setDoc(doc(customer, 'recipes', 'p1'), receta));

    await assertSucceeds(getDocs(collection(admin, 'users')));
    await assertFails(getDocs(collection(customer, 'users')));
    await assertFails(updateDoc(doc(customer, 'users', 'customer'), { role: 'ADMINISTRADOR' }));
    // El vehículo pertenece al perfil del repartidor, pero solo administración lo modifica.
    await assertSucceeds(updateDoc(doc(admin, 'users', 'driver'), {
      vehicleType: 'Moto', vehiclePlate: 'ABC-123', vehicleModel: 'Honda 150', updatedAt: Date.now()
    }));
    await assertFails(updateDoc(doc(driver, 'users', 'driver'), { vehiclePlate: 'FALSA-1' }));
    await assertSucceeds(updateDoc(doc(driver, 'users', 'driver'), { name: 'Repartidor actualizado' }));
    await assertFails(updateDoc(doc(admin, 'users', 'customer'), {
      vehicleType: 'Moto', vehiclePlate: 'ABC-123', vehicleModel: ''
    }));
    await assertFails(updateDoc(doc(admin, 'users', 'driver'), {
      vehicleType: '', vehiclePlate: '', vehicleModel: 'Sin placa'
    }));
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
    await assertFails(setDoc(doc(env.authenticatedContext('fake-driver').firestore(), 'users', 'fake-driver'), {
      uid: 'fake-driver', role: 'CLIENTE', active: true,
      vehicleType: 'Moto', vehiclePlate: 'FALSA-1', vehicleModel: ''
    }));

    await assertSucceeds(getDoc(doc(customer, 'orders', 'existing')));
    await assertFails(getDoc(doc(other, 'orders', 'existing')));
    await assertSucceeds(getDocs(query(collection(customer, 'orders'), where('userId', '==', 'customer'))));
    await assertFails(getDocs(collection(customer, 'orders')));
    await assertSucceeds(getDocs(query(collection(driver, 'orders'), where('isDelivery', '==', true))));
    await assertFails(getDocs(collection(driver, 'orders')));
    await assertFails(getDoc(doc(driver, 'orders', 'pickup')));
    await assertSucceeds(getDocs(query(collection(cook, 'orders'), where('status', 'in', ['PENDIENTE', 'EN_PROCESO', 'LISTO_PARA_ENTREGA']))));
    await assertFails(getDocs(collection(cook, 'orders')));
    await assertSucceeds(updateDoc(doc(admin, 'users', 'other'), {
      role: 'REPARTIDOR', active: true, updatedAt: Date.now()
    }));

    await assertSucceeds(setDoc(doc(customer, 'orders', 'new'), order('customer')));
    const ventaPOS = {
      ...order('admin', false), serviceType: 'RETIRO', paymentMethod: 'TARJETA',
      paymentStatus: 'PAGADO', paymentVerifiedBy: 'admin', paymentVerifiedAt: serverTimestamp()
    };
    await assertSucceeds(setDoc(doc(admin, 'orders', 'pos-web'), ventaPOS));
    await assertFails(setDoc(doc(customer, 'orders', 'pos-falso'), {
      ...ventaPOS, userId: 'customer', paymentVerifiedBy: 'customer'
    }));
    await assertFails(setDoc(doc(admin, 'orders', 'pos-sin-validacion'), {
      ...ventaPOS, paymentVerifiedAt: new Date(0)
    }));
    await assertFails(setDoc(doc(admin, 'orders', 'pos-monto-falso'), {
      ...ventaPOS, paymentAmount: 5
    }));
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
    await assertFails(updateDoc(doc(driver, 'orders', 'existing'), {
      status: 'EN_CAMINO', deliveryDriverId: 'driver',
      deliveryDriverName: 'driver', deliveryDriverPhone: '', routeOrderIndex: 1
    }));
    await assertFails(updateDoc(doc(customer, 'orders', 'existing'), {
      status: 'EN_PROCESO', updatedAt: Date.now()
    }));
    await assertSucceeds(updateDoc(doc(cook, 'orders', 'existing'), {
      status: 'EN_PROCESO', updatedAt: Date.now()
    }));
    await assertFails(updateDoc(doc(customer, 'orders', 'existing'), {
      status: 'CANCELADO', updatedAt: Date.now()
    }));
    await assertFails(updateDoc(doc(driver, 'orders', 'existing'), {
      status: 'EN_CAMINO', deliveryDriverId: 'driver',
      deliveryDriverName: 'driver', deliveryDriverPhone: '', routeOrderIndex: 1
    }));
    await assertSucceeds(updateDoc(doc(cook, 'orders', 'existing'), {
      status: 'LISTO_PARA_ENTREGA', updatedAt: Date.now()
    }));
    await assertSucceeds(updateDoc(doc(cook, 'orders', 'existing'), {
      status: 'EN_PROCESO', updatedAt: Date.now()
    }));
    await assertSucceeds(updateDoc(doc(cook, 'orders', 'existing'), {
      status: 'LISTO_PARA_ENTREGA', updatedAt: Date.now()
    }));
    await assertFails(updateDoc(doc(cook, 'orders', 'existing'), {
      status: 'EN_CAMINO', updatedAt: Date.now()
    }));
    await assertSucceeds(updateDoc(doc(driver, 'orders', 'existing'), {
      status: 'EN_CAMINO', deliveryDriverId: 'driver',
      deliveryDriverName: 'driver', deliveryDriverPhone: '', routeOrderIndex: 1
    }));
    await assertFails(updateDoc(doc(cook, 'orders', 'existing'), {
      status: 'EN_PROCESO', updatedAt: Date.now()
    }));
    await assertFails(updateDoc(doc(driver2, 'orders', 'existing'), {
      deliveryDriverId: 'driver2'
    }));

    // Solo el repartidor asignado publica la posición; solo el dueño del pedido la ve.
    const ubicacion = () => ({
      latitude: -13.0618, longitude: -76.3520,
      accuracyMeters: 12, updatedAt: serverTimestamp()
    });
    const rutaDriver = doc(driver, 'orders', 'existing', 'tracking', 'actual');
    await assertFails(setDoc(doc(driver, 'orders', 'new', 'tracking', 'actual'), ubicacion()));
    await assertFails(setDoc(doc(driver2, 'orders', 'existing', 'tracking', 'actual'), ubicacion()));
    await assertSucceeds(setDoc(rutaDriver, ubicacion()));
    await assertSucceeds(getDoc(doc(customer, 'orders', 'existing', 'tracking', 'actual')));
    await assertFails(getDoc(doc(other, 'orders', 'existing', 'tracking', 'actual')));
    await assertFails(setDoc(rutaDriver, { ...ubicacion(), latitude: 91 }));
    await assertFails(setDoc(rutaDriver, { ...ubicacion(), extra: 'no permitido' }));

    await assertFails(updateDoc(doc(driver, 'orders', 'existing'), {
      status: 'ENTREGADO', paymentStatus: 'PAGADO'
    }));
    await assertSucceeds(updateDoc(doc(driver, 'orders', 'existing'), {
      status: 'ENTREGADO', paymentStatus: 'PAGADO',
      paymentVerifiedBy: 'driver', paymentVerifiedAt: serverTimestamp()
    }));
    await assertFails(getDoc(doc(customer, 'orders', 'existing', 'tracking', 'actual')));
    await assertFails(setDoc(rutaDriver, ubicacion()));

    // El comprobante es legible solo por dueño y administración. Ni el repartidor puede aprobarse un pago digital.
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'orders', 'digital'), {
        ...order('customer'), paymentMethod: 'YAPE', status: 'EN_CAMINO',
        deliveryDriverId: 'driver', timestamp: new Date()
      });
    });
    const comprobante = (db) => doc(db, 'orders', 'digital', 'privado', 'comprobante');
    const imagen = Bytes.fromUint8Array(new Uint8Array([0xff, 0xd8, 0xff, 0xd9]));
    const datosComprobante = () => ({
      imagen, mime: 'image/jpeg', subidoPor: 'customer', subidoEn: serverTimestamp()
    });
    await assertFails(setDoc(comprobante(customer), datosComprobante()));
    await assertFails(updateDoc(doc(customer, 'orders', 'digital'), { paymentProofStatus: 'EN_REVISION' }));
    async function enviarComprobante() {
      const lote = writeBatch(customer);
      lote.set(comprobante(customer), datosComprobante());
      lote.update(doc(customer, 'orders', 'digital'), { paymentProofStatus: 'EN_REVISION' });
      return lote.commit();
    }
    await assertSucceeds(enviarComprobante());
    await assertSucceeds(getDoc(comprobante(customer)));
    await assertSucceeds(getDoc(comprobante(admin)));
    await assertFails(getDoc(comprobante(driver)));
    await assertFails(getDoc(comprobante(other)));
    await assertFails(updateDoc(comprobante(customer), { subidoPor: 'other' }));
    await assertFails(enviarComprobante());
    await assertFails(updateDoc(doc(admin, 'orders', 'digital'), { paymentStatus: 'PAGADO' }));
    await assertFails(updateDoc(doc(customer, 'orders', 'digital'), { paymentProofStatus: 'APROBADO' }));
    await assertFails(updateDoc(doc(driver, 'orders', 'digital'), {
      status: 'ENTREGADO', paymentStatus: 'PAGADO'
    }));
    await assertSucceeds(updateDoc(doc(admin, 'orders', 'digital'), { paymentProofStatus: 'RECHAZADO' }));
    await assertSucceeds(enviarComprobante());
    await assertSucceeds(updateDoc(doc(admin, 'orders', 'digital'), {
      paymentProofStatus: 'APROBADO', paymentStatus: 'PAGADO',
      paymentVerifiedBy: 'admin', paymentVerifiedAt: serverTimestamp()
    }));
    await assertFails(enviarComprobante());
    await assertSucceeds(updateDoc(doc(driver, 'orders', 'digital'), { status: 'ENTREGADO' }));

    // Una ruta múltiple no debe tomar parcialmente pedidos si uno dejó de estar listo.
    await env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'orders', 'batch-ready'), {
        ...order('customer'), status: 'LISTO_PARA_ENTREGA', timestamp: new Date()
      });
      await setDoc(doc(db, 'orders', 'batch-pending'), {
        ...order('customer'), timestamp: new Date()
      });
    });
    async function tomarRuta() {
      return runTransaction(driver, async (tx) => {
        const primero = doc(driver, 'orders', 'batch-ready');
        const segundo = doc(driver, 'orders', 'batch-pending');
        await tx.get(primero);
        await tx.get(segundo);
        tx.update(primero, {
          status: 'EN_CAMINO', deliveryDriverId: 'driver',
          deliveryDriverName: 'driver', deliveryDriverPhone: '', routeOrderIndex: 1
        });
        tx.update(segundo, {
          status: 'EN_CAMINO', deliveryDriverId: 'driver',
          deliveryDriverName: 'driver', deliveryDriverPhone: '', routeOrderIndex: 2
        });
      });
    }
    await assertFails(tomarRuta());
    assert.equal((await getDoc(doc(driver, 'orders', 'batch-ready'))).data().status, 'LISTO_PARA_ENTREGA');
    await env.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), 'orders', 'batch-pending'), {
        status: 'LISTO_PARA_ENTREGA'
      });
    });
    await assertSucceeds(tomarRuta());

    // Administración registra el pedido y la auditoría en una sola transacción.
    await env.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'orders', 'admin-flow'), {
        ...order('customer'), timestamp: new Date()
      });
    });
    await assertSucceeds(runTransaction(admin, async (tx) => {
      const pedido = doc(admin, 'orders', 'admin-flow');
      const log = doc(admin, 'auditoria', 'state-admin-flow');
      const actual = await tx.get(pedido);
      tx.update(pedido, { status: 'EN_PROCESO', updatedAt: Date.now() });
      tx.set(log, {
        id: 'state-admin-flow', usuarioAfectadoId: actual.data().userId,
        usuarioAdministradorId: 'admin', accion: 'CAMBIO_ESTADO_PEDIDO',
        rolAnterior: actual.data().status, rolNuevo: 'EN_PROCESO'
      });
    }));
    assert.equal((await getDoc(doc(admin, 'auditoria', 'state-admin-flow'))).data().rolAnterior, 'PENDIENTE');

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
