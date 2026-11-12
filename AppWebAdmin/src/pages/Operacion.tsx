import { useEffect, useState } from 'react';
import {
  codigo, dinero, disponibilidad, fecha, fechaCorta, mesasOcupadas, precioProducto,
  type Datos, type Pagina, type Producto, type Usuario, ROLES,
} from '../domain';
import { agregarMesa, guardarUsuario, quitarMesa, registrarVentaPOS, type LineaPOS } from '../repository';
import { miniaturaCloudinary } from '../catalogo';
import { EncabezadoPagina, Estado, Modal, Tarjeta, Vacio, botonBorde, botonRosa, botonSuave } from '../ui';
import TarifaDeliveryAjustes from './TarifaDeliveryAjustes';

type Ejecutar = (accion: () => Promise<unknown>, exito: string) => Promise<boolean>;
type Props = { datos: Datos; uid: string; demo: boolean; ejecutar: Ejecutar; ir: (p: Pagina) => void };
const salsasYalazas = ['Acevichada', 'BBQ Clásica', 'Búfalo Picante', 'Maracuyá Dulce',
  'Honey Mustard', 'Mango Habanero', 'Ajo Parmesano', 'Tártara Especial'];

export function POS({ datos, uid, demo, ejecutar, ir }: Props) {
  const [buscar, setBuscar] = useState('');
  const [categoria, setCategoria] = useState('Todos');
  const [carrito, setCarrito] = useState<LineaPOS[]>([]);
  const [productoSalsa, setProductoSalsa] = useState<Producto | null>(null);
  const [salsas, setSalsas] = useState<string[]>([]);
  const [nota, setNota] = useState('');
  const [cliente, setCliente] = useState('');
  const [telefono, setTelefono] = useState('');
  const [metodo, setMetodo] = useState('EFECTIVO');
  const [recibido, setRecibido] = useState('');
  const [confirmar, setConfirmar] = useState(false);
  const categorias = ['Todos', ...new Set(datos.productos.map(p => p.category || 'Otros'))];
  const visibles = datos.productos.filter(p => (categoria === 'Todos' || (p.category || 'Otros') === categoria)
    && `${p.name} ${p.category}`.toLocaleLowerCase().includes(buscar.toLocaleLowerCase()));
  const total = Math.round(carrito.reduce((s, l) => s + (precioProducto(l.producto, datos)
    + Math.max(0, l.salsas.length - (l.producto.maxSauces || 2))) * l.cantidad, 0) * 100) / 100;

  function agregar(p: Producto, seleccion: string[] = [], notaItem = '') {
    setCarrito(actual => {
      const idx = actual.findIndex(x => x.producto.id === p.id && x.nota === notaItem
        && x.salsas.join('|') === seleccion.join('|'));
      if (idx < 0) return [...actual, { producto: p, cantidad: 1, salsas: seleccion, nota: notaItem }];
      return actual.map((x, i) => i === idx ? { ...x, cantidad: x.cantidad + 1 } : x);
    });
    setProductoSalsa(null); setSalsas([]); setNota('');
  }
  function cambiarCantidad(idx: number, delta: number) {
    setCarrito(actual => actual.map((x, i) => i === idx ? { ...x, cantidad: x.cantidad + delta } : x).filter(x => x.cantidad > 0));
  }
  async function confirmarVenta() {
    const ok = await ejecutar(async () => {
      const id = await registrarVentaPOS(carrito, metodo, Number(recibido), cliente, telefono, uid, datos);
      return id;
    }, 'Venta registrada y enviada a cocina');
    if (ok) { setCarrito([]); setCliente(''); setTelefono(''); setRecibido(''); setConfirmar(false); ir('ventas'); }
  }
  const opciones = productoSalsa ? (productoSalsa.availableSauces.length ? productoSalsa.availableSauces : salsasYalazas) : [];
  const recetaSalsa = datos.recetas.find(r => r.productId === productoSalsa?.id);
  const elegibles = opciones.filter(nombre => {
    if (!recetaSalsa?.sauceSlotsMl.length) return false;
    const i = datos.insumos.find(x => x.id === recetaSalsa.sauceIngredients[nombre]);
    return i && i.unit === 'ml' && i.stock >= recetaSalsa.sauceSlotsMl[0] && i.stock >= i.pauseThreshold;
  });

  return <>
    <EncabezadoPagina etiqueta="CAJA Y MOSTRADOR" titulo="Venta directa / POS"
      descripcion="Registra un cobro presencial y envía el pedido a cocina como pendiente."
      acciones={<span className="estado estado-normal"><i className="bi bi-shield-check me-1" />Pago confirmado por administración</span>} />
    <div className="row g-3"><div className="col-12 col-xl-8"><Tarjeta><div className="d-flex flex-wrap gap-2 mb-3"><input className="form-control flex-grow-1" style={{ width: 180 }} placeholder="Buscar en la carta" value={buscar} onChange={e => setBuscar(e.target.value)} /><select className="form-select" style={{ width: 190 }} value={categoria} onChange={e => setCategoria(e.target.value)}>{categorias.map(c => <option key={c}>{c}</option>)}</select></div><div className="row g-2">{visibles.length ? visibles.map(p => { const d = disponibilidad(p, datos); return <div className="col-6 col-md-4 col-xxl-3" key={p.id}><Tarjeta className="producto-card"><div className="producto-foto">{p.imageUrl ? <img src={miniaturaCloudinary(p.imageUrl, 400, 240)} alt="" /> : <i className="bi bi-egg-fried" />}</div><div className="producto-body"><h3>{p.name}</h3><p>{p.tagline || p.category}</p><div className="mt-auto"><div className="producto-precio mb-2">{dinero(precioProducto(p, datos))}</div><button className={`${d.disponible ? botonRosa : botonBorde} btn-sm w-100`} disabled={!d.disponible} onClick={() => p.allowsSauces ? setProductoSalsa(p) : agregar(p)}>{d.disponible ? '+ Agregar' : 'Agotado'}</button></div></div></Tarjeta></div>; }) : <div className="col-12"><Vacio titulo="Sin productos disponibles" /></div>}</div></Tarjeta></div>
      <div className="col-12 col-xl-4"><Tarjeta className="pos-carrito" title="Pedido en mostrador" action={<span className="estado estado-neutro">{carrito.reduce((s, x) => s + x.cantidad, 0)} artículos</span>}>
        {carrito.length ? carrito.map((l, idx) => <div className="data-row" key={`${l.producto.id}-${idx}`}><div className="me-2"><div className="data-row-title">{l.producto.name}</div><div className="data-row-sub">{l.salsas.join(', ') || l.nota || l.producto.category}</div><strong className="text-lima small">{dinero((precioProducto(l.producto, datos) + Math.max(0, l.salsas.length - (l.producto.maxSauces || 2))) * l.cantidad)}</strong></div><div className="cantidad-control"><button aria-label="Disminuir" onClick={() => cambiarCantidad(idx, -1)}>−</button><span>{l.cantidad}</span><button aria-label="Aumentar" onClick={() => cambiarCantidad(idx, 1)}>+</button></div></div>) : <Vacio icono="bi-cart3" titulo="El pedido está vacío" texto="Añade productos de la carta." />}
        <div className="d-flex justify-content-between align-items-center border-top pt-3 mt-3"><span className="text-secondary">Total a cobrar</span><strong className="fs-4 text-lima">{dinero(total)}</strong></div><button className={`${botonRosa} w-100 mt-3`} disabled={!carrito.length || demo} onClick={() => setConfirmar(true)}>Continuar al cobro <i className="bi bi-arrow-right ms-1" /></button>
        <p className="muted-label mt-2 mb-0">Los pedidos registrados aparecen en cocina. La venta cuenta en reportes al entregarse.</p>
      </Tarjeta></div></div>
    {productoSalsa && <Modal ancho="modal-sm" titulo={`Personalizar · ${productoSalsa.name}`} cerrar={() => { setProductoSalsa(null); setSalsas([]); }} footer={<><button className={botonBorde} onClick={() => setProductoSalsa(null)}>Cancelar</button><button className={botonRosa} disabled={!salsas.length} onClick={() => agregar(productoSalsa, salsas, nota)}>Agregar al pedido</button></>}><p className="text-secondary">Incluye {productoSalsa.maxSauces || 2} salsa(s). Cada adicional cuesta S/ 1.</p>{elegibles.map(nombre => <label className="d-flex gap-2 align-items-center py-2 border-bottom" key={nombre}><input className="form-check-input" type="checkbox" checked={salsas.includes(nombre)} onChange={e => setSalsas(e.target.checked ? [...salsas, nombre] : salsas.filter(x => x !== nombre))} />{nombre}</label>)}{!elegibles.length && <Vacio titulo="No hay salsas disponibles" />}<label className="form-label mt-3">Nota para cocina</label><input className="form-control" value={nota} onChange={e => setNota(e.target.value)} placeholder="Ej. sin picante" /></Modal>}
    {confirmar && <Modal ancho="modal-md" titulo="Confirmar venta directa" cerrar={() => setConfirmar(false)} footer={<><button className={botonBorde} onClick={() => setConfirmar(false)}>Volver</button><button className={botonRosa} onClick={() => void confirmarVenta()}>Confirmar pago y registrar pedido</button></>}><div className="d-flex justify-content-between fs-5 fw-black mb-3"><span>Total</span><span className="text-lima">{dinero(total)}</span></div><div className="row g-3"><div className="col-sm-6"><label className="form-label">Nombre del cliente (opcional)</label><input className="form-control" value={cliente} onChange={e => setCliente(e.target.value)} placeholder="Mostrador" /></div><div className="col-sm-6"><label className="form-label">Teléfono (opcional)</label><input className="form-control" value={telefono} onChange={e => setTelefono(e.target.value)} /></div><div className="col-sm-6"><label className="form-label">Medio de pago recibido</label><select className="form-select" value={metodo} onChange={e => setMetodo(e.target.value)}>{['EFECTIVO', 'YAPE', 'PLIN', 'TRANSFERENCIA', 'TARJETA'].map(m => <option key={m}>{m}</option>)}</select></div>{metodo === 'EFECTIVO' && <div className="col-sm-6"><label className="form-label">Efectivo recibido S/</label><input className="form-control" type="number" min={total} step="0.01" value={recibido} onChange={e => setRecibido(e.target.value)} /></div>}</div><p className="muted-label mt-3 mb-0">Confirma solo cuando hayas recibido el dinero o comprobado el pago en caja.</p></Modal>}
  </>;
}

export function Clientes({ datos, uid, demo, ejecutar, busquedaInicial = '' }: Props & { busquedaInicial?: string }) {
  const [buscar, setBuscar] = useState('');
  useEffect(() => { setBuscar(busquedaInicial); }, [busquedaInicial]);
  const [seleccionado, setSeleccionado] = useState<Usuario | null>(null);
  const clientes = datos.usuarios.filter(u => u.role === 'CLIENTE' && `${u.name} ${u.email} ${u.phone}`.toLocaleLowerCase().includes(buscar.toLocaleLowerCase())).sort((a, b) => a.name.localeCompare(b.name, 'es'));
  const pedidosCliente = seleccionado ? datos.pedidos.filter(p => p.userId === seleccionado.uid).sort((a, b) => (fecha(b.timestamp)?.getTime() ?? 0) - (fecha(a.timestamp)?.getTime() ?? 0)) : [];
  return <><EncabezadoPagina etiqueta="PERSONAS" titulo="Clientes" descripcion="Consulta datos de contacto e historial de pedidos de las cuentas registradas." /><Tarjeta><div className="d-flex justify-content-between gap-2 align-items-center mb-3"><strong>{clientes.length} clientes</strong><input className="form-control" style={{ maxWidth: 280 }} placeholder="Buscar nombre, correo o teléfono" value={buscar} onChange={e => setBuscar(e.target.value)} /></div>{clientes.length ? <div className="tabla-wrap"><table className="tabla-y" style={{ minWidth: 650 }}><thead><tr><th>Cliente</th><th>Contacto</th><th>Pedidos</th><th>Cuenta</th><th></th></tr></thead><tbody>{clientes.map(u => <tr key={u.uid}><td className="row-strong">{u.name || 'Sin nombre'}</td><td>{u.email}<div className="row-sub">{u.phone}</div></td><td>{datos.pedidos.filter(p => p.userId === u.uid).length}</td><td><span className={`estado ${u.active ? 'estado-normal' : 'estado-cancelado'}`}>{u.active ? 'Activa' : 'Inactiva'}</span></td><td><button className="btn-link-sutil" onClick={() => setSeleccionado(u)}>Ver perfil</button></td></tr>)}</tbody></table></div> : <Vacio titulo="No hay clientes" texto="Ajusta la búsqueda o espera nuevos registros." />}</Tarjeta>
    {seleccionado && <Modal titulo={seleccionado.name || 'Cliente'} cerrar={() => setSeleccionado(null)} footer={<button className={botonBorde} onClick={() => setSeleccionado(null)}>Cerrar</button>}><div className="row g-3"><div className="col-sm-6"><div className="receta-detalle"><strong>Contacto</strong><br />{seleccionado.email}<br />{seleccionado.phone || 'Sin teléfono'}<br />{seleccionado.address || 'Sin dirección'}</div></div><div className="col-sm-6"><div className="receta-detalle"><strong>Actividad</strong><br />{pedidosCliente.length} pedidos registrados<br />{pedidosCliente.filter(p => p.status === 'ENTREGADO').length} entregados</div></div></div><h3 className="fs-6 mt-4">Últimos pedidos</h3>{pedidosCliente.slice(0, 6).map(p => <div className="data-row" key={p.id}><span>{codigo(p.id)} · {fechaCorta(p.timestamp)}</span><span>{dinero(p.total)} <Estado pedido={p} /></span></div>)}{!pedidosCliente.length && <Vacio titulo="Sin pedidos" />}<button className={`${seleccionado.active ? 'btn btn-peligro' : botonSuave} mt-3`} disabled={demo} onClick={() => void ejecutar(() => guardarUsuario(seleccionado, { role: seleccionado.role, active: !seleccionado.active, vehicleType: '', vehiclePlate: '', vehicleModel: '' }, uid), seleccionado.active ? 'Cuenta desactivada' : 'Cuenta activada').then(ok => { if (ok) setSeleccionado(null); })}>{seleccionado.active ? 'Desactivar cuenta' : 'Activar cuenta'}</button></Modal>}
  </>;
}

export function Repartidores({ datos, uid, demo, ejecutar }: Props) {
  const [editar, setEditar] = useState<Usuario | null>(null);
  const repartidores = datos.usuarios.filter(u => u.role === 'REPARTIDOR');
  return <><EncabezadoPagina etiqueta="FLOTA DE DELIVERY" titulo="Repartidores" descripcion="Rutas asignadas en tiempo real y datos del vehículo administrados aquí." />
    <div className="row g-3">{repartidores.length ? repartidores.map(u => { const rutas = datos.pedidos.filter(p => p.deliveryDriverId === u.uid && p.status === 'EN_CAMINO'); return <div className="col-12 col-md-6 col-xl-4" key={u.uid}><Tarjeta><div className="d-flex align-items-start gap-3"><div className="avatar" style={{ width: 48, height: 48, fontSize: 17 }}>{(u.name || 'R').charAt(0).toUpperCase()}</div><div className="flex-grow-1"><h3 className="fs-6 fw-black mb-1">{u.name || 'Repartidor'}</h3><div className="muted-label">{u.phone || 'Sin teléfono'}</div></div><span className={`estado ${!u.active ? 'estado-cancelado' : rutas.length ? 'estado-en_camino' : 'estado-neutro'}`}>{!u.active ? 'Inactivo' : rutas.length ? 'En ruta' : 'Sin ruta activa'}</span></div><div className="receta-detalle mt-3">{u.vehicleType || 'Vehículo no registrado'} · {u.vehiclePlate || 'Sin placa'}<br />{u.vehicleModel || 'Sin modelo / color'}</div><div className="muted-label mt-2">{rutas.length ? `Pedidos: ${rutas.map(p => codigo(p.id)).join(', ')}` : 'Sin entregas asignadas ahora'}</div><button className={`${botonBorde} mt-3`} onClick={() => setEditar({ ...u })}>Editar vehículo</button></Tarjeta></div>; }) : <div className="col-12"><Tarjeta><Vacio titulo="Sin repartidores registrados" /></Tarjeta></div>}</div><p className="muted-label mt-3">La app Android guarda «en descanso» solo en el teléfono. Aquí se muestra la ruta activa y si la cuenta está habilitada; no se deduce disponibilidad de turno.</p>
    {editar && <Modal ancho="modal-md" titulo={`Vehículo · ${editar.name}`} cerrar={() => setEditar(null)} footer={<><button className={botonBorde} onClick={() => setEditar(null)}>Cancelar</button><button className={botonRosa} disabled={demo} onClick={() => void ejecutar(() => guardarUsuario(datos.usuarios.find(u => u.uid === editar.uid)!, { role: 'REPARTIDOR', active: editar.active, vehicleType: editar.vehicleType || '', vehiclePlate: editar.vehiclePlate || '', vehicleModel: editar.vehicleModel || '' }, uid), 'Vehículo actualizado').then(ok => { if (ok) setEditar(null); })}>Guardar</button></>}><div className="row g-3"><div className="col-sm-6"><label className="form-label">Tipo de vehículo</label><input className="form-control" value={editar.vehicleType || ''} onChange={e => setEditar({ ...editar, vehicleType: e.target.value })} placeholder="Moto" /></div><div className="col-sm-6"><label className="form-label">Placa</label><input className="form-control" value={editar.vehiclePlate || ''} onChange={e => setEditar({ ...editar, vehiclePlate: e.target.value })} /></div><div className="col-12"><label className="form-label">Modelo / color</label><input className="form-control" value={editar.vehicleModel || ''} onChange={e => setEditar({ ...editar, vehicleModel: e.target.value })} /></div></div></Modal>}
  </>;
}

export function Mesas({ datos, demo, ejecutar }: Props) {
  const [nueva, setNueva] = useState(false);
  const [numero, setNumero] = useState('');
  const [quitar, setQuitar] = useState<number | null>(null);
  const ocupadas = mesasOcupadas(datos);
  return <><EncabezadoPagina etiqueta="SALÓN" titulo="Mesas del local" descripcion="Administra las mesas visibles para el mesero y consulta su ocupación."
    acciones={<button className={botonRosa} onClick={() => { setNumero(String(Array.from({ length: 200 }, (_, i) => i + 1).find(n => !datos.mesas.includes(n)) ?? '')); setNueva(true); }}><i className="bi bi-plus-lg me-1" />Agregar mesa</button>} />
    <div className="row g-3 mb-3"><div className="col-4"><Tarjeta className="kpi kpi-rosa"><div className="kpi-label">Total</div><div className="kpi-value">{datos.mesas.length}</div></Tarjeta></div><div className="col-4"><Tarjeta className="kpi kpi-lima"><div className="kpi-label">Libres</div><div className="kpi-value text-lima">{datos.mesas.length - datos.mesas.filter(n => ocupadas.has(n)).length}</div></Tarjeta></div><div className="col-4"><Tarjeta className="kpi kpi-oro"><div className="kpi-label">Ocupadas</div><div className="kpi-value text-oro">{datos.mesas.filter(n => ocupadas.has(n)).length}</div></Tarjeta></div></div>
    <div className="row g-3">{datos.mesas.length ? [...datos.mesas].sort((a, b) => a - b).map(n => { const ocupada = ocupadas.has(n); const comandas = datos.pedidos.filter(p => p.serviceType === 'MESA' && p.tableNumber === n && !['ENTREGADO', 'CANCELADO'].includes(p.status)); return <div className="col-6 col-md-4 col-xl-3 col-xxl-2" key={n}><Tarjeta className={`mesa-card ${ocupada ? 'ocupada' : 'libre'}`}><i className="bi bi-table mesa-icono" /><div className="d-flex justify-content-between align-items-center mt-2"><strong>Mesa {n}</strong><span className={`estado ${ocupada ? 'estado-revision' : 'estado-normal'}`}>{ocupada ? 'Ocupada' : 'Libre'}</span></div><div className="muted-label mt-1">{ocupada ? `${comandas.length} comanda(s) en curso` : 'Lista para nuevos pedidos'}</div><button className="btn-link-sutil mt-2" disabled={ocupada || demo} onClick={() => setQuitar(n)}>Retirar mesa</button></Tarjeta></div>; }) : <div className="col-12"><Tarjeta><Vacio titulo="Aún no hay mesas" /></Tarjeta></div>}</div>
    {nueva && <Modal ancho="modal-sm" titulo="Agregar mesa" cerrar={() => setNueva(false)} footer={<><button className={botonBorde} onClick={() => setNueva(false)}>Cancelar</button><button className={botonRosa} disabled={demo} onClick={() => void ejecutar(() => agregarMesa(Number(numero)), 'Mesa agregada').then(ok => { if (ok) setNueva(false); })}>Agregar</button></>}><label className="form-label">Número de mesa (1–200)</label><input autoFocus className="form-control" type="number" min="1" max="200" value={numero} onChange={e => setNumero(e.target.value)} /><p className="muted-label mt-2">Aparecerá en el selector del mesero al guardar.</p></Modal>}
    {quitar !== null && <Modal ancho="modal-sm" titulo={`Retirar Mesa ${quitar}`} cerrar={() => setQuitar(null)} footer={<><button className={botonBorde} onClick={() => setQuitar(null)}>Cancelar</button><button className="btn btn-peligro" disabled={demo} onClick={() => void ejecutar(() => quitarMesa(quitar), 'Mesa retirada').then(ok => { if (ok) setQuitar(null); })}>Retirar</button></>}><p>La mesa dejará de estar disponible para nuevas comandas. Los pedidos históricos conservarán su número.</p></Modal>}
  </>;
}

export function Ajustes({ datos, uid, demo, ejecutar, ir, busquedaInicial = '' }: Props & { busquedaInicial?: string }) {
  const [buscar, setBuscar] = useState('');
  useEffect(() => { setBuscar(busquedaInicial); }, [busquedaInicial]);
  const [editar, setEditar] = useState<Usuario | null>(null);
  const usuarios = datos.usuarios.filter(u => `${u.name} ${u.email} ${u.role}`.toLocaleLowerCase().includes(buscar.toLocaleLowerCase()));
  return <><EncabezadoPagina etiqueta="CONFIGURACIÓN" titulo="Ajustes y accesos" descripcion="Gestiona roles y cuentas del personal sin cambiar los identificadores de Firestore." />
    <div className="row g-3 mb-3"><div className="col-md-6"><Tarjeta title="Sucursal activa"><div className="fs-5 fw-black">San Antonio</div><p className="muted-label mt-2 mb-0">El proyecto actual registra una sola sucursal. El selector superior muestra esta sede.</p></Tarjeta></div><div className="col-md-6"><Tarjeta title="Salón y operación"><p className="text-secondary small">La configuración de mesas se comparte en tiempo real con la app del mesero.</p><button className={botonBorde} onClick={() => ir('mesas')}>Administrar mesas <i className="bi bi-arrow-right ms-1" /></button></Tarjeta></div></div>
    <div className="mb-3"><TarifaDeliveryAjustes tarifa={datos.tarifa} uid={uid} demo={demo} ejecutar={ejecutar} /></div>
    <Tarjeta title="Usuarios y permisos" action={<input className="form-control" style={{ maxWidth: 250 }} placeholder="Buscar usuario" value={buscar} onChange={e => setBuscar(e.target.value)} />}>
      {usuarios.length ? <div className="tabla-wrap"><table className="tabla-y" style={{ minWidth: 650 }}><thead><tr><th>Usuario</th><th>Rol</th><th>Cuenta</th><th>Vehículo</th><th></th></tr></thead><tbody>{usuarios.map(u => <tr key={u.uid}><td className="row-strong">{u.name || u.email}<div className="row-sub">{u.email}</div></td><td>{u.role}</td><td><span className={`estado ${u.active ? 'estado-normal' : 'estado-cancelado'}`}>{u.active ? 'Activa' : 'Inactiva'}</span></td><td>{u.role === 'REPARTIDOR' ? u.vehiclePlate || 'Sin registrar' : '—'}</td><td><button className="btn-link-sutil" disabled={u.uid === uid || demo} onClick={() => setEditar({ ...u })}>Administrar</button></td></tr>)}</tbody></table></div> : <Vacio titulo="Sin usuarios" />}
    </Tarjeta><p className="muted-label mt-3">Las cuentas nuevas se crean mediante Firebase Authentication y se registran inicialmente como clientes. Aquí puedes cambiar su rol y activación.</p>
    {editar && <Modal ancho="modal-md" titulo={`Administrar · ${editar.name || editar.email}`} cerrar={() => setEditar(null)} footer={<><button className={botonBorde} onClick={() => setEditar(null)}>Cancelar</button><button className={botonRosa} onClick={() => void ejecutar(() => guardarUsuario(datos.usuarios.find(u => u.uid === editar.uid)!, { role: editar.role, active: editar.active, vehicleType: editar.vehicleType || '', vehiclePlate: editar.vehiclePlate || '', vehicleModel: editar.vehicleModel || '' }, uid), 'Cuenta actualizada').then(ok => { if (ok) setEditar(null); })}>Guardar cambios</button></>}><div className="row g-3"><div className="col-sm-6"><label className="form-label">Rol</label><select className="form-select" value={editar.role === 'ADMIN' ? 'ADMINISTRADOR' : editar.role} onChange={e => setEditar({ ...editar, role: e.target.value })}>{ROLES.map(r => <option key={r}>{r}</option>)}</select></div><div className="col-sm-6 d-flex align-items-end"><label className="form-check mb-2"><input className="form-check-input" type="checkbox" checked={editar.active} onChange={e => setEditar({ ...editar, active: e.target.checked })} /> <span className="form-check-label">Cuenta activa</span></label></div>{editar.role === 'REPARTIDOR' && <><div className="col-sm-6"><label className="form-label">Tipo de vehículo</label><input className="form-control" value={editar.vehicleType || ''} onChange={e => setEditar({ ...editar, vehicleType: e.target.value })} /></div><div className="col-sm-6"><label className="form-label">Placa</label><input className="form-control" value={editar.vehiclePlate || ''} onChange={e => setEditar({ ...editar, vehiclePlate: e.target.value })} /></div><div className="col-12"><label className="form-label">Modelo / color</label><input className="form-control" value={editar.vehicleModel || ''} onChange={e => setEditar({ ...editar, vehicleModel: e.target.value })} /></div></>}</div><p className="muted-label mt-3">Solo administradores pueden guardar estos cambios. No puedes modificar tu propia cuenta desde aquí.</p></Modal>}
  </>;
}
