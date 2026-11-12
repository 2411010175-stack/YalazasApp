const {randomUUID} = require('node:crypto');

class OrderError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
const fail = (status, message) => { throw new OrderError(status, message); };
const validNumber = n => typeof n === 'number' && Number.isFinite(n);
const round = n => Math.round((n + Number.EPSILON) * 100) / 100;
const salsaBase = ['Acevichada', 'BBQ Clásica', 'Búfalo Picante', 'Maracuyá Dulce',
  'Honey Mustard', 'Mango Habanero', 'Ajo Parmesano', 'Tártara Especial'];

function distanceKm(lat, lon) {
  const rad = x => x * Math.PI / 180;
  const lat0 = -13.061851, lon0 = -76.352075;
  const a = Math.sin(rad(lat - lat0) / 2) ** 2 +
    Math.cos(rad(lat0)) * Math.cos(rad(lat)) * Math.sin(rad(lon - lon0) / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function validRecipe(recipe, product, stocks) {
  if (!recipe || (!recipe.ingredients?.length && !recipe.sauceSlotsMl?.length)) return false;
  const lines = recipe.ingredients || [], slots = recipe.sauceSlotsMl || [];
  const ids = lines.map(x => x.ingredientId);
  if (new Set(ids).size !== ids.length || lines.some(x =>
    !x.ingredientId || !validNumber(x.quantity) || x.quantity <= 0 || !stocks.has(x.ingredientId))) return false;
  if (!!product.allowsSauces !== (slots.length > 0)) return false;
  if (!slots.length) return true;
  if (!Number.isInteger(product.maxSauces) || product.maxSauces < 1 || slots.length < product.maxSauces ||
    slots.some(x => !validNumber(x) || x <= 0)) return false;
  const options = product.availableSauces?.length ? product.availableSauces : salsaBase;
  return options.some(name => stocks.get(recipe.sauceIngredients?.[name])?.unit === 'ml');
}

function computeCart(lines, products, recipes, stocks, promotions) {
  if (!Array.isArray(lines) || lines.length < 1 || lines.length > 50) fail(400, 'Agrega entre 1 y 50 productos.');
  const usage = new Map(), items = [];
  let subtotal = 0;
  for (const line of lines) {
    const product = products.get(line.productId);
    const recipe = recipes.get(line.productId);
    if (!product || product.isAvailable !== true || !validRecipe(recipe, product, stocks)) {
      fail(409, 'Un producto ya no tiene receta o está pausado. Actualiza el pedido.');
    }
    if (!Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > 50) fail(400, 'Cantidad inválida.');
    const sauces = line.selectedSauces ?? [];
    if (!Array.isArray(sauces) || sauces.length > 8 || new Set(sauces).size !== sauces.length ||
      sauces.some(x => typeof x !== 'string')) fail(400, 'Revisa las salsas elegidas.');
    const sauceSlots = recipe.sauceSlotsMl || [];
    if (!!sauceSlots.length !== (sauces.length > 0)) fail(400, 'Selecciona las salsas de este plato.');
    const allowed = product.availableSauces?.length ? product.availableSauces : salsaBase;
    const ingredients = [...(recipe.ingredients || [])];
    sauces.forEach((name, index) => {
      if (!allowed.includes(name)) fail(400, 'Salsa no permitida.');
      const id = recipe.sauceIngredients?.[name], stock = stocks.get(id);
      if (!stock || stock.unit !== 'ml') fail(409, 'Una salsa no tiene insumo disponible.');
      ingredients.push({ingredientId: id,
        quantity: sauceSlots[Math.min(index, sauceSlots.length - 1)]});
    });
    for (const ingredient of ingredients) {
      usage.set(ingredient.ingredientId,
        (usage.get(ingredient.ingredientId) || 0) + ingredient.quantity * line.quantity);
    }
    const promo = promotions.get(line.productId);
    const price = promo?.active === true && product.isOffer !== true &&
      validNumber(promo.offerPrice) && promo.offerPrice > 0 && promo.offerPrice < product.price
      ? promo.offerPrice : product.price;
    if (!validNumber(price) || price <= 0) fail(409, 'El precio del producto no es válido.');
    const extras = Math.max(0, sauces.length - (product.maxSauces || 0));
    const lineTotal = round((price + extras) * line.quantity);
    subtotal = round(subtotal + lineTotal);
    items.push({productId: line.productId, name: product.name,
      description: `${product.name}${sauces.length ? ` · Salsas: ${sauces.join(', ')}` : ''}`,
      quantity: line.quantity, subtotal: lineTotal,
      notes: String(line.notes || '').slice(0, 500), selectedSauces: sauces});
  }
  for (const [id, amount] of usage) {
    const stock = stocks.get(id);
    if (!stock || !validNumber(amount) || amount <= 0 || !validNumber(stock.stock) ||
      !validNumber(stock.pauseThreshold) || stock.stock < amount || stock.stock < stock.pauseThreshold) {
      fail(409, `${stock?.name || 'Un insumo'} no alcanza para este pedido.`);
    }
  }
  return {items, usage, subtotal};
}

function validateContext(input, role, tables, tariff) {
  const serviceType = input.serviceType || (input.isDelivery ? 'DELIVERY' : 'RETIRO');
  const isTable = serviceType === 'MESA', isPOS = input.pos === true;
  if (isTable && (role !== 'MESERO' || input.isDelivery || !Number.isInteger(input.tableNumber) ||
    !tables.includes(input.tableNumber))) fail(403, 'La mesa no está disponible.');
  if (isPOS && (role !== 'ADMIN' && role !== 'ADMINISTRADOR' || isTable || input.isDelivery)) {
    fail(403, 'Solo administración puede registrar ventas de mostrador.');
  }
  if (!isTable && !isPOS && (role !== 'CLIENTE' || !['DELIVERY', 'RETIRO'].includes(serviceType))) {
    fail(403, 'Tu cuenta no puede crear este pedido.');
  }
  if (serviceType === 'DELIVERY' && input.isDelivery !== true) fail(400, 'Modalidad de entrega inválida.');
  if (!isTable && serviceType !== 'DELIVERY' && input.isDelivery === true) fail(400, 'Modalidad de entrega inválida.');
  if (!isTable && !isPOS && (!input.address || typeof input.address !== 'string')) fail(400, 'Falta la dirección.');
  const methods = isTable ? ['POR_DEFINIR'] : isPOS
    ? ['EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA']
    : ['EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA'];
  if (!methods.includes(input.paymentMethod)) fail(400, 'Método de pago inválido.');
  let lat = 0, lon = 0, km = 0, fee = 0;
  if (serviceType === 'DELIVERY') {
    if ((input.deliveryTariffVersion ?? 0) !== tariff.version) {
      fail(409, 'La tarifa cambió. Actualiza el pedido antes de confirmar.');
    }
    lat = input.latitude; lon = input.longitude;
    if (!validNumber(lat) || !validNumber(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180 ||
      (lat === 0 && lon === 0)) fail(400, 'Ubicación de entrega inválida.');
    km = distanceKm(lat, lon);
    fee = (tariff.baseCents + Math.max(0, Math.ceil(km - tariff.includedMeters / 1000)) *
      tariff.extraKmCents) / 100;
  }
  return {serviceType, isTable, isPOS, lat, lon, km, fee,
    tariffVersion: serviceType === 'DELIVERY' ? tariff.version : 0};
}

function readTariff(snapshot) {
  if (!snapshot.exists) return {version: 0, baseCents: 300, includedMeters: 1500, extraKmCents: 100};
  const data = snapshot.data();
  if (!Number.isInteger(data.version) || data.version < 1 ||
    !Number.isInteger(data.baseCents) || data.baseCents < 0 || data.baseCents > 10000 ||
    !Number.isInteger(data.includedMeters) || data.includedMeters < 1 || data.includedMeters > 50000 ||
    !Number.isInteger(data.extraKmCents) || data.extraKmCents < 0 || data.extraKmCents > 10000) {
    fail(503, 'La tarifa de reparto no está configurada correctamente.');
  }
  return data;
}

function createOrderService(db, firestore) {
  async function create(uid, input) {
    if (!input || typeof input !== 'object' || !/^[0-9a-f]{32}$/i.test(input.requestId || '')) {
      fail(400, 'Identificador de solicitud inválido.');
    }
    const orderRef = db.collection('orders').doc(input.requestId);
    const profileRef = db.collection('users').doc(uid);
    if (!Array.isArray(input.items) || input.items.length > 50 || input.items.some(x => !x || typeof x !== 'object')) {
      fail(400, 'Productos inválidos.');
    }
    const uniqueIds = [...new Set(input.items.map(x => x.productId))];
    if (!uniqueIds.length || uniqueIds.length > 50 || uniqueIds.some(id => typeof id !== 'string' || !id)) {
      fail(400, 'Productos inválidos.');
    }
    const productRefs = uniqueIds.map(id => db.collection('products').doc(id));
    const recipeRefs = uniqueIds.map(id => db.collection('recipes').doc(id));
    const promoRefs = uniqueIds.map(id => db.collection('promotions').doc(id));
    return db.runTransaction(async tx => {
      const [existing, profile, salon, tariffDoc] = await Promise.all([
        tx.get(orderRef), tx.get(profileRef), tx.get(db.collection('settings').doc('salon')),
        tx.get(db.collection('settings').doc('deliveryTariff'))]);
      if (existing.exists) {
        if (existing.get('userId') !== uid || existing.get('inventoryVersion') !== 1) fail(409, 'Solicitud duplicada.');
        return {id: orderRef.id, total: existing.get('total'), reused: true};
      }
      if (profile.exists && profile.get('active') === false) fail(403, 'Cuenta desactivada.');
      const role = profile.exists ? profile.get('role') : 'CLIENTE';
      const tables = salon.exists ? salon.get('tableNumbers') : [1, 2, 3, 4, 5];
      const context = validateContext(input, role, tables, readTariff(tariffDoc));
      const [productDocs, recipeDocs, promoDocs] = await Promise.all([
        Promise.all(productRefs.map(ref => tx.get(ref))),
        Promise.all(recipeRefs.map(ref => tx.get(ref))),
        Promise.all(promoRefs.map(ref => tx.get(ref)))]);
      const products = new Map(productDocs.filter(x => x.exists).map(x => [x.id, x.data()]));
      const recipes = new Map(recipeDocs.filter(x => x.exists).map(x => [x.id, x.data()]));
      const promos = new Map(promoDocs.filter(x => x.exists).map(x => [x.id, x.data()]));
      const ingredientIds = [...new Set(recipeDocs.flatMap(x => x.exists
        ? [...(x.get('ingredients') || []).map(i => i.ingredientId), ...Object.values(x.get('sauceIngredients') || {})]
        : []).filter(Boolean))];
      if (ingredientIds.length > 100) fail(400, 'El pedido usa demasiados insumos.');
      const ingredientRefs = ingredientIds.map(id => db.collection('ingredients').doc(id));
      const ingredientDocs = await Promise.all(ingredientRefs.map(ref => tx.get(ref)));
      const stocks = new Map(ingredientDocs.filter(x => x.exists).map(x => [x.id, x.data()]));
      const {items, usage, subtotal} = computeCart(input.items, products, recipes, stocks, promos);
      const total = round(subtotal + context.fee);
      if (!validNumber(input.expectedTotal) || Math.abs(input.expectedTotal - total) > 0.01) {
        fail(409, 'El precio o la tarifa cambió. Actualiza el pedido antes de confirmar.');
      }
      const cash = input.paymentMethod === 'EFECTIVO';
      if (cash && (!validNumber(input.paymentAmount) || input.paymentAmount < total)) fail(400, 'El efectivo no cubre el total.');
      const paid = context.isTable ? 0 : cash ? input.paymentAmount : total;
      const now = firestore.FieldValue.serverTimestamp();
      const usageObject = Object.fromEntries(usage);
      const order = {
        userId: uid, customerName: context.isTable ? String(input.customerName || `Mesa ${input.tableNumber}`).slice(0, 100)
          : context.isPOS ? String(input.customerName || 'Mostrador').slice(0, 100)
            : String(profile.get?.('name') || input.customerName || 'Cliente').slice(0, 100),
        phone: context.isTable ? '' : String(context.isPOS ? input.phone || '' : profile.get?.('phone') || input.phone || '').slice(0, 30),
        address: context.isTable ? `Mesa ${input.tableNumber}` : context.isPOS ? 'Mostrador' : String(input.address).slice(0, 300),
        reference: context.isTable ? 'Salón' : String(input.reference || '').slice(0, 300),
        latitude: context.lat, longitude: context.lon, comment: String(input.comment || '').slice(0, 500),
        total, status: 'PENDIENTE', items, timestamp: now,
        paymentMethod: input.paymentMethod, paymentAmount: paid,
        paymentChange: cash ? round(paid - total) : 0,
        paymentStatus: context.isPOS ? 'PAGADO' : 'PENDIENTE',
        deliveryDriverId: '', deliveryDriverName: '', deliveryDriverPhone: '',
        isDelivery: context.serviceType === 'DELIVERY', deliveryDistanceKm: context.km, deliveryFee: context.fee,
        deliveryTariffVersion: context.tariffVersion,
        routeOrderIndex: 0, serviceType: context.serviceType,
        tableNumber: context.isTable ? input.tableNumber : 0,
        waiterId: context.isTable ? uid : '', waiterName: context.isTable ? String(profile.get('name') || '') : '',
        paymentVerifiedBy: context.isPOS ? uid : '',
        ...(context.isPOS ? {paymentVerifiedAt: now, paymentProofStatus: ''} : {}),
        inventoryVersion: 1, inventoryUsage: usageObject, inventoryRestored: false,
      };
      for (const [id, amount] of usage) {
        tx.update(db.collection('ingredients').doc(id), {stock: Math.max(0, stocks.get(id).stock - amount)});
      }
      tx.create(orderRef, order);
      return {id: orderRef.id, total, reused: false};
    });
  }

  async function cancel(uid, orderId) {
    if (typeof orderId !== 'string' || !/^[A-Za-z0-9-]{1,128}$/.test(orderId)) fail(400, 'Pedido inválido.');
    const orderRef = db.collection('orders').doc(orderId);
    const profileRef = db.collection('users').doc(uid);
    return db.runTransaction(async tx => {
      const [order, profile] = await Promise.all([tx.get(orderRef), tx.get(profileRef)]);
      if (!order.exists) fail(404, 'El pedido ya no existe.');
      const isAdmin = profile.exists && profile.get('active') !== false &&
        ['ADMIN', 'ADMINISTRADOR'].includes(profile.get('role'));
      if (!isAdmin && (order.get('userId') !== uid || order.get('serviceType') === 'MESA' ||
        profile.get?.('active') === false)) {
        fail(403, 'Este pedido ya no se puede cancelar.');
      }
      const state = order.get('status');
      if (state === 'CANCELADO') return {id: orderId, reused: true};
      if (!isAdmin && state !== 'PENDIENTE') fail(403, 'Este pedido ya no se puede cancelar.');
      if (isAdmin && !['PENDIENTE', 'EN_PROCESO', 'LISTO_PARA_ENTREGA', 'EN_CAMINO'].includes(state)) {
        fail(409, 'El pedido ya no puede cancelarse.');
      }
      const usage = state === 'PENDIENTE' && order.get('inventoryVersion') === 1 &&
        order.get('inventoryRestored') !== true ? order.get('inventoryUsage') || {} : {};
      const ids = Object.keys(usage);
      const refs = ids.map(id => db.collection('ingredients').doc(id));
      const docs = await Promise.all(refs.map(ref => tx.get(ref)));
      for (let i = 0; i < ids.length; i++) {
        if (!docs[i].exists || !validNumber(usage[ids[i]]) || usage[ids[i]] <= 0) fail(409, 'No se pudo devolver el inventario.');
      }
      refs.forEach((ref, i) => tx.update(ref, {stock: firestore.FieldValue.increment(usage[ids[i]])}));
      tx.update(orderRef, {status: 'CANCELADO', updatedAt: Date.now(),
        ...(ids.length ? {inventoryRestored: true} : {})});
      if (isAdmin) {
        const auditRef = db.collection('auditoria').doc(randomUUID());
        tx.set(auditRef, {id: auditRef.id, usuarioAfectadoId: order.get('userId') || orderId,
          usuarioAdministradorId: uid, accion: 'CAMBIO_ESTADO_PEDIDO', rolAnterior: state,
          rolNuevo: 'CANCELADO', fecha: Date.now(), detalles: 'API de pedidos'});
      }
      return {id: orderId, reused: false};
    });
  }
  return {create, cancel};
}

module.exports = {createOrderService, computeCart, validRecipe, validateContext, OrderError};
