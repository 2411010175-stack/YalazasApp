import test from 'node:test';
import assert from 'node:assert/strict';
import { disponibilidad, prioridad, resumen } from '../src/domain.ts';

const hoy = new Date('2026-11-11T12:00:00');
const pedido = (status, paymentStatus, total, extras = {}) => ({
  id: status, status, paymentStatus, total, timestamp: hoy,
  paymentMethod: 'YAPE', paymentProofStatus: '', serviceType: 'DELIVERY',
  isDelivery: true, items: [], ...extras,
});

test('el resumen cuenta solo ventas entregadas y pagadas', () => {
  const datos = { pedidos: [pedido('ENTREGADO', 'PAGADO', 85), pedido('EN_CAMINO', 'PAGADO', 45),
    pedido('ENTREGADO', 'PENDIENTE', 25)], productos: [], insumos: [], recetas: [], promociones: [], usuarios: [], mesas: [] };
  assert.equal(resumen(datos, hoy).total, 85);
  assert.equal(resumen(datos, hoy).ticket, 85);
});

test('pagos por revisar van antes de pedidos pendientes', () => {
  const revisar = pedido('PENDIENTE', 'PENDIENTE', 30, { paymentProofStatus: 'EN_REVISION' });
  assert.ok(prioridad(revisar) < prioridad(pedido('PENDIENTE', 'PENDIENTE', 30)));
});

test('una receta con insumo crítico pausa el plato', () => {
  const producto = { id: 'alitas', isAvailable: true };
  const datos = { productos: [producto], insumos: [{ id: 'bbq', name: 'Salsa BBQ', unit: 'ml', stock: 15,
    lowThreshold: 1000, pauseThreshold: 20 }], recetas: [{ productId: 'alitas', ingredients: [{ ingredientId: 'bbq', quantity: 25 }],
    sauceSlotsMl: [], sauceIngredients: {} }], pedidos: [], promociones: [], usuarios: [], mesas: [] };
  assert.equal(disponibilidad(producto, datos).disponible, false);
});

test('un producto sin receta no aparece disponible para venta', () => {
  const producto = { id: 'sin-receta', isAvailable: true };
  const datos = { productos: [producto], insumos: [], recetas: [], pedidos: [], promociones: [], usuarios: [], mesas: [] };
  assert.equal(disponibilidad(producto, datos).disponible, false);
});

test('una receta con insumo inexistente no habilita el producto', () => {
  const producto = { id: 'alitas', isAvailable: true };
  const datos = { productos: [producto], insumos: [], recetas: [{ productId: 'alitas',
    ingredients: [{ ingredientId: 'alitas', quantity: 6 }], sauceSlotsMl: [], sauceIngredients: {} }],
    pedidos: [], promociones: [], usuarios: [], mesas: [] };
  assert.equal(disponibilidad(producto, datos).disponible, false);
});

test('una receta completa y con stock permite la venta', () => {
  const producto = { id: 'papas', isAvailable: true, allowsSauces: false };
  const datos = { productos: [producto], insumos: [{ id: 'papa', name: 'Papa', unit: 'g',
    stock: 1000, lowThreshold: 500, pauseThreshold: 100 }], recetas: [{ productId: 'papas',
    ingredients: [{ ingredientId: 'papa', quantity: 250 }], sauceSlotsMl: [], sauceIngredients: {} }],
    pedidos: [], promociones: [], usuarios: [], mesas: [] };
  assert.equal(disponibilidad(producto, datos).disponible, true);
});
