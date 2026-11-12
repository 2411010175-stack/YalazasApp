const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineString, defineSecret} = require("firebase-functions/params");
const admin = require("firebase-admin");
const {v2: cloudinary} = require("cloudinary");
const {randomUUID} = require("node:crypto");

admin.initializeApp();
const db = admin.firestore();
const cloudName = defineString("CLOUDINARY_CLOUD_NAME");
const apiKey = defineString("CLOUDINARY_API_KEY");
const apiSecret = defineSecret("CLOUDINARY_API_SECRET");
const options = {region: "us-central1", secrets: [apiSecret]};
const DIGITALES = new Set(["YAPE", "PLIN", "TRANSFERENCIA"]);
const FORMATOS = new Set(["jpg", "jpeg", "png", "webp"]);
const LIMITE_BYTES = 5 * 1024 * 1024;

function configurarCloudinary() {
  cloudinary.config({cloud_name: cloudName.value(), api_key: apiKey.value(), api_secret: apiSecret.value(), secure: true});
}

function exigirSesion(request) {
  if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Inicia sesión para continuar.");
  return request.auth.uid;
}

async function exigirAdministrador(uid) {
  const perfil = await db.collection("users").doc(uid).get();
  if (!perfil.exists || perfil.get("active") === false || !["ADMIN", "ADMINISTRADOR"].includes(perfil.get("role"))) {
    throw new HttpsError("permission-denied", "Solo administración puede realizar esta acción.");
  }
}

async function exigirClienteActivo(uid) {
  const perfil = await db.collection("users").doc(uid).get();
  if (!perfil.exists || perfil.get("active") === false || perfil.get("role") !== "CLIENTE") {
    throw new HttpsError("permission-denied", "La cuenta no puede subir comprobantes.");
  }
}

async function exigirPedidoDigital(pedidoId, uid) {
  await exigirClienteActivo(uid);
  if (typeof pedidoId !== "string" || !/^[A-Za-z0-9]{1,128}$/.test(pedidoId)) {
    throw new HttpsError("invalid-argument", "Pedido inválido.");
  }
  const ref = db.collection("orders").doc(pedidoId);
  const pedido = await ref.get();
  if (!pedido.exists || pedido.get("userId") !== uid || !DIGITALES.has(pedido.get("paymentMethod")) ||
      pedido.get("serviceType") === "MESA" || pedido.get("status") === "CANCELADO" ||
      pedido.get("paymentStatus") === "PAGADO") {
    throw new HttpsError("permission-denied", "No puedes adjuntar un comprobante a este pedido.");
  }
  return ref;
}

/** Firma una sola subida con ID generado por el servidor y permisos según rol/pedido. */
exports.autorizarSubidaImagen = onCall(options, async (request) => {
  const uid = exigirSesion(request);
  const tipo = request.data?.tipo;
  if (tipo !== "PRODUCTO") throw new HttpsError("invalid-argument", "Tipo de imagen inválido.");
  await exigirAdministrador(uid);

  configurarCloudinary();
  const publicId = `yalazas/productos/${randomUUID()}`;
  const timestamp = Math.floor(Date.now() / 1000);
  const parametros = {public_id: publicId, asset_folder: "yalazas/productos", timestamp, transformation: "fl_force_strip"};
  const signature = cloudinary.utils.api_sign_request(parametros, apiSecret.value());
  return {
    cloudName: cloudName.value(), apiKey: apiKey.value(), signature,
    publicId, timestamp, assetFolder: "yalazas/productos", transformation: "fl_force_strip", tipoEntrega: "upload",
  };
});

/** El servidor recibe comprobantes pequeños y fuerza type=authenticated; el cliente no puede cambiarlo. */
exports.subirComprobante = onCall({region: "us-central1", secrets: [apiSecret], timeoutSeconds: 120}, async (request) => {
  const uid = exigirSesion(request);
  const {pedidoId, base64, mime} = request.data || {};
  const ref = await exigirPedidoDigital(pedidoId, uid);
  if (typeof base64 !== "string" || base64.length > Math.ceil(LIMITE_BYTES * 4 / 3) + 8 ||
      !/^[A-Za-z0-9+/]+={0,2}$/.test(base64) ||
      !["image/jpeg", "image/png", "image/webp"].includes(mime)) {
    throw new HttpsError("invalid-argument", "Selecciona una imagen JPG, PNG o WebP de hasta 5 MB.");
  }
  const bytes = Buffer.from(base64, "base64");
  if (!bytes.length || bytes.length > LIMITE_BYTES) {
    throw new HttpsError("invalid-argument", "La imagen supera el límite de 5 MB.");
  }
  const publicId = `yalazas/comprobantes/${pedidoId}/${randomUUID()}`;
  configurarCloudinary();
  let activo;
  try {
    activo = await cloudinary.uploader.upload(`data:${mime};base64,${base64}`, {
      public_id: publicId, type: "authenticated", resource_type: "image",
      asset_folder: "yalazas/comprobantes",
      allowed_formats: ["jpg", "jpeg", "png", "webp"], overwrite: false,
      transformation: "fl_force_strip", discard_original_filename: true,
    });
  } catch (_) {
    throw new HttpsError("invalid-argument", "No se pudo subir esta imagen.");
  }
  if (activo.bytes > LIMITE_BYTES || !FORMATOS.has(activo.format) || activo.type !== "authenticated") {
    throw new HttpsError("invalid-argument", "El archivo no cumple los límites de imagen.");
  }
  const comprobanteRef = ref.collection("privado").doc("comprobante");
  let comprobanteAnterior = null;
  try { await db.runTransaction(async (tx) => {
    const pedido = await tx.get(ref);
    const anterior = await tx.get(comprobanteRef);
    if (!pedido.exists || pedido.get("userId") !== uid || !DIGITALES.has(pedido.get("paymentMethod")) ||
        pedido.get("paymentStatus") === "PAGADO" || pedido.get("status") === "CANCELADO") {
      throw new HttpsError("failed-precondition", "El pedido ya no acepta comprobantes.");
    }
    comprobanteAnterior = anterior.exists ? anterior.get("publicId") : null;
    tx.set(comprobanteRef, {
      publicId, format: activo.format, assetId: activo.asset_id,
      estado: "EN_REVISION", subidoPor: uid,
      actualizadoEn: admin.firestore.FieldValue.serverTimestamp(),
    });
    tx.update(ref, {paymentProofStatus: "EN_REVISION"});
  }); } catch (error) {
    await cloudinary.uploader.destroy(publicId, {resource_type: "image", type: "authenticated"}).catch(() => {});
    throw error;
  }
  if (comprobanteAnterior && comprobanteAnterior !== publicId) {
    await cloudinary.uploader.destroy(comprobanteAnterior, {resource_type: "image", type: "authenticated"}).catch(() => {});
  }
  return {estado: "EN_REVISION"};
});

/** Solo propietario y administrador pueden recibir una descarga temporal. */
exports.obtenerComprobante = onCall(options, async (request) => {
  const uid = exigirSesion(request);
  const pedidoId = request.data?.pedidoId;
  if (typeof pedidoId !== "string" || !/^[A-Za-z0-9]{1,128}$/.test(pedidoId)) {
    throw new HttpsError("invalid-argument", "Pedido inválido.");
  }
  const ref = db.collection("orders").doc(pedidoId);
  const pedido = await ref.get();
  if (!pedido.exists) throw new HttpsError("not-found", "Pedido inexistente.");
  if (pedido.get("userId") !== uid) await exigirAdministrador(uid);
  else await exigirClienteActivo(uid);
  const comprobante = await ref.collection("privado").doc("comprobante").get();
  if (!comprobante.exists) throw new HttpsError("not-found", "El pedido aún no tiene comprobante.");
  configurarCloudinary();
  const url = cloudinary.utils.private_download_url(comprobante.get("publicId"), comprobante.get("format"), {
    resource_type: "image", type: "authenticated", expires_at: Math.floor(Date.now() / 1000) + 300,
  });
  return {url, estado: comprobante.get("estado")};
});

/** La aprobación es una transición de pago interna, separada del estado visible del pedido. */
exports.revisarComprobante = onCall(options, async (request) => {
  const uid = exigirSesion(request);
  await exigirAdministrador(uid);
  const {pedidoId, decision} = request.data || {};
  if (typeof pedidoId !== "string" || !/^[A-Za-z0-9]{1,128}$/.test(pedidoId) ||
      !["APROBADO", "RECHAZADO"].includes(decision)) {
    throw new HttpsError("invalid-argument", "Revisión inválida.");
  }
  const ref = db.collection("orders").doc(pedidoId);
  const comprobanteRef = ref.collection("privado").doc("comprobante");
  await db.runTransaction(async (tx) => {
    const [pedido, comprobante] = await Promise.all([tx.get(ref), tx.get(comprobanteRef)]);
    if (!pedido.exists || !comprobante.exists || !DIGITALES.has(pedido.get("paymentMethod")) ||
        pedido.get("paymentStatus") === "PAGADO" || pedido.get("status") === "CANCELADO" ||
        comprobante.get("estado") !== "EN_REVISION") {
      throw new HttpsError("failed-precondition", "Este comprobante ya no admite revisión.");
    }
    tx.update(comprobanteRef, {estado: decision, revisadoPor: uid,
      revisadoEn: admin.firestore.FieldValue.serverTimestamp()});
    const cambios = {paymentProofStatus: decision};
    if (decision === "APROBADO") {
      cambios.paymentStatus = "PAGADO";
      cambios.paymentVerifiedBy = uid;
      cambios.paymentVerifiedAt = admin.firestore.FieldValue.serverTimestamp();
    }
    tx.update(ref, cambios);
  });
  return {estado: decision};
});
