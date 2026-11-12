import { useEffect, useMemo, useState } from 'react';
import {
  canal, codigo, dinero, fecha, fechaCorta, horaCorta, nombreEstado, pagoEnRevision,
  prioridad, type Datos, type Pedido,
} from '../domain';
import { cambiarEstadoPedido, obtenerComprobante, revisarPago } from '../repository';
import { EncabezadoPagina, Estado, Modal, Tarjeta, Vacio, botonBorde, botonSuave } from '../ui';

const localISO = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
type Ejecutar = (accion: () => Promise<unknown>, exito: string) => Promise<boolean>;

export default function Ventas({ datos, uid, demo, ejecutar, busquedaInicial = '' }: {
  datos: Datos; uid: string; demo: boolean; ejecutar: Ejecutar; busquedaInicial?: string;
}) {
  const [desde, setDesde] = useState('');
  const [hasta, setHasta] = useState('');
  const [rapido, setRapido] = useState('Todo');
  const [metodo, setMetodo] = useState('Todos');
  const [filtroCanal, setFiltroCanal] = useState('Todos');
  const [estado, setEstado] = useState('Todos');
  const [busqueda, setBusqueda] = useState(busquedaInicial);
  const [detalle, setDetalle] = useState<Pedido | null>(null);
  const [imagen, setImagen] = useState('');
  const [cargandoImagen, setCargandoImagen] = useState(false);
  const [decision, setDecision] = useState<'aprobar' | 'rechazar' | null>(null);
  const [mostrarMas, setMostrarMas] = useState(false);

  useEffect(() => { if (busquedaInicial) setBusqueda(busquedaInicial); }, [busquedaInicial]);
  useEffect(() => () => { if (imagen) URL.revokeObjectURL(imagen); }, [imagen]);

  const filtrados = useMemo(() => datos.pedidos.filter(p => {
    const f = fecha(p.timestamp);
    if (desde && (!f || localISO(f) < desde)) return false;
    if (hasta && (!f || localISO(f) > hasta)) return false;
    if (metodo !== 'Todos' && p.paymentMethod !== metodo) return false;
    if (filtroCanal !== 'Todos' && canal(p) !== filtroCanal) return false;
    if (estado === 'Cobrado' && p.paymentStatus !== 'PAGADO') return false;
    if (estado === 'Anulado' && p.status !== 'CANCELADO') return false;
    if (estado === 'En curso' && ['CANCELADO', 'ENTREGADO'].includes(p.status)) return false;
    if (estado === 'Revisar pago' && !pagoEnRevision(p)) return false;
    const q = busqueda.trim().toLocaleLowerCase();
    return !q || [p.id, codigo(p.id), p.customerName, p.phone, p.deliveryDriverName ?? '', p.waiterName ?? ''].some(s => s.toLocaleLowerCase().includes(q));
  }).sort((a, b) => prioridad(a) - prioridad(b) || (fecha(b.timestamp)?.getTime() ?? 0) - (fecha(a.timestamp)?.getTime() ?? 0)), [datos.pedidos, desde, hasta, metodo, filtroCanal, estado, busqueda]);

  const totalCobrado = filtrados.filter(p => p.status === 'ENTREGADO' && p.paymentStatus === 'PAGADO').reduce((s, p) => s + p.total, 0);

  function rango(nombre: string) {
    const hoy = new Date();
    const inicio = new Date(hoy);
    if (nombre === 'Ayer') inicio.setDate(inicio.getDate() - 1);
    if (nombre === 'Esta semana') inicio.setDate(inicio.getDate() - ((inicio.getDay() + 6) % 7));
    if (nombre === 'Este mes') inicio.setDate(1);
    setDesde(localISO(inicio));
    setHasta(localISO(nombre === 'Ayer' ? inicio : hoy));
    setRapido(nombre);
  }

  function cerrarDetalle() { setDetalle(null); setDecision(null); setImagen(''); }
  async function verImagen() {
    if (!detalle || demo) return;
    setCargandoImagen(true);
    try { setImagen(await obtenerComprobante(detalle.id)); }
    catch (e) { window.alert(e instanceof Error ? e.message : 'No se pudo abrir el comprobante.'); }
    finally { setCargandoImagen(false); }
  }

  async function decidirPago() {
    if (!detalle || !decision) return;
    const ok = await ejecutar(() => revisarPago(detalle.id, decision === 'aprobar', uid),
      decision === 'aprobar' ? 'Pago validado' : 'Se solicitó otro comprobante al cliente');
    if (ok) cerrarDetalle();
  }

  async function exportarExcel() {
    const writeExcelFile = (await import('write-excel-file/browser')).default;
    const cabecera = ['Pedido', 'Fecha', 'Hora', 'Cliente', 'Atendido por', 'Canal', 'Estado', 'Medio de pago', 'Pago', 'Monto S/', 'Comprobante emitido'];
    const filas = filtrados.map(p => [codigo(p.id), fechaCorta(p.timestamp), horaCorta(p.timestamp), p.customerName,
      p.waiterName || p.deliveryDriverName || 'Administración', canal(p), nombreEstado(p.status),
      p.paymentMethod, p.paymentStatus, p.total, p.receiptNumber || 'No registrado']);
    await writeExcelFile([cabecera, ...filas], { columns: [19, 18, 10, 28, 24, 17, 20, 20, 16, 16, 24].map(width => ({ width })),
      stickyRowsCount: 1, sheet: 'Ventas Yalazas' }, { fontFamily: 'Arial', fontSize: 11 })
      .toFile(`ventas-yalazas-${localISO(new Date())}.xlsx`);
  }

  async function exportarPDF() {
    const { PDFDocument, StandardFonts, rgb } = await import('pdf-lib');
    const pdf = await PDFDocument.create();
    const normal = await pdf.embedFont(StandardFonts.Helvetica);
    const negrita = await pdf.embedFont(StandardFonts.HelveticaBold);
    const limpio = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^\x20-\x7E]/g, '');
    const columnas = [38, 122, 238, 386, 474, 588, 731];
    let pagina = pdf.addPage([842, 595]);
    let y = 0;
    const iniciar = () => {
      pagina.drawRectangle({ x: 0, y: 535, width: 842, height: 60, color: rgb(.91, .11, .38) });
      pagina.drawText("YALAZA'S  /  REPORTE DE VENTAS", { x: 38, y: 562, size: 16, font: negrita, color: rgb(1, 1, 1) });
      pagina.drawText(limpio(`${desde || 'Inicio'} a ${hasta || 'actualidad'}  |  ${filtrados.length} pedidos`), { x: 38, y: 544, size: 9, font: normal, color: rgb(1, 1, 1) });
      ['Pedido', 'Fecha / hora', 'Cliente', 'Canal', 'Estado', 'Pago', 'Monto'].forEach((t, i) => pagina.drawText(t, { x: columnas[i], y: 513, size: 8, font: negrita }));
      y = 493;
    };
    iniciar();
    filtrados.forEach((p, indice) => {
      if (y < 45) { pagina = pdf.addPage([842, 595]); iniciar(); }
      if (indice % 2 === 0) pagina.drawRectangle({ x: 32, y: y - 5, width: 780, height: 20, color: rgb(.95, .96, .95) });
      const fila = [codigo(p.id), `${fechaCorta(p.timestamp)} ${horaCorta(p.timestamp)}`, p.customerName,
        canal(p), nombreEstado(p.status), `${p.paymentMethod} / ${p.paymentStatus}`, dinero(p.total)];
      fila.forEach((valor, i) => pagina.drawText(limpio(valor).slice(0, [15, 22, 25, 18, 18, 22, 16][i]),
        { x: columnas[i], y, size: 8, font: normal }));
      y -= 22;
    });
    if (y < 42) { pagina = pdf.addPage([842, 595]); iniciar(); }
    pagina.drawText(limpio(`Ventas completadas y pagadas: ${dinero(totalCobrado)}`), { x: 38, y: y - 12, size: 11, font: negrita });
    const bytes = await pdf.save();
    const url = URL.createObjectURL(new Blob([new Uint8Array(bytes)], { type: 'application/pdf' }));
    const enlace = document.createElement('a'); enlace.href = url; enlace.download = `ventas-yalazas-${localISO(new Date())}.pdf`; enlace.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  const siguientes: Record<string, string[]> = { PENDIENTE: ['EN_PROCESO', 'CANCELADO'], EN_PROCESO: ['LISTO_PARA_ENTREGA', 'CANCELADO'],
    LISTO_PARA_ENTREGA: ['EN_CAMINO', 'CANCELADO'], EN_CAMINO: ['ENTREGADO', 'CANCELADO'] };
  const accionesEstado = detalle?.serviceType === 'MESA' ? ['CANCELADO'].filter(s => (siguientes[detalle.status] ?? []).includes(s))
    : (siguientes[detalle?.status ?? ''] ?? []).filter(s => s !== 'ENTREGADO' || detalle?.paymentStatus === 'PAGADO');

  return <>
    <EncabezadoPagina etiqueta="OPERACIÓN Y REPORTES" titulo="Ventas y filtros"
      descripcion="Pedidos ordenados por atención urgente; filtra el historial y descarga reportes."
      acciones={<><button className={botonBorde} onClick={() => void exportarExcel().catch(e => window.alert(`No se pudo exportar Excel: ${e instanceof Error ? e.message : String(e)}`))}><i className="bi bi-file-earmark-spreadsheet me-2" />Excel</button><button className={botonBorde} onClick={() => void exportarPDF().catch(e => window.alert(`No se pudo exportar PDF: ${e instanceof Error ? e.message : String(e)}`))}><i className="bi bi-file-earmark-pdf me-2" />PDF</button></>} />
    <Tarjeta className="mb-3"><div className="row g-2 align-items-end">
      <div className="col-6 col-md-3 col-xl-2"><label className="form-label" htmlFor="ventas-desde">Desde</label><input id="ventas-desde" type="date" className="form-control" value={desde} onChange={e => { setDesde(e.target.value); setRapido(''); }} /></div>
      <div className="col-6 col-md-3 col-xl-2"><label className="form-label" htmlFor="ventas-hasta">Hasta</label><input id="ventas-hasta" type="date" className="form-control" value={hasta} onChange={e => { setHasta(e.target.value); setRapido(''); }} /></div>
      <div className="col-12 col-md-6 col-xl-5"><label className="form-label">Accesos rápidos</label><div className="filtro-rapido">{['Hoy', 'Ayer', 'Esta semana', 'Este mes'].map(n => <button key={n} className={rapido === n ? 'activo' : ''} onClick={() => rango(n)}>{n}</button>)}<button className={rapido === 'Todo' ? 'activo' : ''} onClick={() => { setDesde(''); setHasta(''); setRapido('Todo'); }}>Todo</button></div></div>
      <div className="col-12 col-xl-3"><label className="form-label" htmlFor="ventas-buscar">Buscar pedido o cliente</label><input id="ventas-buscar" className="form-control" placeholder="Código, nombre o teléfono" value={busqueda} onChange={e => setBusqueda(e.target.value)} /></div>
      <div className="col-6 col-lg-4"><label className="form-label" htmlFor="ventas-medio">Medio de pago</label><select id="ventas-medio" className="form-select" value={metodo} onChange={e => setMetodo(e.target.value)}>{['Todos', 'EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA'].map(x => <option key={x}>{x}</option>)}</select></div>
      <div className="col-6 col-lg-4"><label className="form-label" htmlFor="ventas-canal">Canal</label><select id="ventas-canal" className="form-select" value={filtroCanal} onChange={e => setFiltroCanal(e.target.value)}>{['Todos', 'Salón', 'Para llevar', 'Delivery'].map(x => <option key={x}>{x}</option>)}</select></div>
      <div className="col-12 col-lg-4"><label className="form-label" htmlFor="ventas-estado">Estado</label><select id="ventas-estado" className="form-select" value={estado} onChange={e => setEstado(e.target.value)}>{['Todos', 'Revisar pago', 'Cobrado', 'En curso', 'Anulado'].map(x => <option key={x}>{x}</option>)}</select></div>
    </div></Tarjeta>
    <Tarjeta title={`${filtrados.length} pedidos`} action={<span className="muted-label">Ventas completadas y pagadas: <strong className="text-lima">{dinero(totalCobrado)}</strong></span>}>
      {filtrados.length ? <><div className="tabla-wrap"><table className="tabla-y"><thead><tr><th>Pedido</th><th>Fecha / hora</th><th>Cliente / repartidor</th><th>Atendido por</th><th>Monto</th><th>Pago</th><th>Estado</th><th>Comprobante emitido</th><th>Acciones</th></tr></thead>
        <tbody>{filtrados.slice(0, mostrarMas ? undefined : 30).map(p => <tr key={p.id} className={pagoEnRevision(p) ? 'fila-prioritaria' : ''}>
          <td className="row-strong">{codigo(p.id)}</td><td>{fechaCorta(p.timestamp)}<div className="row-sub">{horaCorta(p.timestamp)} · {canal(p)}</div></td>
          <td>{p.customerName || 'Cliente'}<div className="row-sub">{p.deliveryDriverName || p.phone || '—'}</div></td>
          <td>{p.waiterName || '—'}</td><td className="row-strong">{dinero(p.total)}</td>
          <td><span className="pill-control">{p.paymentMethod || '—'}</span><div className="row-sub mt-1">{p.paymentStatus}</div></td>
          <td><Estado pedido={p} /></td><td>{p.receiptNumber || <span className="text-secondary">No registrado</span>}</td>
          <td><button className="btn-link-sutil" onClick={() => setDetalle(p)}>{pagoEnRevision(p) ? 'Revisar pago' : 'Ver ticket'}</button></td>
        </tr>)}</tbody></table></div>{filtrados.length > 30 && <button className={`${botonBorde} mt-3`} onClick={() => setMostrarMas(!mostrarMas)}>{mostrarMas ? 'Mostrar menos' : `Ver ${filtrados.length - 30} más`}</button>}</> : <Vacio icono="bi-search" titulo="No se encontraron pedidos" texto="Ajusta el rango de fechas o los filtros." />}
    </Tarjeta>
    {detalle && <Modal titulo={`Pedido ${codigo(detalle.id)}`} cerrar={cerrarDetalle} footer={<><button className={botonBorde} onClick={cerrarDetalle}>Cerrar</button><button className={botonSuave} onClick={() => window.print()}><i className="bi bi-printer me-1" />Imprimir ticket</button></>}>
      <div className="ticket-print"><div className="d-flex justify-content-between flex-wrap gap-2 mb-3"><div><strong>{detalle.customerName || 'Cliente'}</strong><div className="text-secondary">{fechaCorta(detalle.timestamp)} · {horaCorta(detalle.timestamp)} · {canal(detalle)}</div></div><Estado pedido={detalle} /></div>
        <div className="row g-2 mb-3"><div className="col-sm-6"><div className="receta-detalle"><strong>Entrega</strong><br />{detalle.serviceType === 'MESA' ? `Mesa ${detalle.tableNumber}` : detalle.address || 'Mostrador'}<br />{detalle.phone || 'Sin teléfono'}</div></div><div className="col-sm-6"><div className="receta-detalle"><strong>Pago</strong><br />{detalle.paymentMethod} · {detalle.paymentStatus}<br />{detalle.receiptNumber ? `Comprobante: ${detalle.receiptNumber}` : 'Boleta fiscal no registrada'}</div></div></div>
        <div className="tabla-wrap"><table className="tabla-y" style={{ minWidth: 0 }}><thead><tr><th>Producto</th><th>Cant.</th><th>Subtotal</th></tr></thead><tbody>{detalle.items.map((item, i) => <tr key={`${item.productId}-${i}`}><td>{item.name}<div className="row-sub">{item.selectedSauces?.join(', ')} {item.notes}</div></td><td>{item.quantity}</td><td>{dinero(item.subtotal)}</td></tr>)}</tbody></table></div>
        {detalle.deliveryFee > 0 && <div className="d-flex justify-content-between mt-2"><span>Delivery</span><strong>{dinero(detalle.deliveryFee)}</strong></div>}
        <div className="d-flex justify-content-between fs-5 fw-black mt-3 pt-2 border-top"><span>Total</span><span>{dinero(detalle.total)}</span></div>
        {detalle.comment && <p className="mt-2 mb-0">Nota: {detalle.comment}</p>}
      </div>
      {['YAPE', 'PLIN', 'TRANSFERENCIA'].includes(detalle.paymentMethod) && <div className="mt-3 pt-3 border-top"><h3 className="fs-6">Comprobante de pago del cliente</h3>
        <p className="text-secondary mb-2">Estado: {detalle.paymentProofStatus || 'Aún no enviado'}</p>
        {imagen ? <img className="img-fluid rounded border" style={{ maxHeight: 360 }} src={imagen} alt="Comprobante privado enviado por el cliente" /> : <button className={botonBorde} onClick={() => void verImagen()} disabled={demo || cargandoImagen}>{cargandoImagen ? 'Abriendo…' : 'Ver imagen privada'}</button>}
        {pagoEnRevision(detalle) && <div className="mt-3">{decision ? <div className="receta-detalle"><strong>{decision === 'aprobar' ? 'Confirmar pago validado' : 'Solicitar nueva imagen'}</strong><p className="mb-2 mt-1">{decision === 'aprobar' ? 'Comprueba el importe y destinatario antes de confirmar.' : 'El cliente verá que debe subir otro comprobante.'}</p><button className={decision === 'aprobar' ? botonSuave : 'btn btn-peligro'} onClick={() => void decidirPago()}>Confirmar</button><button className={`${botonBorde} ms-2`} onClick={() => setDecision(null)}>Volver</button></div> : <div className="d-flex gap-2 flex-wrap"><button className={botonSuave} disabled={demo || !imagen} onClick={() => setDecision('aprobar')}>Validar pago</button><button className="btn btn-peligro" disabled={demo || !imagen} onClick={() => setDecision('rechazar')}>Pedir otro comprobante</button></div>}</div>}
      </div>}
      {!!accionesEstado.length && <div className="mt-3 pt-3 border-top"><h3 className="fs-6">Cambiar estado</h3><div className="d-flex gap-2 flex-wrap">{accionesEstado.map(s => <button key={s} className={s === 'CANCELADO' ? 'btn btn-peligro' : botonBorde} disabled={demo} onClick={() => void ejecutar(() => cambiarEstadoPedido(detalle.id, s, uid), `Pedido actualizado: ${nombreEstado(s)}`).then(ok => { if (ok) cerrarDetalle(); })}>{s === 'CANCELADO' ? 'Anular pedido' : `Pasar a ${nombreEstado(s)}`}</button>)}</div></div>}
      <p className="muted-label mt-3 mb-0">El ticket resume el pedido. La emisión y reimpresión de boletas fiscales requieren una integración de facturación.</p>
    </Modal>}
  </>;
}
