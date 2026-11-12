const {getApps, initializeApp, applicationDefault, cert} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
const {createOrderService, OrderError} = require('./orderService');

function services() {
  if (!process.env.FIREBASE_PROJECT_ID) throw new Error('FIREBASE_PROJECT_ID no configurado');
  const json = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  const app = getApps()[0] || initializeApp({
    credential: json ? cert(JSON.parse(json)) : applicationDefault(),
    projectId: process.env.FIREBASE_PROJECT_ID,
  });
  return {auth: getAuth(app), orders: createOrderService(getFirestore(app), {FieldValue})};
}

async function body(req) {
  if (req.body && typeof req.body === 'object' && !Buffer.isBuffer(req.body)) return req.body;
  let raw = '';
  if (req.body) raw = Buffer.isBuffer(req.body) ? req.body.toString('utf8') : String(req.body);
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 65536) throw new OrderError(413, 'La solicitud es demasiado grande.');
  }
  try { return JSON.parse(raw); } catch (_) { throw new OrderError(400, 'JSON inválido.'); }
}

async function handler(req, res) {
  const origin = req.headers.origin;
  const allowed = (process.env.API_ALLOWED_ORIGINS || '').split(',').map(x => x.trim()).filter(Boolean);
  if (origin && allowed.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  }
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  if (req.method === 'OPTIONS') {
    res.statusCode = origin && !allowed.includes(origin) ? 403 : 204;
    return res.end();
  }
  if (req.method !== 'POST' || !req.url?.split('?')[0].endsWith('/api/orders')) {
    res.statusCode = 404; return res.end(JSON.stringify({error: 'Ruta no encontrada.'}));
  }
  try {
    if (origin && !allowed.includes(origin)) throw new OrderError(403, 'Origen no autorizado.');
    if (!req.headers['content-type']?.startsWith('application/json')) throw new OrderError(415, 'Se requiere JSON.');
    const token = /^Bearer (.+)$/.exec(req.headers.authorization || '')?.[1];
    if (!token) throw new OrderError(401, 'Inicia sesión para continuar.');
    const {auth, orders} = services();
    const decoded = await auth.verifyIdToken(token, true);
    const data = await body(req);
    const result = data.action === 'create' ? await orders.create(decoded.uid, data.order)
      : data.action === 'cancel' ? await orders.cancel(decoded.uid, data.orderId)
        : (() => { throw new OrderError(400, 'Acción inválida.'); })();
    res.statusCode = 200;
    res.end(JSON.stringify(result));
  } catch (error) {
    const authError = typeof error.code === 'string' && error.code.startsWith('auth/');
    const contention = error.code === 10 || error.code === 'aborted';
    res.statusCode = error instanceof OrderError ? error.status : authError ? 401 : contention ? 503 : 500;
    if (res.statusCode === 500) console.error('Error al procesar el pedido', error);
    res.end(JSON.stringify({error: res.statusCode === 500 ? 'No se pudo procesar el pedido.'
      : contention ? 'Hay muchos pedidos simultáneos. Vuelve a intentar.' : error.message}));
  }
}

module.exports = handler;
