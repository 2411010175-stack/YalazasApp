import { useEffect, useState } from 'react';
import type { TarifaDelivery } from '../domain';
import { guardarTarifaDelivery } from '../repository';
import { Tarjeta, botonRosa } from '../ui';

type Props = {
  tarifa: TarifaDelivery; uid: string; demo: boolean;
  ejecutar: (accion: () => Promise<unknown>, exito: string) => Promise<boolean>;
};

/** Edición única para la misma política Strategy que consulta Android. */
export default function TarifaDeliveryAjustes({ tarifa, uid, demo, ejecutar }: Props) {
  const [base, setBase] = useState('');
  const [incluidos, setIncluidos] = useState('');
  const [extra, setExtra] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    setBase((tarifa.baseCents / 100).toFixed(2));
    setIncluidos((tarifa.includedMeters / 1000).toString());
    setExtra((tarifa.extraKmCents / 100).toFixed(2));
    setError('');
  }, [tarifa.version, tarifa.baseCents, tarifa.includedMeters, tarifa.extraKmCents]);

  const convertir = (valor: string, factor: number) => {
    if (!valor.trim()) return NaN;
    const numero = Number(valor.replace(',', '.'));
    const unidades = numero * factor;
    return Number.isFinite(unidades) && Math.abs(unidades - Math.round(unidades)) < 0.000001
      ? Math.round(unidades) : NaN;
  };

  async function guardar() {
    const baseCents = convertir(base, 100);
    const includedMeters = convertir(incluidos, 1000);
    const extraKmCents = convertir(extra, 100);
    if (![baseCents, includedMeters, extraKmCents].every(Number.isInteger) ||
      baseCents < 0 || baseCents > 10000 || includedMeters < 1 || includedMeters > 50000 ||
      extraKmCents < 0 || extraKmCents > 10000) {
      setError('Ingresa importes entre S/ 0 y S/ 100 y una distancia entre 0,001 y 50 km.');
      return;
    }
    setError('');
    await ejecutar(() => guardarTarifaDelivery({ version: tarifa.version, baseCents,
      includedMeters, extraKmCents }, uid), 'Tarifa de reparto actualizada');
  }

  return <Tarjeta title="Tarifa de reparto" action={<span className="estado estado-normal">Versión {tarifa.version}</span>}>
    <p className="muted-label">Se aplica a los pedidos a domicilio. El retiro y las mesas no tienen recargo.</p>
    <div className="row g-3">
      <div className="col-md-4"><label className="form-label" htmlFor="tarifa-base">Base (S/)</label>
        <input id="tarifa-base" className="form-control" inputMode="decimal" value={base}
          onChange={e => setBase(e.target.value)} /></div>
      <div className="col-md-4"><label className="form-label" htmlFor="tarifa-km">Distancia incluida (km)</label>
        <input id="tarifa-km" className="form-control" inputMode="decimal" value={incluidos}
          onChange={e => setIncluidos(e.target.value)} /></div>
      <div className="col-md-4"><label className="form-label" htmlFor="tarifa-extra">Por km adicional iniciado (S/)</label>
        <input id="tarifa-extra" className="form-control" inputMode="decimal" value={extra}
          onChange={e => setExtra(e.target.value)} /></div>
    </div>
    {error && <div className="text-danger small mt-2" role="alert">{error}</div>}
    <div className="d-flex flex-wrap align-items-center gap-3 mt-3">
      <button className={botonRosa} disabled={demo} onClick={() => void guardar()}>Guardar nueva versión</button>
      <span className="muted-label">Cada cambio queda registrado en el historial de Firestore.</span>
    </div>
  </Tarjeta>;
}
