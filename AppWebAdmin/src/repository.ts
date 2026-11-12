import { signInWithEmailAndPassword, signOut, type User } from 'firebase/auth';
import {
  addDoc, collection, doc, getDocFromServer, getDocsFromServer, increment,
  onSnapshot, query, runTransaction, serverTimestamp, setDoc, updateDoc, where, writeBatch,
  type Firestore, type Unsubscribe,
} from 'firebase/firestore';
import { auth, db } from './firebase';
import {
  datosVacios, disponibilidad, pedidoAbierto, precioProducto, type Datos, type Insumo,
  type Pedido, type PedidoItem, type Producto, type Receta, type Usuario,
} from './domain';

function base(): Firestore {
  if (!db) throw new Error('Configura Firebase antes de guardar cambios.');
  return db;
}

export async function entrar(email: string, password: string): Promise<void> {
  if (!auth) throw new Error('Falta la configuración de Firebase.');
  await signInWithEmailAndPassword(auth, email.trim(), password);
}

export async function salir(): Promise<void> {
  if (auth) await signOut(auth);
}

export async function perfilAdministrador(usuario: User): Promise<Usuario> {
  const perfil = await getDocFromServer(doc(base(), 'users', usuario.uid));
  if (!perfil.exists()) throw new Error('Esta cuenta no tiene perfil en Yalaza’s.');
  const datos = perfil.data() as Usuario;
  if (!['ADMIN', 'ADMINISTRADOR'].includes(datos.role) || datos.active === false) {
    throw new Error('Esta cuenta no tiene permiso de administrador.');
  }
  return { ...datos, uid: usuario.uid };
}

/** El servidor valida cada cambio; las suscripciones se cierran al salir del panel. */
export function escucharDatos(alCambiar: (datos: Datos, cargadas: Set<keyof Datos>) => void,
  alError: (origen: string, error: Error) => void): Unsubscribe {
  const firestore = base();
  const datos: Datos = { ...datosVacios };
  const cargadas = new Set<keyof Datos>();
  const limpiezas: Unsubscribe[] = [];
  const emitir = () => alCambiar({ ...datos }, new Set(cargadas));

  function escucharColeccion<K extends Exclude<keyof Datos, 'mesas'>>(
    nombre: string, clave: K, idCampo: string,
  ) {
    limpiezas.push(onSnapshot(collection(firestore, nombre), { includeMetadataChanges: true }, snapshot => {
      if (snapshot.metadata.fromCache || snapshot.metadata.hasPendingWrites) return;
      const registros = snapshot.docs.map(documento => ({ ...documento.data(), [idCampo]: documento.id }));
      (datos[clave] as unknown[]) = registros;
      cargadas.add(clave);
      emitir();
    }, error => alError(nombre, error)));
  }

  escucharColeccion('orders', 'pedidos', 'id');
  escucharColeccion('users', 'usuarios', 'uid');
  escucharColeccion('products', 'productos', 'id');
  escucharColeccion('ingredients', 'insumos', 'id');
  escucharColeccion('recipes', 'recetas', 'productId');
  escucharColeccion('promotions', 'promociones', 'productId');
  limpiezas.push(onSnapshot(doc(firestore, 'settings', 'salon'), { includeMetadataChanges: true }, snapshot => {
    if (snapshot.metadata.fromCache || snapshot.metadata.hasPendingWrites) return;
    datos.mesas = snapshot.exists() ? ((snapshot.data().tableNumbers as number[] | undefined) ?? []).filter(n => Number.isInteger(n)) : [1, 2, 3, 4, 5];
    cargadas.add('mesas');
    emitir();
  }, error => alError('mesas', error)));
  return () => limpiezas.forEach(limpiar => limpiar());
}

function auditoria(uid: string, usuarioAfectadoId: string, accion: string, antes = '', despues = '') {
  const ref = doc(collection(base(), 'auditoria'));
  return { ref, datos: {
    id: ref.id, usuarioAfectadoId, usuarioAdministradorId: uid, accion,
    rolAnterior: antes, rolNuevo: despues, fecha: Date.now(), detalles: 'Panel web administrativo',
  } };
}

export async function cambiarEstadoPedido(id: string, destino: string, uid: string): Promise<void> {
  const firestore = base();
  const ref = doc(firestore, 'orders', id);
  const siguientes: Record<string, string[]> = {
    PENDIENTE: ['EN_PROCESO', 'CANCELADO'], EN_PROCESO: ['LISTO_PARA_ENTREGA', 'CANCELADO'],
    LISTO_PARA_ENTREGA: ['EN_CAMINO', 'CANCELADO'], EN_CAMINO: ['ENTREGADO', 'CANCELADO'],
  };
  await runTransaction(firestore, async tx => {
    const snap = await tx.get(ref);
    if (!snap.exists()) throw new Error('El pedido ya no existe.');
    const p = snap.data() as Pedido;
    if (!(siguientes[p.status] ?? []).includes(destino)) throw new Error('El pedido cambió de estado. Actualiza la lista.');
    if (p.serviceType === 'MESA' && destino !== 'CANCELADO') throw new Error('La entrega en mesa la confirma el mesero.');
    if (destino === 'ENTREGADO' && p.paymentStatus !== 'PAGADO') throw new Error('Primero debe confirmarse el pago.');
    const log = auditoria(uid, p.userId || id, 'CAMBIO_ESTADO_PEDIDO', p.status, destino);
    tx.update(ref, { status: destino, updatedAt: Date.now() });
    tx.set(log.ref, log.datos);
  });
}

export async function obtenerComprobante(id: string): Promise<string> {
  const snap = await getDocFromServer(doc(base(), 'orders', id, 'privado', 'comprobante'));
  if (!snap.exists()) throw new Error('No hay imagen de comprobante para este pedido.');
  const bytes = snap.data().imagen?.toUint8Array?.() as Uint8Array | undefined;
  if (!bytes?.length) throw new Error('El comprobante no contiene una imagen válida.');
  return URL.createObjectURL(new Blob([new Uint8Array(bytes)], { type: 'image/jpeg' }));
}

export async function revisarPago(id: string, aprobar: boolean, uid: string): Promise<void> {
  const firestore = base();
  const pedidoRef = doc(firestore, 'orders', id);
  const pruebaRef = doc(firestore, 'orders', id, 'privado', 'comprobante');
  await runTransaction(firestore, async tx => {
    const pedido = await tx.get(pedidoRef);
    const prueba = await tx.get(pruebaRef);
    if (!pedido.exists() || !prueba.exists() || !prueba.data().imagen) throw new Error('Falta el comprobante.');
    const datos = pedido.data() as Pedido;
    if (datos.paymentProofStatus !== 'EN_REVISION' || datos.paymentStatus !== 'PENDIENTE' || datos.status === 'CANCELADO') {
      throw new Error('Este pago ya fue revisado o el pedido cambió.');
    }
    tx.update(pedidoRef, aprobar ? {
      paymentProofStatus: 'APROBADO', paymentStatus: 'PAGADO',
      paymentVerifiedBy: uid, paymentVerifiedAt: serverTimestamp(),
    } : { paymentProofStatus: 'RECHAZADO' });
  });
}

export async function guardarUsuario(objetivo: Usuario, cambios: {
  role: string; active: boolean; vehicleType: string; vehiclePlate: string; vehicleModel: string;
}, adminUid: string): Promise<void> {
  if (objetivo.uid === adminUid) throw new Error('Gestiona tu propia cuenta desde Firebase Console.');
  const lote = writeBatch(base());
  const actualizacion: Record<string, unknown> = {
    uid: objetivo.uid, role: cambios.role, active: cambios.active, updatedAt: Date.now(),
  };
  if (cambios.role === 'REPARTIDOR') {
    actualizacion.vehicleType = cambios.vehicleType.trim();
    actualizacion.vehiclePlate = cambios.vehiclePlate.trim().toUpperCase();
    actualizacion.vehicleModel = cambios.vehicleModel.trim();
  }
  lote.update(doc(base(), 'users', objetivo.uid), actualizacion);
  if (objetivo.role !== cambios.role) {
    const log = auditoria(adminUid, objetivo.uid, 'CAMBIO_ROL', objetivo.role, cambios.role);
    lote.set(log.ref, log.datos);
  }
  if (objetivo.active !== cambios.active) {
    const log = auditoria(adminUid, objetivo.uid, cambios.active ? 'ACTIVAR_CUENTA' : 'DESACTIVAR_CUENTA', cambios.role, cambios.role);
    lote.set(log.ref, log.datos);
  }
  await lote.commit();
}

export async function guardarProducto(p: Producto): Promise<void> {
  if (!p.name.trim() || !Number.isFinite(p.price) || p.price <= 0) throw new Error('Nombre y precio válidos son obligatorios.');
  const ref = p.id ? doc(base(), 'products', p.id) : doc(collection(base(), 'products'));
  await setDoc(ref, { ...p, id: ref.id, name: p.name.trim(), category: p.category.trim() });
}

export async function pausarProducto(p: Producto, pausar: boolean): Promise<void> {
  await updateDoc(doc(base(), 'products', p.id), { isAvailable: !pausar });
}

export async function eliminarProducto(id: string): Promise<void> {
  const lote = writeBatch(base());
  lote.delete(doc(base(), 'recipes', id));
  lote.delete(doc(base(), 'promotions', id));
  lote.delete(doc(base(), 'products', id));
  await lote.commit();
}

export async function guardarPromocion(p: Producto, precio: number, tag: string, active: boolean): Promise<void> {
  if (!Number.isFinite(precio) || precio <= 0 || precio >= p.price) throw new Error('La oferta debe ser menor que el precio base.');
  await setDoc(doc(base(), 'promotions', p.id), { productId: p.id, offerPrice: precio, offerTag: tag.slice(0, 40), active });
}

export async function guardarInsumo(i: Insumo): Promise<void> {
  if (!i.name.trim() || !['u', 'g', 'ml'].includes(i.unit) ||
      ![i.stock, i.lowThreshold, i.pauseThreshold].every(n => Number.isFinite(n) && n >= 0) ||
      i.lowThreshold < i.pauseThreshold) throw new Error('Revisa las cantidades y los umbrales del insumo.');
  if (i.id) {
    // Los ajustes de stock se hacen por separado para no pisar una entrada concurrente.
    await updateDoc(doc(base(), 'ingredients', i.id), {
      name: i.name.trim(), lowThreshold: i.lowThreshold, pauseThreshold: i.pauseThreshold,
    });
    return;
  }
  const ref = doc(collection(base(), 'ingredients'));
  await setDoc(ref, { ...i, id: ref.id, name: i.name.trim() });
}

export async function registrarEntrada(i: Insumo, cantidad: number): Promise<void> {
  if (!Number.isFinite(cantidad) || cantidad <= 0) throw new Error('Escribe una cantidad mayor que cero.');
  await updateDoc(doc(base(), 'ingredients', i.id), { stock: increment(cantidad) });
}

export async function ajustarStock(i: Insumo, cantidad: number): Promise<void> {
  if (!Number.isFinite(cantidad) || cantidad < 0) throw new Error('El saldo no puede ser negativo.');
  await updateDoc(doc(base(), 'ingredients', i.id), { stock: cantidad });
}

export async function guardarReceta(r: Receta): Promise<void> {
  if (!r.ingredients.length && !r.sauceSlotsMl.length) throw new Error('La receta necesita al menos un insumo o una salsa.');
  if (r.ingredients.some(i => !Number.isFinite(i.quantity) || i.quantity <= 0) ||
      r.sauceSlotsMl.some(cantidad => !Number.isFinite(cantidad) || cantidad <= 0)) {
    throw new Error('La receta contiene cantidades inválidas.');
  }
  await setDoc(doc(base(), 'recipes', r.productId), {
    ...r,
    sauceIngredients: Object.fromEntries(Object.entries(r.sauceIngredients).filter(([, id]) => id)),
  });
}

export async function agregarMesa(numero: number): Promise<void> {
  if (!Number.isInteger(numero) || numero < 1 || numero > 200) throw new Error('Escribe una mesa entre 1 y 200.');
  const firestore = base();
  const ref = doc(firestore, 'settings', 'salon');
  await runTransaction(firestore, async tx => {
    const snap = await tx.get(ref);
    const mesas: number[] = snap.exists() ? snap.data().tableNumbers ?? [] : [1, 2, 3, 4, 5];
    if (mesas.includes(numero)) throw new Error('Esta mesa ya existe.');
    if (mesas.length >= 200) throw new Error('El salón alcanzó el límite de mesas.');
    tx.set(ref, { tableNumbers: [...mesas, numero].sort((a, b) => a - b), updatedAt: serverTimestamp() });
  });
}

export async function quitarMesa(numero: number): Promise<void> {
  const firestore = base();
  const ocupadas = await getDocsFromServer(query(collection(firestore, 'orders'), where('serviceType', '==', 'MESA')));
  if (ocupadas.docs.some(d => d.data().tableNumber === numero && pedidoAbierto(d.data() as Pedido))) {
    throw new Error('La mesa tiene una comanda abierta y no se puede retirar.');
  }
  const ref = doc(firestore, 'settings', 'salon');
  await runTransaction(firestore, async tx => {
    const snap = await tx.get(ref);
    const mesas: number[] = snap.exists() ? snap.data().tableNumbers ?? [] : [1, 2, 3, 4, 5];
    if (!mesas.includes(numero)) throw new Error('Esta mesa ya fue retirada.');
    tx.set(ref, { tableNumbers: mesas.filter(n => n !== numero), updatedAt: serverTimestamp() });
  });
}

export interface LineaPOS { producto: Producto; cantidad: number; salsas: string[]; nota: string }
const salsasPredeterminadas = ['Acevichada', 'BBQ Clásica', 'Búfalo Picante', 'Maracuyá Dulce',
  'Honey Mustard', 'Mango Habanero', 'Ajo Parmesano', 'Tártara Especial'];

export async function registrarVentaPOS(lineas: LineaPOS[], metodo: string, recibido: number,
  cliente: string, telefono: string, adminUid: string, datos: Datos): Promise<string> {
  if (!lineas.length) throw new Error('Agrega al menos un producto.');
  if (!['EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA'].includes(metodo)) throw new Error('Selecciona un medio de pago.');
  // Consulta el servidor justo antes del cobro: el catálogo y las existencias pueden cambiar.
  const firestore = base();
  const [catalogo, recetas, insumos, promociones] = await Promise.all([
    getDocsFromServer(collection(firestore, 'products')),
    getDocsFromServer(collection(firestore, 'recipes')),
    getDocsFromServer(collection(firestore, 'ingredients')),
    getDocsFromServer(collection(firestore, 'promotions')),
  ]);
  const actuales: Datos = { ...datos,
    productos: catalogo.docs.map(x => ({ ...x.data(), id: x.id } as Producto)),
    recetas: recetas.docs.map(x => ({ ...x.data(), productId: x.id } as Receta)),
    insumos: insumos.docs.map(x => ({ ...x.data(), id: x.id } as Insumo)),
    promociones: promociones.docs.map(x => ({ ...x.data(), productId: x.id } as Datos['promociones'][number])),
  };
  const demanda = new Map<string, number>();
  const items: PedidoItem[] = lineas.map(linea => {
    const producto = actuales.productos.find(p => p.id === linea.producto.id);
    if (!producto || !Number.isInteger(linea.cantidad) || linea.cantidad < 1 || !disponibilidad(producto, actuales).disponible) {
      throw new Error(`${linea.producto.name} no está disponible.`);
    }
    if (precioProducto(producto, actuales) !== precioProducto(linea.producto, datos)) {
      throw new Error(`El precio de ${producto.name} cambió. Vacía el pedido y vuelve a agregarlo.`);
    }
    const receta = actuales.recetas.find(r => r.productId === producto.id);
    if (linea.salsas.length !== new Set(linea.salsas).size ||
        linea.salsas.some(s => !(producto.availableSauces.length ? producto.availableSauces : salsasPredeterminadas).includes(s))) {
      throw new Error(`Revisa las salsas de ${producto.name}.`);
    }
    if (receta?.sauceSlotsMl.length && !linea.salsas.length) throw new Error(`Elige al menos una salsa para ${producto.name}.`);
    const ingredientes = [...(receta?.ingredients ?? [])];
    linea.salsas.forEach((salsa, indice) => {
      if (!receta?.sauceSlotsMl.length) return;
      const id = receta.sauceIngredients[salsa];
      if (!id) throw new Error(`La salsa ${salsa} ya no está disponible.`);
      ingredientes.push({ ingredientId: id, quantity: receta.sauceSlotsMl[Math.min(indice, receta.sauceSlotsMl.length - 1)] });
    });
    ingredientes.forEach(i => demanda.set(i.ingredientId, (demanda.get(i.ingredientId) ?? 0) + i.quantity * linea.cantidad));
    const extra = Math.max(0, linea.salsas.length - (producto.maxSauces || 2));
    return {
      productId: producto.id, name: producto.name,
      description: linea.salsas.length ? `${producto.description} · Salsas: ${linea.salsas.join(', ')}` : producto.description,
      quantity: linea.cantidad, subtotal: Math.round((precioProducto(producto, actuales) + extra) * linea.cantidad * 100) / 100,
      notes: linea.nota, selectedSauces: linea.salsas,
    };
  });
  for (const [id, cantidad] of demanda) {
    const insumo = actuales.insumos.find(i => i.id === id);
    if (!insumo || !Number.isFinite(cantidad) || cantidad <= 0 || insumo.stock < cantidad || insumo.stock < insumo.pauseThreshold) {
      throw new Error(`${insumo?.name || 'Un insumo'} no alcanza para este pedido. Actualiza la carta.`);
    }
  }
  const total = Math.round(items.reduce((s, item) => s + item.subtotal, 0) * 100) / 100;
  if (total <= 0) throw new Error('El total no es válido.');
  if (metodo === 'EFECTIVO' && (!Number.isFinite(recibido) || recibido < total)) throw new Error('El efectivo recibido no cubre el total.');
  const monto = metodo === 'EFECTIVO' ? recibido : total;
  const ref = await addDoc(collection(base(), 'orders'), {
    userId: adminUid, customerName: cliente.trim() || 'Mostrador', phone: telefono.trim(),
    address: 'Mostrador', reference: '', comment: 'Venta directa registrada por administración',
    latitude: 0, longitude: 0, total, status: 'PENDIENTE', items,
    timestamp: serverTimestamp(), paymentMethod: metodo, paymentAmount: monto,
    paymentChange: Math.round((monto - total) * 100) / 100,
    paymentStatus: 'PAGADO', paymentProofStatus: '', paymentVerifiedBy: adminUid,
    paymentVerifiedAt: serverTimestamp(), deliveryDriverId: '', deliveryDriverName: '',
    deliveryDriverPhone: '', isDelivery: false, deliveryDistanceKm: 0, deliveryFee: 0,
    routeOrderIndex: 0, serviceType: 'RETIRO', tableNumber: 0, waiterId: '', waiterName: '',
  });
  return ref.id;
}

export async function subirFotoProducto(file: File): Promise<string> {
  const cloud = import.meta.env.VITE_CLOUDINARY_CLOUD_NAME;
  const preset = import.meta.env.VITE_CLOUDINARY_UPLOAD_PRESET;
  if (!cloud || !preset) throw new Error('Falta configurar Cloudinary para fotos públicas de productos.');
  if (!file.type.startsWith('image/') || file.size > 5 * 1024 * 1024) throw new Error('Selecciona una imagen de hasta 5 MB.');
  const form = new FormData();
  form.set('file', file);
  form.set('upload_preset', preset);
  const res = await fetch(`https://api.cloudinary.com/v1_1/${encodeURIComponent(cloud)}/image/upload`, { method: 'POST', body: form });
  if (!res.ok) throw new Error(`Cloudinary rechazó la imagen (${res.status}).`);
  const result = await res.json() as { secure_url?: string };
  if (!result.secure_url?.startsWith(`https://res.cloudinary.com/${cloud}/`)) throw new Error('Cloudinary devolvió una dirección inesperada.');
  return result.secure_url;
}
