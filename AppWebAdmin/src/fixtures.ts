import type { Datos, Pedido, Producto, Usuario } from './domain';

const fechaHoy = (hora: number, minuto = 0) => {
  const f = new Date();
  f.setHours(hora, minuto, 0, 0);
  return f;
};

const productos: Producto[] = [
  { id: 'demo-alitas', name: 'Alitas Acevichadas', tagline: 'El favorito de la casa', description: '6 alitas broaster con papas', price: 29.5, originalPrice: 0, isOffer: false, offerTag: '', imageUrl: '', category: 'Alitas', isAvailable: true, allowsSauces: true, maxSauces: 2, availableSauces: ['Acevichada', 'BBQ Clásica'] },
  { id: 'demo-bbq', name: 'Alitas BBQ', tagline: 'Dulces y ahumadas', description: '6 alitas broaster con papas y salsa BBQ', price: 29.5, originalPrice: 0, isOffer: false, offerTag: '', imageUrl: '', category: 'Alitas', isAvailable: true, allowsSauces: true, maxSauces: 2, availableSauces: ['BBQ Clásica', 'Acevichada'] },
  { id: 'demo-papas', name: 'Papas Grandes', tagline: 'Crujientes', description: 'Porción grande de papas fritas', price: 13, originalPrice: 0, isOffer: false, offerTag: '', imageUrl: '', category: 'Acompañamientos', isAvailable: true, allowsSauces: false, maxSauces: 0, availableSauces: [] },
  { id: 'demo-chaufa', name: 'Chaufa de Pollo', tagline: 'Recién preparado', description: 'Arroz chaufa con pollo', price: 23, originalPrice: 0, isOffer: false, offerTag: '', imageUrl: '', category: 'Platos', isAvailable: true, allowsSauces: false, maxSauces: 0, availableSauces: [] },
  { id: 'demo-gaseosa', name: 'Gaseosa', tagline: 'Helada', description: 'Bebida personal', price: 6, originalPrice: 0, isOffer: false, offerTag: '', imageUrl: '', category: 'Bebidas', isAvailable: true, allowsSauces: false, maxSauces: 0, availableSauces: [] },
];

const usuarios: Usuario[] = [
  { uid: 'demo-admin', email: 'admin@yalazas.pe', role: 'ADMINISTRADOR', name: 'Alex Ramos', phone: '999 100 100', active: true },
  { uid: 'demo-mesero', email: 'jesus@yalazas.pe', role: 'MESERO', name: 'Jesús A.', phone: '999 200 200', active: true },
  { uid: 'demo-driver-1', email: 'carlos@yalazas.pe', role: 'REPARTIDOR', name: 'Carlos Mendoza', phone: '999 300 300', active: true, vehicleType: 'Moto', vehiclePlate: 'ABC-123', vehicleModel: 'Negro' },
  { uid: 'demo-driver-2', email: 'maria@yalazas.pe', role: 'REPARTIDOR', name: 'María Torres', phone: '999 400 400', active: true, vehicleType: 'Moto', vehiclePlate: 'DEF-456', vehicleModel: 'Rojo' },
  { uid: 'demo-cliente-1', email: 'cliente@ejemplo.pe', role: 'CLIENTE', name: 'Valeria Rojas', phone: '999 500 500', active: true },
];

function pedido(id: string, hora: number, status: string, total: number, paymentMethod: string,
  extra: Partial<Pedido> = {}): Pedido {
  return {
    id, userId: 'demo-cliente-1', customerName: 'Valeria Rojas', phone: '999 500 500',
    address: 'Av. San Antonio 123', reference: '', comment: '', total, status,
    items: [{ productId: 'demo-alitas', name: 'Alitas Acevichadas', quantity: 1, subtotal: total }],
    timestamp: fechaHoy(hora), paymentMethod, paymentAmount: total, paymentChange: 0,
    paymentStatus: status === 'ENTREGADO' ? 'PAGADO' : 'PENDIENTE', paymentProofStatus: '',
    isDelivery: true, deliveryDistanceKm: 1.2, deliveryFee: 3,
    deliveryDriverId: '', deliveryDriverName: '', deliveryDriverPhone: '', routeOrderIndex: 0,
    serviceType: 'DELIVERY', tableNumber: 0, waiterId: '', waiterName: '', ...extra,
  };
}

export const datosDemo: Datos = {
  usuarios, productos,
  pedidos: [
    pedido('ejemploUTPCZI', 11, 'ENTREGADO', 85, 'EFECTIVO', { serviceType: 'MESA', tableNumber: 5, isDelivery: false, deliveryFee: 0, waiterName: 'Jesús A.', customerName: 'Mesa 5' }),
    pedido('ejemploPL88X', 12, 'ENTREGADO', 120.5, 'TARJETA', { serviceType: 'MESA', tableNumber: 2, isDelivery: false, deliveryFee: 0, waiterName: 'Jesús A.', customerName: 'Mesa 2' }),
    pedido('ejemploQR77A', 13, 'ENTREGADO', 45, 'YAPE'),
    pedido('ejemploBS221', 14, 'ENTREGADO', 35, 'EFECTIVO'),
    pedido('ejemploMZ443', 15, 'EN_CAMINO', 39, 'EFECTIVO', { deliveryDriverId: 'demo-driver-1', deliveryDriverName: 'Carlos Mendoza' }),
    pedido('ejemploTY909', 14, 'LISTO_PARA_ENTREGA', 58, 'YAPE', { paymentProofStatus: 'EN_REVISION', serviceType: 'MESA', tableNumber: 3, isDelivery: false, deliveryFee: 0, customerName: 'Mesa 3', waiterName: 'Jesús A.' }),
    pedido('ejemploKL22X', 15, 'EN_PROCESO', 72, 'PLIN', { serviceType: 'MESA', tableNumber: 1, isDelivery: false, deliveryFee: 0, customerName: 'Mesa 1', waiterName: 'Jesús A.' }),
    pedido('ejemploRT772', 16, 'PENDIENTE', 29.5, 'YAPE', { paymentProofStatus: 'EN_REVISION' }),
    pedido('ejemploHH421', 16, 'PENDIENTE', 42, 'EFECTIVO', { serviceType: 'MESA', tableNumber: 4, isDelivery: false, deliveryFee: 0, customerName: 'Mesa 4', waiterName: 'Jesús A.' }),
    pedido('ejemploPC991', 15, 'PENDIENTE', 53, 'EFECTIVO', { serviceType: 'MESA', tableNumber: 6, isDelivery: false, deliveryFee: 0, customerName: 'Mesa 6', waiterName: 'Jesús A.' }),
  ],
  insumos: [
    { id: 'demo-alitas', name: 'Alitas Crudas', unit: 'u', stock: 85, lowThreshold: 40, pauseThreshold: 6 },
    { id: 'demo-papas', name: 'Papa Amarilla', unit: 'g', stock: 4200, lowThreshold: 10000, pauseThreshold: 200 },
    { id: 'demo-bbq', name: 'Salsa BBQ', unit: 'ml', stock: 15, lowThreshold: 1000, pauseThreshold: 200 },
    { id: 'demo-acevichada', name: 'Salsa Acevichada', unit: 'ml', stock: 1500, lowThreshold: 500, pauseThreshold: 25 },
  ],
  recetas: [
    { productId: 'demo-alitas', ingredients: [{ ingredientId: 'demo-alitas', quantity: 6 }, { ingredientId: 'demo-papas', quantity: 200 }], sauceSlotsMl: [25, 25], sauceIngredients: { Acevichada: 'demo-acevichada', 'BBQ Clásica': 'demo-bbq' } },
    { productId: 'demo-bbq', ingredients: [{ ingredientId: 'demo-alitas', quantity: 6 }, { ingredientId: 'demo-papas', quantity: 200 }, { ingredientId: 'demo-bbq', quantity: 50 }], sauceSlotsMl: [], sauceIngredients: {} },
    { productId: 'demo-papas', ingredients: [{ ingredientId: 'demo-papas', quantity: 250 }], sauceSlotsMl: [], sauceIngredients: {} },
  ],
  promociones: [], mesas: [1, 2, 3, 4, 5, 6, 7, 8],
  tarifa: { version: 0, baseCents: 300, includedMeters: 1500, extraKmCents: 100 },
};
