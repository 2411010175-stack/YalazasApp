import { useEffect, type ReactNode } from 'react';
import type { Pagina, Pedido } from './domain';
import { codigo, nombreEstado, pagoEnRevision } from './domain';

export const navegacion: Array<{ id: Pagina; nombre: string; icono: string }> = [
  { id: 'resumen', nombre: 'Resumen', icono: 'bi-grid-1x2' },
  { id: 'pos', nombre: 'Venta directa / POS', icono: 'bi-cart3' },
  { id: 'ventas', nombre: 'Ventas y filtros', icono: 'bi-receipt' },
  { id: 'clientes', nombre: 'Clientes', icono: 'bi-people' },
  { id: 'inventario', nombre: 'Inventario y almacén', icono: 'bi-box-seam' },
  { id: 'repartidores', nombre: 'Repartidores', icono: 'bi-bicycle' },
  { id: 'mesas', nombre: 'Salón y mesas', icono: 'bi-table' },
  { id: 'ajustes', nombre: 'Ajustes', icono: 'bi-gear' },
];

export function Marca({ compacta = false }: { compacta?: boolean }) {
  return <span className={`marca ${compacta ? 'marca-compacta' : ''}`}>
    <img src={`${import.meta.env.BASE_URL}yalaza-logo.png`} alt="Yalaza's" />
  </span>;
}

export function Sidebar({ pagina, ir, abierto, cerrar, avisos }: {
  pagina: Pagina; ir: (p: Pagina) => void; abierto: boolean; cerrar: () => void; avisos: number;
}) {
  return <>
    {abierto && <button className="sidebar-velo d-lg-none" aria-label="Cerrar menú" onClick={cerrar} />}
    <aside className={`sidebar ${abierto ? 'sidebar-abierto' : ''}`}>
      <div className="sidebar-logo"><Marca /><button className="btn btn-icon d-lg-none" aria-label="Cerrar menú" onClick={cerrar}><i className="bi bi-x-lg" /></button></div>
      <div className="sidebar-caption">ESPACIO DE TRABAJO</div>
      <nav aria-label="Menú principal" className="sidebar-nav">
        {navegacion.map(item => <button key={item.id} type="button"
          className={`sidebar-enlace ${pagina === item.id ? 'activo' : ''}`}
          onClick={() => { ir(item.id); cerrar(); }} aria-current={pagina === item.id ? 'page' : undefined}>
          <i className={`bi ${item.icono}`} aria-hidden="true" /><span>{item.nombre}</span>
          {item.id === 'ventas' && avisos > 0 && <span className="sidebar-cuenta">{avisos}</span>}
        </button>)}
      </nav>
      <div className="sidebar-foot"><span className="punto-en-vivo" /> Panel administrativo <span className="text-secondary">· Yalaza’s</span></div>
    </aside>
  </>;
}

export function EncabezadoPagina({ etiqueta, titulo, descripcion, acciones }: {
  etiqueta?: string; titulo: string; descripcion: string; acciones?: ReactNode;
}) {
  return <div className="page-heading d-flex flex-wrap align-items-end justify-content-between gap-3">
    <div>{etiqueta && <div className="eyebrow">{etiqueta}</div>}<h1>{titulo}</h1><p>{descripcion}</p></div>
    {acciones && <div className="d-flex flex-wrap gap-2">{acciones}</div>}
  </div>;
}

export function Tarjeta({ children, className = '', title, action }: { children: ReactNode; className?: string; title?: string; action?: ReactNode }) {
  return <section className={`tarjeta ${className}`}>
    {(title || action) && <div className="tarjeta-head"><h2>{title}</h2>{action}</div>}
    {children}
  </section>;
}

export function Estado({ pedido }: { pedido: Pedido }) {
  const clave = pagoEnRevision(pedido) ? 'REVISION' : pedido.status;
  const texto = clave === 'REVISION' ? 'Revisar pago' : nombreEstado(clave);
  return <span className={`estado estado-${clave.toLowerCase()}`}>{texto}</span>;
}

export function Vacio({ icono = 'bi-inbox', titulo, texto }: { icono?: string; titulo: string; texto?: string }) {
  return <div className="vacio"><i className={`bi ${icono}`} aria-hidden="true" /><strong>{titulo}</strong>{texto && <span>{texto}</span>}</div>;
}

export function Modal({ titulo, children, cerrar, footer, ancho = 'modal-lg' }: {
  titulo: string; children: ReactNode; cerrar: () => void; footer?: ReactNode; ancho?: string;
}) {
  useEffect(() => {
    const tecla = (e: KeyboardEvent) => { if (e.key === 'Escape') cerrar(); };
    document.addEventListener('keydown', tecla);
    document.body.style.overflow = 'hidden';
    return () => { document.removeEventListener('keydown', tecla); document.body.style.overflow = ''; };
  }, [cerrar]);
  return <div className="modal d-block" role="dialog" aria-modal="true" aria-label={titulo} onMouseDown={e => { if (e.target === e.currentTarget) cerrar(); }}>
    <div className={`modal-dialog modal-dialog-centered modal-dialog-scrollable ${ancho}`}>
      <div className="modal-content"><div className="modal-header"><h2 className="modal-title fs-5">{titulo}</h2><button className="btn-close btn-close-white" aria-label="Cerrar" onClick={cerrar} /></div>
        <div className="modal-body">{children}</div>{footer && <div className="modal-footer">{footer}</div>}</div>
    </div>
  </div>;
}

export const botonRosa = 'btn btn-yalaza';
export const botonSuave = 'btn btn-suave';
export const botonBorde = 'btn btn-borde';
export const iconoPedido = (pedido: Pedido) => pedido.serviceType === 'MESA' ? `Mesa ${pedido.tableNumber}` : codigo(pedido.id);
