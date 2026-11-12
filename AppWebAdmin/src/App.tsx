import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { onAuthStateChanged, type User } from 'firebase/auth';
import { auth, configurado } from './firebase';
import { datosDemo } from './fixtures';
import { codigo, datosVacios, resumen, type Datos, type Pagina, type Usuario } from './domain';
import { entrar, escucharDatos, perfilAdministrador, salir } from './repository';
import { Marca, Sidebar, botonRosa, navegacion } from './ui';
import Resumen from './pages/Resumen';
import Ventas from './pages/Ventas';
import Inventario from './pages/Inventario';
import { Ajustes, Clientes, Mesas, POS, Repartidores } from './pages/Operacion';

const paginaHash = (): Pagina => {
  const hash = window.location.hash.replace('#', '') as Pagina;
  return navegacion.some(n => n.id === hash) ? hash : 'resumen';
};
const demoPerfil: Usuario = { uid: 'demo-admin', email: 'admin@yalazas.pe', role: 'ADMINISTRADOR', name: 'Alex Ramos', phone: '', active: true };

function Login({ error, onEntrar }: { error: string; onEntrar: (email: string, password: string) => Promise<void> }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mostrarPassword, setMostrarPassword] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  async function enviar(e: FormEvent) {
    e.preventDefault(); setOcupado(true);
    try { await onEntrar(email, password); } finally { setOcupado(false); }
  }
  return <div className="login-wrap">
    <div className="login-arte"><Marca /><div><div className="eyebrow mb-3">PANEL ADMINISTRATIVO</div>
      <h1>Todo el restaurante, en una sola vista.</h1>
      <p className="text-secondary mt-3">Pedidos, caja, almacén, salón y delivery conectados a Yalaza’s.</p>
    </div><small>Acceso exclusivo para administración · San Antonio</small></div>
    <div className="login-panel"><form className="login-form" onSubmit={e => void enviar(e)}>
      <div className="eyebrow mb-2">BIENVENIDO DE NUEVO</div><h2>Inicia sesión</h2>
      <p className="text-secondary small mb-4">Utiliza la misma cuenta de administrador de la app Android.</p>
      <label className="form-label" htmlFor="login-email">Correo electrónico</label>
      <input id="login-email" className="form-control mb-3" type="email" autoComplete="username" required value={email} onChange={e => setEmail(e.target.value)} />
      <label className="form-label" htmlFor="login-password">Contraseña</label>
      <div className="password-wrap mb-3"><input id="login-password" className="form-control" type={mostrarPassword ? 'text' : 'password'}
        autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} />
        <button type="button" className="password-toggle" aria-label={mostrarPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          aria-pressed={mostrarPassword} title={mostrarPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          onClick={() => setMostrarPassword(actual => !actual)}>
          <i className={`bi ${mostrarPassword ? 'bi-eye-slash' : 'bi-eye'}`} aria-hidden="true" />
        </button>
      </div>
      {error && <div className="alert alert-danger small py-2" role="alert">{error}</div>}
      <button className={`${botonRosa} w-100 mt-2`} disabled={ocupado}>{ocupado ? 'Ingresando…' : 'Entrar al panel'}</button>
      <p className="muted-label mt-3">El acceso también se comprueba con las reglas de Firestore.</p>
    </form></div>
  </div>;
}

export default function App() {
  const demo = !configurado;
  const [pagina, setPagina] = useState<Pagina>(paginaHash);
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [notificaciones, setNotificaciones] = useState(false);
  const [perfilMenu, setPerfilMenu] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [busquedaInicial, setBusquedaInicial] = useState('');
  const [datos, setDatos] = useState<Datos>(demo ? datosDemo : datosVacios);
  const [cargadas, setCargadas] = useState<Set<keyof Datos>>(demo ? new Set(Object.keys(datosDemo) as Array<keyof Datos>) : new Set());
  const [usuario, setUsuario] = useState<User | null>(null);
  const [perfil, setPerfil] = useState<Usuario | null>(demo ? demoPerfil : null);
  const [revisando, setRevisando] = useState(!demo);
  const [accesoError, setAccesoError] = useState('');
  const [datosError, setDatosError] = useState('');
  const [aviso, setAviso] = useState<{ mensaje: string; error: boolean } | null>(null);
  const [ocupado, setOcupado] = useState(false);

  useEffect(() => {
    document.documentElement.setAttribute('data-bs-theme', 'dark');
    const actualizar = () => setPagina(paginaHash());
    window.addEventListener('hashchange', actualizar);
    return () => window.removeEventListener('hashchange', actualizar);
  }, []);

  useEffect(() => {
    if (!auth) return;
    let activo = true;
    let cancelar: (() => void) | undefined;
    const dejar = onAuthStateChanged(auth, async sesion => {
      cancelar?.(); cancelar = undefined;
      setUsuario(sesion); setPerfil(null); setDatos(datosVacios); setCargadas(new Set()); setDatosError('');
      if (!sesion) { setRevisando(false); return; }
      setRevisando(true);
      try {
        const p = await perfilAdministrador(sesion);
        if (!activo) return;
        setPerfil(p); setAccesoError(''); setRevisando(false);
        cancelar = escucharDatos((nuevo, cargado) => { if (activo) { setDatos(nuevo); setCargadas(cargado); } },
          (origen, error) => { if (activo) setDatosError(`No se pudo cargar ${origen}: ${error.message}`); });
      } catch (e) {
        if (activo) { setAccesoError(e instanceof Error ? e.message : 'No se pudo verificar la cuenta.'); setRevisando(false); }
      }
    });
    return () => { activo = false; cancelar?.(); dejar(); };
  }, []);

  useEffect(() => {
    if (!aviso) return;
    const timer = setTimeout(() => setAviso(null), 4700);
    return () => clearTimeout(timer);
  }, [aviso]);

  const avisar = useCallback((mensaje: string, error = false) => setAviso({ mensaje, error }), []);
  const ir = useCallback((destino: Pagina) => { window.location.hash = destino; setPagina(destino); setBusquedasAlNavegar(destino); window.scrollTo({ top: 0, behavior: 'smooth' }); }, []);
  function setBusquedasAlNavegar(destino: Pagina) { if (destino !== 'ventas') setBusquedaInicial(''); }

  const ejecutar = useCallback(async (accion: () => Promise<unknown>, exito: string): Promise<boolean> => {
    if (demo) { avisar('Vista de ejemplo: conecta Firebase para guardar cambios.', true); return false; }
    if (ocupado) return false;
    setOcupado(true);
    try { await accion(); avisar(exito); return true; }
    catch (e) { avisar(e instanceof Error ? e.message : 'No se pudo completar la acción.', true); return false; }
    finally { setOcupado(false); }
  }, [demo, ocupado, avisar]);

  const m = useMemo(() => resumen(datos), [datos]);
  const sugerencias = useMemo(() => {
    const q = busqueda.trim().toLocaleLowerCase();
    if (q.length < 2) return [];
    return [
      ...datos.pedidos.filter(p => [p.id, codigo(p.id), p.customerName, p.phone].some(x => x?.toLocaleLowerCase().includes(q))).slice(0, 4).map(p => ({ tipo: 'Pedido', texto: `${codigo(p.id)} · ${p.customerName}`, pagina: 'ventas' as Pagina, consulta: codigo(p.id) })),
      ...datos.usuarios.filter(u => `${u.name} ${u.email}`.toLocaleLowerCase().includes(q)).slice(0, 3).map(u => ({ tipo: 'Usuario', texto: u.name || u.email, pagina: (u.role === 'CLIENTE' ? 'clientes' : 'ajustes') as Pagina, consulta: u.name || u.email })),
    ].slice(0, 6);
  }, [busqueda, datos]);

  async function login(email: string, password: string) {
    setAccesoError('');
    try { await entrar(email, password); }
    catch (e) { const code = (e as { code?: string }).code ?? ''; setAccesoError(code.includes('invalid-credential') || code.includes('wrong-password') || code.includes('user-not-found') ? 'Correo o contraseña incorrectos.' : e instanceof Error ? e.message : 'No se pudo iniciar sesión.'); }
  }

  if (revisando) return <div className="cargando"><div><div className="spinner-border text-danger mb-3" role="status" /><div>Verificando acceso de administración…</div></div></div>;
  if (!perfil) return usuario ? <div className="login-panel min-vh-100"><div className="login-form text-center"><Marca /><h2 className="mt-4">Acceso restringido</h2><p className="text-secondary">{accesoError || 'Esta cuenta no puede abrir el panel.'}</p><button className={botonRosa} onClick={() => void salir()}>Cerrar sesión</button></div></div> : <Login error={accesoError} onEntrar={login} />;

  const listo = demo || ['pedidos', 'usuarios', 'productos', 'insumos', 'recetas', 'mesas'].every(x => cargadas.has(x as keyof Datos));
  const props = { datos, uid: perfil.uid, demo, ejecutar, ir };
  return <div className="app">
    <Sidebar pagina={pagina} ir={ir} abierto={menuAbierto} cerrar={() => setMenuAbierto(false)} avisos={m.revisiones} />
    <div className="contenido">
      <header className="topbar"><button className="btn-icon d-lg-none" aria-label="Abrir menú" onClick={() => setMenuAbierto(true)}><i className="bi bi-list fs-4" /></button><span className="sucursal">Sucursal: <strong>San Antonio</strong> <i className="bi bi-chevron-down ms-1" /></span><span className="topbar-sep" /><div className="busqueda-global"><i className="bi bi-search" /><input placeholder="Buscar cliente o pedido…" aria-label="Búsqueda global" value={busqueda} onChange={e => setBusqueda(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && busqueda.trim()) { ir('ventas'); setBusquedaInicial(busqueda.trim()); setBusqueda(''); } }} />{sugerencias.length > 0 && <div className="busqueda-resultados">{sugerencias.map((s, i) => <button key={`${s.tipo}-${i}`} onClick={() => { ir(s.pagina); setBusquedaInicial(s.consulta); setBusqueda(''); }}><span className="text-secondary me-2">{s.tipo}</span>{s.texto}</button>)}</div>}</div>
        <div className="notif-wrap"><button className="btn-icon" aria-label={`${m.revisiones + m.criticos.length} alertas`} onClick={() => { setNotificaciones(!notificaciones); setPerfilMenu(false); }}><i className="bi bi-bell" />{m.revisiones + m.criticos.length > 0 && <span className="notif-cuenta">{m.revisiones + m.criticos.length}</span>}</button>{notificaciones && <div className="notif-menu"><h3>Notificaciones</h3>{m.revisiones > 0 && <button onClick={() => { ir('ventas'); setNotificaciones(false); }}><i className="bi bi-credit-card text-rosa" />{m.revisiones} pagos esperan revisión</button>}{m.criticos.length > 0 && <button onClick={() => { ir('inventario'); setNotificaciones(false); }}><i className="bi bi-exclamation-triangle text-oro" />{m.criticos.length} insumos críticos</button>}{m.revisiones + m.criticos.length === 0 && <div className="muted-label px-2 pb-2">Sin alertas por ahora.</div>}</div>}</div>
        <div className="notif-wrap"><button className="btn-icon perfil-corto" aria-label="Cuenta de administrador" onClick={() => { setPerfilMenu(!perfilMenu); setNotificaciones(false); }}><span className="avatar">{(perfil.name || 'A').charAt(0).toUpperCase()}</span><span>{perfil.name?.split(' ')[0] || 'Admin'}</span><i className="bi bi-chevron-down" /></button>{perfilMenu && <div className="notif-menu" style={{ width: 230 }}><h3>{perfil.name || 'Administrador'}</h3><div className="muted-label px-2 mb-2">{perfil.email}</div><button onClick={() => { ir('ajustes'); setPerfilMenu(false); }}><i className="bi bi-gear" />Ajustes</button>{!demo && <button onClick={() => void salir()}><i className="bi bi-box-arrow-right" />Cerrar sesión</button>}</div>}</div>
      </header>
      {demo && <div className="demo-alerta"><i className="bi bi-eye me-1" /> Vista de ejemplo con datos ficticios. Para usar datos reales, configura Firebase en <code>AppWebAdmin/.env</code>.</div>}
      {datosError && <div className="alert alert-danger rounded-0 mb-0 small py-2" role="alert">{datosError}</div>}
      {!listo && <div className="demo-alerta">Sincronizando datos del servidor…</div>}
      <main className="main-inner" id="contenido-principal">
        {pagina === 'resumen' && <Resumen datos={datos} ir={ir} listo={listo} />}
        {pagina === 'pos' && <POS {...props} />}
        {pagina === 'ventas' && <Ventas datos={datos} uid={perfil.uid} demo={demo} ejecutar={ejecutar} busquedaInicial={busquedaInicial} />}
        {pagina === 'clientes' && <Clientes {...props} busquedaInicial={busquedaInicial} />}
        {pagina === 'inventario' && <Inventario datos={datos} demo={demo} ejecutar={ejecutar} avisar={avisar} />}
        {pagina === 'repartidores' && <Repartidores {...props} />}
        {pagina === 'mesas' && <Mesas {...props} />}
        {pagina === 'ajustes' && <Ajustes {...props} busquedaInicial={busquedaInicial} />}
      </main>
    </div>
    {aviso && <div className={`toast-y ${aviso.error ? 'error' : ''}`} role="status">{aviso.mensaje}</div>}
  </div>;
}
