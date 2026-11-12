import { useEffect, useMemo, useRef } from 'react';
import Chart from 'chart.js/auto';
import {
  canal, cantidadInsumo, codigo, dinero, esHoy, estadoInsumo,
  fecha, horaCorta, pagoEnRevision, resumen, type Datos, type Pagina,
} from '../domain';
import { EncabezadoPagina, Estado, Tarjeta, Vacio, botonBorde, botonRosa } from '../ui';

export default function Resumen({ datos, ir, listo }: { datos: Datos; ir: (p: Pagina) => void; listo: boolean }) {
  const m = useMemo(() => resumen(datos), [datos]);
  const grafico = useRef<HTMLCanvasElement>(null);
  const dona = useRef<HTMLCanvasElement>(null);
  const pagos = ['EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA'].map(metodo => ({
    metodo, monto: m.ventas.filter(p => p.paymentMethod === metodo).reduce((s, p) => s + p.total, 0),
  })).filter(x => x.monto > 0);
  const platos = Object.entries(m.ventas.flatMap(p => p.items).reduce<Record<string, number>>((acc, item) => {
    acc[item.name] = (acc[item.name] ?? 0) + item.quantity;
    return acc;
  }, {})).sort((a, b) => b[1] - a[1]).slice(0, 3);
  const canales = ['Salón', 'Delivery', 'Para llevar'].map(nombre => ({
    nombre, valor: m.ventas.filter(p => canal(p) === nombre).length,
  }));
  const recientes = [...datos.pedidos].sort((a, b) => (fecha(b.timestamp)?.getTime() ?? 0) - (fecha(a.timestamp)?.getTime() ?? 0)).slice(0, 5);
  const repartidores = datos.usuarios.filter(u => u.role === 'REPARTIDOR');

  useEffect(() => {
    if (!grafico.current) return;
    const horas = Array.from({ length: 15 }, (_, i) => i + 8);
    const totales = horas.map(h => m.ventas.filter(p => fecha(p.timestamp)?.getHours() === h).reduce((s, p) => s + p.total, 0));
    const chart = new Chart(grafico.current, {
      type: 'line',
      data: { labels: horas.map(h => `${h}:00`), datasets: [{
        label: 'Ventas completadas', data: totales, borderColor: '#e81b62', backgroundColor: '#e81b6240',
        fill: true, tension: .42, pointRadius: 2, pointHoverRadius: 5, borderWidth: 3,
      }] },
      options: { responsive: true, maintainAspectRatio: false, animation: false, plugins: {
        legend: { display: false }, tooltip: { callbacks: { label: x => dinero(Number(x.parsed.y)) } },
      }, scales: { x: { grid: { display: false }, ticks: { color: '#9da99f', maxTicksLimit: 8, font: { size: 10 } } },
        y: { beginAtZero: true, grid: { color: '#344038' }, ticks: { color: '#9da99f', callback: value => `S/ ${value}`, font: { size: 10 } } } } },
    });
    return () => chart.destroy();
  }, [m.ventas]);

  useEffect(() => {
    if (!dona.current) return;
    const chart = new Chart(dona.current, {
      type: 'doughnut', data: { labels: canales.map(c => c.nombre), datasets: [{
        data: canales.map(c => c.valor), backgroundColor: ['#e81b62', '#9fd600', '#7a8a7c'], borderColor: '#1d211f', borderWidth: 3,
      }] }, options: { responsive: true, maintainAspectRatio: false, animation: false, cutout: '72%', plugins: {
        legend: { display: false }, tooltip: { callbacks: { label: x => `${x.label}: ${x.parsed} pedidos` } },
      } },
    });
    return () => chart.destroy();
  }, [m.ventas]);

  return <>
    <EncabezadoPagina etiqueta="VISIÓN GENERAL" titulo="Panel de resumen"
      descripcion={`Supervisión del local en tiempo real · ${new Date().toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' })}`}
      acciones={<button className={botonBorde} onClick={() => ir('ventas')}><i className="bi bi-receipt me-2" />Ver ventas</button>} />
    <div className="row g-3 mb-3">
      <div className="col-12 col-sm-6 col-xl-3"><Tarjeta className="kpi kpi-rosa"><div className="kpi-label"><i className="bi bi-cash-stack" />Venta total de hoy</div><div className="kpi-value">{listo ? dinero(m.total) : '—'}</div><div className="kpi-foot">{pagos.length ? pagos.map(x => `${x.metodo}: ${dinero(x.monto)}`).join(' · ') : 'Pedidos entregados y pagados'}</div></Tarjeta></div>
      <div className="col-12 col-sm-6 col-xl-3"><Tarjeta className="kpi kpi-oro"><div className="kpi-label"><i className="bi bi-bag-check" />Pedidos de hoy</div><div className="kpi-value">{listo ? m.pedidosHoy : '—'}</div><div className="kpi-foot">Ticket promedio completado: {dinero(m.ticket)}</div></Tarjeta></div>
      <div className="col-12 col-sm-6 col-xl-3"><Tarjeta className="kpi kpi-lima"><div className="kpi-label"><i className="bi bi-table" />Ocupación del salón</div><div className="kpi-value"><span className="text-lima">{listo ? m.ocupadas : '—'}</span><span className="fs-6 text-secondary"> / {m.mesas}</span></div><div className="kpi-foot">Mesas ocupadas ahora</div><div className="progreso"><span style={{ width: `${m.mesas ? m.ocupadas / m.mesas * 100 : 0}%` }} /></div></Tarjeta></div>
      <div className="col-12 col-sm-6 col-xl-3"><Tarjeta className="kpi kpi-rojo"><div className="kpi-label"><i className="bi bi-exclamation-triangle" />Alertas de inventario</div><div className="kpi-value text-rojo">{listo ? m.criticos.length : '—'} <span className="fs-6">críticos</span></div><div className="kpi-foot">{m.pausados.length} platos pausados · {m.bajos.length} insumos bajos</div></Tarjeta></div>
    </div>
    <div className="row g-3 mb-3">
      <div className="col-12 col-xl-8"><Tarjeta title="Ventas por hora" action={<span className="muted-label">Solo pedidos completados y pagados</span>}><div className="chart-wrap"><canvas ref={grafico} aria-label="Gráfico de ventas por hora" role="img" /></div></Tarjeta></div>
      <div className="col-12 col-md-6 col-xl-4"><div className="d-flex flex-column gap-3 h-100">
        <Tarjeta title="Pedidos por canal"><div className="d-flex align-items-center gap-3"><div className="chart-donut flex-grow-1"><canvas ref={dona} aria-label="Gráfico de pedidos por canal" role="img" /></div><div className="flex-grow-1">{canales.map((c, i) => <div className="resumen-barra" key={c.nombre}><span style={{ color: ['#ff75a5', '#9fd600', '#a9b6aa'][i] }}>●</span><span>{c.nombre}</span><strong className="ms-auto">{c.valor}</strong></div>)}</div></div></Tarjeta>
        <Tarjeta title="Platos más vendidos hoy">{platos.length ? platos.map(([nombre, cantidad], i) => <div className="data-row" key={nombre}><div><span className="text-secondary me-2">{i + 1}.</span><span className="data-row-title">{nombre}</span></div><strong className="text-lima">{cantidad}</strong></div>) : <Vacio titulo="Sin ventas completadas hoy" />}</Tarjeta>
      </div></div>
    </div>
    <div className="row g-3">
      <div className="col-12 col-xl-6"><Tarjeta title="Pedidos recientes" action={<button className="btn-link-sutil" onClick={() => ir('ventas')}>Ver todos <i className="bi bi-arrow-right" /></button>}>
        {recientes.length ? recientes.map(p => <div className="data-row" key={p.id}><div><div className="data-row-title">{codigo(p.id)} · {p.customerName || 'Cliente'}</div><div className="data-row-sub">{canal(p)} · {horaCorta(p.timestamp)}{pagoEnRevision(p) ? ' · Falta validar pago' : ''}</div></div><div className="text-end"><strong className="d-block small">{dinero(p.total)}</strong><Estado pedido={p} /></div></div>) : <Vacio titulo="Aún no hay pedidos" />}</Tarjeta></div>
      <div className="col-12 col-md-6 col-xl-3"><Tarjeta title="Almacén" action={<button className="btn-link-sutil" onClick={() => ir('inventario')}>Gestionar</button>}>
        {datos.insumos.length ? [...datos.insumos].sort((a, b) => ({ critico: 0, bajo: 1, normal: 2 })[estadoInsumo(a)] - ({ critico: 0, bajo: 1, normal: 2 })[estadoInsumo(b)]).slice(0, 4).map(i => <div className="data-row" key={i.id}><div><div className="data-row-title">{i.name}</div><div className="data-row-sub">{cantidadInsumo(i)}</div></div><span className={`estado estado-${estadoInsumo(i)}`}>{estadoInsumo(i) === 'normal' ? 'Normal' : estadoInsumo(i) === 'bajo' ? 'Bajo' : 'Crítico'}</span></div>) : <Vacio titulo="Sin insumos" />}</Tarjeta></div>
      <div className="col-12 col-md-6 col-xl-3"><Tarjeta title="Repartidores" action={<button className="btn-link-sutil" onClick={() => ir('repartidores')}>Ver flota</button>}>
        {repartidores.length ? repartidores.slice(0, 4).map(u => { const ruta = datos.pedidos.find(p => p.deliveryDriverId === u.uid && p.status === 'EN_CAMINO'); return <div className="data-row" key={u.uid}><div><div className="data-row-title">{u.name}</div><div className="data-row-sub">{u.vehicleType || 'Vehículo sin registrar'}</div></div><span className={`estado ${ruta ? 'estado-en_camino' : u.active ? 'estado-neutro' : 'estado-cancelado'}`}>{ruta ? 'En ruta' : u.active ? 'Sin ruta' : 'Inactivo'}</span></div>; }) : <Vacio titulo="Sin repartidores" />}</Tarjeta></div>
    </div>
    {(m.revisiones > 0 || m.criticos.length > 0) && <div className="d-flex flex-wrap gap-2 mt-3">{m.revisiones > 0 && <button className={botonRosa} onClick={() => ir('ventas')}>Revisar {m.revisiones} pagos pendientes</button>}{m.criticos.length > 0 && <button className={botonBorde} onClick={() => ir('inventario')}>Atender {m.criticos.length} insumos críticos</button>}</div>}
    {!listo && <div className="muted-label mt-3">Sincronizando datos del servidor…</div>}
    {datos.pedidos.some(p => !esHoy(p.timestamp)) && <div className="muted-label mt-2">Los indicadores de hoy usan la fecha del pedido registrada en Firestore.</div>}
  </>;
}
