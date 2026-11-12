import { useMemo, useState } from 'react';
import {
  cantidadInsumo, dinero, disponibilidad, estadoInsumo, precioProducto,
  type Datos, type Insumo, type Producto, type Receta,
} from '../domain';
import {
  ajustarStock, eliminarProducto, guardarInsumo, guardarProducto, guardarPromocion,
  guardarReceta, pausarProducto, registrarEntrada, subirFotoProducto,
} from '../repository';
import { EncabezadoPagina, Modal, Tarjeta, Vacio, botonBorde, botonRosa, botonSuave } from '../ui';

type Ejecutar = (accion: () => Promise<unknown>, exito: string) => Promise<boolean>;
const productoVacio = (): Producto => ({ id: '', name: '', tagline: '', description: '', price: 0, originalPrice: 0,
  isOffer: false, offerTag: '', imageUrl: '', category: '', isAvailable: true, allowsSauces: false,
  maxSauces: 0, availableSauces: [] });
const insumoVacio = (): Insumo => ({ id: '', name: '', unit: 'u', stock: 0, lowThreshold: 0, pauseThreshold: 0 });
const factor = (i: Insumo) => i.unit === 'g' ? 1000 : 1;
const unidadCompra = (i: Insumo) => i.unit === 'g' ? 'kg' : i.unit === 'ml' ? 'ml' : 'unid.';

export default function Inventario({ datos, demo, ejecutar, avisar }: {
  datos: Datos; demo: boolean; ejecutar: Ejecutar; avisar: (m: string, error?: boolean) => void;
}) {
  const [tab, setTab] = useState<'productos' | 'insumos' | 'recetas'>('productos');
  const [buscar, setBuscar] = useState('');
  const [producto, setProducto] = useState<Producto | null>(null);
  const [salsasTexto, setSalsasTexto] = useState('');
  const [insumo, setInsumo] = useState<Insumo | null>(null);
  const [movimiento, setMovimiento] = useState<{ insumo: Insumo; tipo: 'entrada' | 'ajuste' } | null>(null);
  const [cantidad, setCantidad] = useState('');
  const [receta, setReceta] = useState<Receta | null>(null);
  const [productoReceta, setProductoReceta] = useState<Producto | null>(null);
  const [promocion, setPromocion] = useState<Producto | null>(null);
  const [precioOferta, setPrecioOferta] = useState('');
  const [tagOferta, setTagOferta] = useState('Oferta');
  const [subiendoFoto, setSubiendoFoto] = useState(false);
  const [confirmarEliminar, setConfirmarEliminar] = useState(false);

  const productos = useMemo(() => datos.productos.filter(p => `${p.name} ${p.category}`.toLocaleLowerCase().includes(buscar.toLocaleLowerCase()))
    .sort((a, b) => a.name.localeCompare(b.name, 'es')), [datos.productos, buscar]);
  const insumos = useMemo(() => datos.insumos.filter(i => i.name.toLocaleLowerCase().includes(buscar.toLocaleLowerCase()))
    .sort((a, b) => ({ critico: 0, bajo: 1, normal: 2 })[estadoInsumo(a)] - ({ critico: 0, bajo: 1, normal: 2 })[estadoInsumo(b)] || a.name.localeCompare(b.name, 'es')),
  [datos.insumos, buscar]);

  function abrirReceta(p: Producto) {
    const actual = datos.recetas.find(r => r.productId === p.id);
    setProductoReceta(p);
    setReceta(actual ? { ...actual, ingredients: actual.ingredients.map(i => ({ ...i })), sauceSlotsMl: [...actual.sauceSlotsMl], sauceIngredients: { ...actual.sauceIngredients } }
      : { productId: p.id, ingredients: [], sauceSlotsMl: p.allowsSauces ? Array(Math.min(8, Math.max(1, p.maxSauces))).fill(25) : [], sauceIngredients: {} });
  }

  function abrirProducto(p: Producto) {
    setProducto(p);
    setSalsasTexto(p.availableSauces.join(', '));
    setConfirmarEliminar(false);
  }

  function cantidadReceta(id: string) { return receta?.ingredients.find(i => i.ingredientId === id)?.quantity ?? 0; }
  function cambiarCantidadReceta(id: string, valor: number) {
    setReceta(r => r ? { ...r, ingredients: [...r.ingredients.filter(i => i.ingredientId !== id), ...(valor > 0 ? [{ ingredientId: id, quantity: valor }] : [])] } : null);
  }

  async function foto(file?: File) {
    if (!file) return;
    if (demo) { avisar('Conecta Firebase y Cloudinary para subir fotos.', true); return; }
    setSubiendoFoto(true);
    try { const url = await subirFotoProducto(file); setProducto(p => p ? { ...p, imageUrl: url } : p); avisar('Imagen pública subida'); }
    catch (e) { avisar(e instanceof Error ? e.message : 'No se pudo subir la imagen.', true); }
    finally { setSubiendoFoto(false); }
  }

  function abrirMovimiento(i: Insumo, tipo: 'entrada' | 'ajuste') {
    setMovimiento({ insumo: i, tipo });
    setCantidad(tipo === 'ajuste' ? String(i.stock / factor(i)) : '');
  }

  return <>
    <EncabezadoPagina etiqueta="CARTA Y ALMACÉN" titulo="Inventario inteligente"
      descripcion="Productos, existencias y recetas vinculadas al mismo catálogo de la app Android."
      acciones={<button className={botonRosa} onClick={() => tab === 'productos' ? abrirProducto(productoVacio()) : tab === 'insumos' ? setInsumo(insumoVacio()) : datos.productos[0] && abrirReceta(datos.productos[0])}>
        <i className="bi bi-plus-lg me-2" />{tab === 'productos' ? 'Nuevo producto' : tab === 'insumos' ? 'Nuevo insumo' : 'Configurar receta'}</button>} />
    <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
      <div className="filtro-rapido">{([['productos', 'Carta y productos'], ['insumos', 'Insumos'], ['recetas', 'Recetas']] as const).map(([id, label]) => <button key={id} className={tab === id ? 'activo' : ''} onClick={() => { setTab(id); setBuscar(''); }}>{label}</button>)}</div>
      <input className="form-control" style={{ maxWidth: 250 }} placeholder={`Buscar ${tab}…`} value={buscar} onChange={e => setBuscar(e.target.value)} aria-label="Buscar en inventario" />
    </div>
    {tab === 'productos' && <div className="row g-3">{productos.length ? productos.map(p => { const d = disponibilidad(p, datos); const promo = datos.promociones.find(x => x.productId === p.id && x.active); return <div className="col-12 col-sm-6 col-xl-4 col-xxl-3" key={p.id}><Tarjeta className="producto-card"><div className="producto-foto">{p.imageUrl ? <img src={p.imageUrl} alt={p.name} /> : <i className="bi bi-egg-fried" />}</div><div className="producto-body"><div className="d-flex align-items-start justify-content-between gap-2"><h3>{p.name}</h3><span className={`estado ${d.disponible ? 'estado-normal' : 'estado-critico'}`}>{d.disponible ? 'Visible' : 'Pausado'}</span></div><p>{p.category || 'Sin categoría'} · {p.tagline || p.description || 'Producto de la carta'}</p><div className="mt-auto"><div className="d-flex align-items-center gap-2"><span className="producto-precio">{dinero(precioProducto(p, datos))}</span>{promo && <span className="estado estado-revision">Oferta</span>}</div><div className="muted-label mt-1">{datos.recetas.some(r => r.productId === p.id) ? `Rendimiento: ${d.porciones} porciones · ${d.limite}` : 'Disponibilidad manual'}</div><div className="d-flex flex-wrap gap-1 mt-3"><button className={`${botonBorde} btn-sm`} onClick={() => abrirProducto({ ...p })}>Editar</button><button className={`${botonBorde} btn-sm`} onClick={() => abrirReceta(p)}>Receta</button><button className={`${botonBorde} btn-sm`} onClick={() => { setPromocion(p); setPrecioOferta(String(promo?.offerPrice ?? '')); setTagOferta(promo?.offerTag ?? 'Oferta'); }}>Oferta</button><button className={`${p.isAvailable ? 'btn btn-peligro' : botonSuave} btn-sm`} disabled={demo} onClick={() => void ejecutar(() => pausarProducto(p, p.isAvailable), p.isAvailable ? 'Producto pausado' : 'Producto activado')}>{p.isAvailable ? 'Pausar' : 'Activar'}</button></div></div></div></Tarjeta></div>; }) : <div className="col-12"><Tarjeta><Vacio titulo="No hay productos" texto="Crea un producto o cambia la búsqueda." /></Tarjeta></div>}</div>}
    {tab === 'insumos' && <div className="row g-3">{insumos.length ? insumos.map(i => { const estado = estadoInsumo(i); const uso = datos.recetas.flatMap(r => r.ingredients).find(x => x.ingredientId === i.id)?.quantity; const porciones = uso ? Math.floor(i.stock / uso) : null; return <div className="col-12 col-md-6 col-xl-4" key={i.id}><Tarjeta><div className="d-flex justify-content-between gap-2 align-items-start"><div><h3 className="fs-6 fw-black mb-1">{i.name}</h3><strong className="fs-4">{cantidadInsumo(i)}</strong></div><span className={`estado estado-${estado}`}>{estado === 'normal' ? 'Normal' : estado === 'bajo' ? 'Stock bajo' : 'Crítico'}</span></div><div className="muted-label mt-2">{porciones === null ? 'Sin receta vinculada' : `~${porciones} porciones estimadas`} · Mínimo: {cantidadInsumo(i, i.lowThreshold)}</div><div className={`nivel-stock ${estado} mt-3`}><span style={{ width: `${Math.min(100, i.lowThreshold > 0 ? i.stock / i.lowThreshold * 60 : 100)}%` }} /></div><div className="d-flex flex-wrap gap-2 mt-3"><button className={`${estado === 'critico' ? botonRosa : botonBorde} btn-sm`} onClick={() => abrirMovimiento(i, 'entrada')}>+ Registrar entrada</button><button className={`${botonBorde} btn-sm`} onClick={() => abrirMovimiento(i, 'ajuste')}>Ajustar</button><button className={`${botonBorde} btn-sm`} onClick={() => setInsumo({ ...i })}>Umbrales</button></div></Tarjeta></div>; }) : <div className="col-12"><Tarjeta><Vacio titulo="No hay insumos" texto="Registra la materia prima para calcular el rendimiento." /></Tarjeta></div>}</div>}
    {tab === 'recetas' && <div className="row g-3">{datos.productos.filter(p => p.name.toLocaleLowerCase().includes(buscar.toLocaleLowerCase())).map(p => { const r = datos.recetas.find(x => x.productId === p.id); const d = disponibilidad(p, datos); return <div className="col-12 col-md-6 col-xl-4" key={p.id}><Tarjeta><div className="d-flex justify-content-between align-items-start gap-2"><div><h3 className="fs-6 fw-black mb-1">{p.name}</h3><span className="muted-label">{p.category || 'Carta'}</span></div><span className={`estado ${r ? d.disponible ? 'estado-normal' : 'estado-critico' : 'estado-neutro'}`}>{r ? d.disponible ? 'Disponible' : 'Pausado' : 'Sin receta'}</span></div><div className="receta-detalle mt-3">{r ? <><strong>{d.porciones} porciones posibles</strong><br />Límite: {d.limite}<div className="mt-2">{r.ingredients.map(x => { const i = datos.insumos.find(y => y.id === x.ingredientId); return `${i?.name || 'Insumo faltante'}: ${x.quantity} ${i?.unit || ''}`; }).join(' · ') || 'Solo salsas configuradas'}</div>{r.sauceSlotsMl.length > 0 && <div className="mt-1 text-rosa">{r.sauceSlotsMl.map((n, j) => `Salsa ${j + 1}: ${n} ml`).join(' · ')}</div>}</> : 'Configura los insumos consumidos por cada porción.'}</div><button className={`${botonBorde} mt-3`} onClick={() => abrirReceta(p)}>{r ? 'Editar receta' : 'Configurar receta'}</button></Tarjeta></div>; })}</div>}

    {producto && <Modal titulo={producto.id ? `Editar ${producto.name}` : 'Nuevo producto'} cerrar={() => setProducto(null)} footer={<><button className={botonBorde} onClick={() => setProducto(null)}>Cancelar</button><button className={botonRosa} disabled={demo || subiendoFoto} onClick={() => void ejecutar(() => guardarProducto({ ...producto, availableSauces: salsasTexto.split(',').map(x => x.trim()).filter(Boolean) }), 'Producto guardado').then(ok => { if (ok) setProducto(null); })}>Guardar producto</button></>}>
      <div className="row g-3"><div className="col-md-7"><label className="form-label">Nombre</label><input className="form-control" value={producto.name} onChange={e => setProducto({ ...producto, name: e.target.value })} /></div><div className="col-md-5"><label className="form-label">Categoría</label><input className="form-control" value={producto.category} onChange={e => setProducto({ ...producto, category: e.target.value })} /></div><div className="col-md-8"><label className="form-label">Frase corta</label><input className="form-control" value={producto.tagline} onChange={e => setProducto({ ...producto, tagline: e.target.value })} /></div><div className="col-md-4"><label className="form-label">Precio S/</label><input className="form-control" type="number" min="0" step="0.01" value={producto.price || ''} onChange={e => setProducto({ ...producto, price: Number(e.target.value) })} /></div><div className="col-12"><label className="form-label">Descripción</label><textarea className="form-control" rows={2} value={producto.description} onChange={e => setProducto({ ...producto, description: e.target.value })} /></div>
      <div className="col-md-8"><label className="form-label">URL de imagen pública</label><input className="form-control" value={producto.imageUrl} onChange={e => setProducto({ ...producto, imageUrl: e.target.value })} placeholder="https://..." /></div><div className="col-md-4"><label className="form-label">O subir foto</label><input className="form-control" type="file" accept="image/*" disabled={subiendoFoto || demo} onChange={e => void foto(e.target.files?.[0])} /></div>
      <div className="col-12"><div className="form-check"><input id="producto-visible" className="form-check-input" type="checkbox" checked={producto.isAvailable} onChange={e => setProducto({ ...producto, isAvailable: e.target.checked })} /><label htmlFor="producto-visible" className="form-check-label">Disponible manualmente en la carta</label></div></div>
      <div className="col-md-4"><div className="form-check"><input id="producto-salsas" className="form-check-input" type="checkbox" checked={producto.allowsSauces} onChange={e => setProducto({ ...producto, allowsSauces: e.target.checked, maxSauces: e.target.checked ? Math.max(1, producto.maxSauces) : 0 })} /><label htmlFor="producto-salsas" className="form-check-label">Permite elegir salsas</label></div></div>{producto.allowsSauces && <><div className="col-md-3"><label className="form-label">Salsas incluidas</label><input className="form-control" type="number" min="1" max="8" value={producto.maxSauces} onChange={e => setProducto({ ...producto, maxSauces: Number(e.target.value) })} /></div><div className="col-md-5"><label className="form-label">Variedades, separadas por coma</label><input className="form-control" value={salsasTexto} onChange={e => setSalsasTexto(e.target.value)} /></div></>}</div>
      {producto.id && <div className="pt-3 mt-3 border-top"><button className="btn btn-peligro" onClick={() => setConfirmarEliminar(!confirmarEliminar)}>Eliminar producto</button>{confirmarEliminar && <div className="receta-detalle mt-2"><strong>Se quitará de la carta, junto con su receta y promoción.</strong> Los pedidos históricos se conservarán.<div className="mt-2"><button className="btn btn-peligro" disabled={demo} onClick={() => void ejecutar(() => eliminarProducto(producto.id), 'Producto eliminado').then(ok => { if (ok) { setProducto(null); setConfirmarEliminar(false); } })}>Confirmar eliminación</button></div></div>}</div>}
    </Modal>}

    {insumo && <Modal titulo={insumo.id ? `Configurar ${insumo.name}` : 'Nuevo insumo'} cerrar={() => setInsumo(null)} footer={<><button className={botonBorde} onClick={() => setInsumo(null)}>Cancelar</button><button className={botonRosa} disabled={demo} onClick={() => void ejecutar(() => guardarInsumo(insumo), 'Insumo guardado').then(ok => { if (ok) setInsumo(null); })}>Guardar insumo</button></>}>
      <div className="row g-3"><div className="col-md-8"><label className="form-label">Nombre del insumo</label><input className="form-control" value={insumo.name} onChange={e => setInsumo({ ...insumo, name: e.target.value })} /></div><div className="col-md-4"><label className="form-label">Unidad de compra</label><select className="form-select" value={insumo.unit} disabled={!!insumo.id} onChange={e => setInsumo({ ...insumo, unit: e.target.value as Insumo['unit'], stock: 0, lowThreshold: 0, pauseThreshold: 0 })}><option value="u">Unidades</option><option value="g">Kilogramos</option><option value="ml">Mililitros</option></select></div>
      {([['stock', 'Saldo inicial'], ['lowThreshold', 'Alerta amarilla'], ['pauseThreshold', 'Pausa roja por debajo de']] as const).filter(([clave]) => !insumo.id || clave !== 'stock').map(([clave, label]) => <div className="col-md-4" key={clave}><label className="form-label">{label} ({unidadCompra(insumo)})</label><input className="form-control" type="number" min="0" step="any" value={insumo[clave] / factor(insumo)} onChange={e => setInsumo({ ...insumo, [clave]: Number(e.target.value) * factor(insumo) })} /></div>)}</div><p className="muted-label mt-3 mb-0">{insumo.id ? 'Para cambiar existencias usa Registrar entrada o Ajustar. ' : ''}Las salsas siempre se guardan en ml. Los kg de papa se convierten a g para las recetas.</p>
    </Modal>}

    {movimiento && <Modal ancho="modal-sm" titulo={`${movimiento.tipo === 'entrada' ? 'Registrar entrada' : 'Ajustar saldo'} · ${movimiento.insumo.name}`} cerrar={() => setMovimiento(null)} footer={<><button className={botonBorde} onClick={() => setMovimiento(null)}>Cancelar</button><button className={botonRosa} disabled={demo} onClick={() => {
      const n = Number(cantidad) * factor(movimiento.insumo);
      void ejecutar(() => movimiento.tipo === 'entrada' ? registrarEntrada(movimiento.insumo, n) : ajustarStock(movimiento.insumo, n),
        movimiento.tipo === 'entrada' ? 'Entrada registrada' : 'Saldo ajustado').then(ok => { if (ok) setMovimiento(null); });
    }}>Guardar</button></>}><p className="text-secondary">Saldo actual: {cantidadInsumo(movimiento.insumo)}</p><label className="form-label">{movimiento.tipo === 'entrada' ? 'Cantidad recibida' : 'Nuevo saldo'} ({unidadCompra(movimiento.insumo)})</label><input autoFocus className="form-control" type="number" min="0" step="any" value={cantidad} onChange={e => setCantidad(e.target.value)} /><p className="muted-label mt-2 mb-0">{movimiento.tipo === 'entrada' ? 'Se suma al saldo actual.' : 'Reemplaza el saldo registrado tras un conteo físico.'}</p></Modal>}

    {promocion && <Modal ancho="modal-sm" titulo={`Oferta · ${promocion.name}`} cerrar={() => setPromocion(null)} footer={<><button className={botonBorde} onClick={() => setPromocion(null)}>Cancelar</button><button className={botonRosa} disabled={demo} onClick={() => void ejecutar(() => guardarPromocion(promocion, Number(precioOferta), tagOferta, true), 'Oferta guardada').then(ok => { if (ok) setPromocion(null); })}>Aplicar oferta</button></>}><p>Precio base: <strong>{dinero(promocion.price)}</strong></p><label className="form-label">Nuevo precio S/</label><input type="number" className="form-control mb-3" min="0" step="0.01" value={precioOferta} onChange={e => setPrecioOferta(e.target.value)} /><label className="form-label">Etiqueta</label><input className="form-control" value={tagOferta} onChange={e => setTagOferta(e.target.value)} /></Modal>}

    {receta && productoReceta && <Modal titulo={`Receta · ${productoReceta.name}`} cerrar={() => { setReceta(null); setProductoReceta(null); }} footer={<><button className={botonBorde} onClick={() => { setReceta(null); setProductoReceta(null); }}>Cancelar</button><button className={botonRosa} disabled={demo} onClick={() => void ejecutar(() => guardarReceta(receta), 'Receta guardada').then(ok => { if (ok) { setReceta(null); setProductoReceta(null); } })}>Guardar receta</button></>}>
      <p className="text-secondary">Cantidad consumida por una porción. Escribe 0 para excluir un insumo.</p><div className="row g-2">{datos.insumos.filter(i => !productoReceta.allowsSauces || i.unit !== 'ml' || !i.name.toLowerCase().startsWith('salsa')).map(i => <div className="col-sm-6" key={i.id}><label className="form-label">{i.name} ({i.unit})</label><input className="form-control" type="number" min="0" step="any" value={cantidadReceta(i.id)} onChange={e => cambiarCantidadReceta(i.id, Number(e.target.value))} /></div>)}</div>
      {productoReceta.allowsSauces && <><h3 className="fs-6 mt-4">Salsas a elección del cliente</h3><p className="muted-label">Un campo por opción incluida; las adicionales usan el volumen del último campo.</p><div className="row g-2">{receta.sauceSlotsMl.map((ml, j) => <div className="col-sm-4" key={j}><label className="form-label">Salsa {j + 1} (ml)</label><input className="form-control" type="number" min="1" step="any" value={ml} onChange={e => setReceta({ ...receta, sauceSlotsMl: receta.sauceSlotsMl.map((n, k) => k === j ? Number(e.target.value) : n) })} /></div>)}</div><h3 className="fs-6 mt-4">Vincular variedades al stock</h3><div className="row g-2">{productoReceta.availableSauces.map(nombre => <div className="col-sm-6" key={nombre}><label className="form-label">{nombre}</label><select className="form-select" value={receta.sauceIngredients[nombre] ?? ''} onChange={e => setReceta({ ...receta, sauceIngredients: { ...receta.sauceIngredients, [nombre]: e.target.value } })}><option value="">Sin vincular</option>{datos.insumos.filter(i => i.unit === 'ml').map(i => <option value={i.id} key={i.id}>{i.name}</option>)}</select></div>)}</div></>}
    </Modal>}
  </>;
}
