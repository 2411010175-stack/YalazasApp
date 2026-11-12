export type Pagina = 'resumen' | 'pos' | 'ventas' | 'clientes' | 'inventario' | 'repartidores' | 'mesas' | 'ajustes';

export interface PedidoItem {
  productId: string;
  name: string;
  description?: string;
  quantity: number;
  subtotal: number;
  notes?: string;
  selectedSauces?: string[];
}

export interface Pedido {
  id: string;
  userId: string;
  customerName: string;
  phone: string;
  address: string;
  reference?: string;
  comment?: string;
  total: number;
  status: string;
  items: PedidoItem[];
  timestamp?: unknown;
  paymentMethod: string;
  paymentAmount: number;
  paymentChange: number;
  paymentStatus: string;
  paymentProofStatus?: string;
  paymentVerifiedBy?: string;
  deliveryDriverId?: string;
  deliveryDriverName?: string;
  deliveryDriverPhone?: string;
  isDelivery: boolean;
  deliveryDistanceKm: number;
  deliveryFee: number;
  routeOrderIndex: number;
  serviceType: string;
  tableNumber: number;
  waiterId?: string;
  waiterName?: string;
  receiptNumber?: string;
}

export interface Usuario {
  uid: string;
  email: string;
  role: string;
  name: string;
  phone: string;
  address?: string;
  active: boolean;
  createdAt?: number;
  vehicleType?: string;
  vehiclePlate?: string;
  vehicleModel?: string;
}

export interface Producto {
  id: string;
  name: string;
  tagline: string;
  description: string;
  price: number;
  originalPrice: number;
  isOffer: boolean;
  offerTag: string;
  imageUrl: string;
  category: string;
  isAvailable: boolean;
  allowsSauces: boolean;
  maxSauces: number;
  availableSauces: string[];
}

export interface Insumo {
  id: string;
  name: string;
  unit: 'u' | 'g' | 'ml';
  stock: number;
  lowThreshold: number;
  pauseThreshold: number;
}

export interface Receta {
  productId: string;
  ingredients: Array<{ ingredientId: string; quantity: number }>;
  sauceSlotsMl: number[];
  sauceIngredients: Record<string, string>;
}

export interface Promocion {
  productId: string;
  offerPrice: number;
  offerTag: string;
  active: boolean;
}

export interface Datos {
  pedidos: Pedido[];
  usuarios: Usuario[];
  productos: Producto[];
  insumos: Insumo[];
  recetas: Receta[];
  promociones: Promocion[];
  mesas: number[];
}

export const datosVacios: Datos = {
  pedidos: [], usuarios: [], productos: [], insumos: [], recetas: [], promociones: [], mesas: [1, 2, 3, 4, 5],
};

export const ESTADOS = ['PENDIENTE', 'EN_PROCESO', 'LISTO_PARA_ENTREGA', 'EN_CAMINO', 'ENTREGADO', 'CANCELADO'] as const;
export const METODOS = ['EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA'] as const;
export const ROLES = ['CLIENTE', 'MESERO', 'COCINERO', 'REPARTIDOR', 'ADMINISTRADOR'] as const;

export function fecha(valor: unknown): Date | null {
  if (valor instanceof Date) return Number.isNaN(valor.getTime()) ? null : valor;
  if (valor && typeof valor === 'object' && 'toDate' in valor && typeof valor.toDate === 'function') {
    return valor.toDate() as Date;
  }
  if (typeof valor === 'number' || typeof valor === 'string') {
    const resultado = new Date(valor);
    return Number.isNaN(resultado.getTime()) ? null : resultado;
  }
  return null;
}

export const dinero = (valor: number) => `S/ ${new Intl.NumberFormat('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(valor || 0)}`;
export const fechaCorta = (valor: unknown) => fecha(valor)?.toLocaleDateString('es-PE') ?? 'Sin fecha';
export const horaCorta = (valor: unknown) => fecha(valor)?.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' }) ?? '—';
export const codigo = (id: string) => `#${id.slice(-6).toUpperCase()}`;
export const esHoy = (valor: unknown, ahora = new Date()) => fecha(valor)?.toDateString() === ahora.toDateString();
export const pagoEnRevision = (p: Pedido) => ['YAPE', 'PLIN', 'TRANSFERENCIA'].includes(p.paymentMethod)
  && p.paymentStatus === 'PENDIENTE' && p.paymentProofStatus === 'EN_REVISION' && p.status !== 'CANCELADO';
export const ventaRealizada = (p: Pedido) => p.status === 'ENTREGADO' && p.paymentStatus === 'PAGADO';
export const pedidoAbierto = (p: Pedido) => !['ENTREGADO', 'CANCELADO'].includes(p.status);
export const canal = (p: Pedido) => p.serviceType === 'MESA' ? 'Salón' : p.isDelivery ? 'Delivery' : 'Para llevar';
export const nombreEstado = (s: string) => ({
  PENDIENTE: 'Pendiente', EN_PROCESO: 'En cocina', LISTO_PARA_ENTREGA: 'Listo',
  EN_CAMINO: 'En camino', ENTREGADO: 'Entregado', CANCELADO: 'Anulado',
}[s] ?? s);

export function prioridad(p: Pedido): number {
  if (pagoEnRevision(p)) return 0;
  return ESTADOS.indexOf(p.status as typeof ESTADOS[number]) + 1 || 9;
}

export function estadoInsumo(i: Insumo): 'normal' | 'bajo' | 'critico' {
  if (i.stock <= 0 || i.stock < i.pauseThreshold) return 'critico';
  if (i.stock <= i.lowThreshold) return 'bajo';
  return 'normal';
}

export function cantidadInsumo(i: Insumo, cantidad = i.stock): string {
  const f = (n: number) => new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 }).format(n);
  if (i.unit === 'g') return cantidad >= 1000 ? `${f(cantidad / 1000)} kg` : `${f(cantidad)} g`;
  return `${f(cantidad)} ${i.unit === 'u' ? 'unid.' : 'ml'}`;
}

export function precioProducto(p: Producto, datos: Datos): number {
  const promo = datos.promociones.find(x => x.productId === p.id && x.active && x.offerPrice > 0 && x.offerPrice < p.price);
  return promo?.offerPrice ?? p.price;
}

export function disponibilidad(p: Producto, datos: Datos): { disponible: boolean; porciones: number; limite: string } {
  if (!p.isAvailable) return { disponible: false, porciones: 0, limite: 'Pausado manualmente' };
  const receta = datos.recetas.find(r => r.productId === p.id);
  if (!receta) return { disponible: true, porciones: 0, limite: 'Sin receta configurada' };
  const capacidades = receta.ingredients.map(linea => {
    const i = datos.insumos.find(x => x.id === linea.ingredientId);
    return { nombre: i?.name ?? 'Insumo faltante', porciones: i && linea.quantity > 0 && i.stock >= i.pauseThreshold
      ? Math.floor(i.stock / linea.quantity) : 0 };
  });
  if (receta.sauceSlotsMl.length) {
    const primera = receta.sauceSlotsMl[0];
    const mejores = Object.values(receta.sauceIngredients).map(id => datos.insumos.find(i => i.id === id))
      .filter((i): i is Insumo => !!i && i.unit === 'ml' && i.stock >= i.pauseThreshold);
    capacidades.push({ nombre: 'Salsas disponibles', porciones: primera > 0 ? Math.max(0, ...mejores.map(i => Math.floor(i.stock / primera))) : 0 });
  }
  if (!capacidades.length) return { disponible: false, porciones: 0, limite: 'Receta vacía' };
  const limite = capacidades.reduce((a, b) => b.porciones < a.porciones ? b : a);
  return { disponible: limite.porciones > 0, porciones: limite.porciones, limite: limite.nombre };
}

export function mesasOcupadas(datos: Datos): Set<number> {
  return new Set(datos.pedidos.filter(p => p.serviceType === 'MESA' && pedidoAbierto(p)).map(p => p.tableNumber));
}

export function resumen(datos: Datos, ahora = new Date()) {
  const ventas = datos.pedidos.filter(p => ventaRealizada(p) && esHoy(p.timestamp, ahora));
  const pedidosHoy = datos.pedidos.filter(p => esHoy(p.timestamp, ahora) && p.status !== 'CANCELADO');
  const ocupadas = mesasOcupadas(datos);
  const criticos = datos.insumos.filter(i => estadoInsumo(i) === 'critico');
  const bajos = datos.insumos.filter(i => estadoInsumo(i) === 'bajo');
  const pausados = datos.productos.filter(p => !disponibilidad(p, datos).disponible);
  return {
    total: ventas.reduce((s, p) => s + p.total, 0), ventas,
    pedidosHoy: pedidosHoy.length,
    ticket: ventas.length ? ventas.reduce((s, p) => s + p.total, 0) / ventas.length : 0,
    ocupadas: ocupadas.size, mesas: datos.mesas.length,
    criticos, bajos, pausados,
    revisiones: datos.pedidos.filter(pagoEnRevision).length,
  };
}
