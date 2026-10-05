import { useEffect, useState } from 'react';
import { apiFetch } from '../api/apiClient';
import { displayReference } from '../util/displayReference';
// Options retain immutable IDs; labels and keyboard search expose only display references.
export default function ReferencePicker({ kind, value, onChange, id }) {
  const [rows, setRows] = useState([]), [error, setError] = useState(false);
  const [enabled, setEnabled] = useState(false);
  const [filter, setFilter] = useState(''), [version, setVersion] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController(); setError(false);
    apiFetch(kind === 'station' ? '/stations?includeInactive=true' : '/reservations', { signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setRows(Array.isArray(data) ? data : []); })
      .catch(() => { if (!controller.signal.aborted) setError(true); });
    return () => controller.abort();
  }, [kind, version, enabled]);
  const key = kind === 'station' ? 'stationId' : 'reservationId';
  const label = row => (row.name ? row.name + ' · ' : '') + displayReference(row[key], kind);
  return <div>
    <input className="form-control mb-1" aria-label={'Find ' + kind + ' reference'} onFocus={() => setEnabled(true)} placeholder="Type a name or reference" value={filter} onChange={e => setFilter(e.target.value)}/>
    <select onFocus={() => setEnabled(true)} id={id} className="form-select" value={value} onChange={onChange}>
      <option value="">All {kind === 'station' ? 'stations' : 'reservations'}</option>
      {rows.filter(row => row[key] === value || label(row).toLowerCase().includes(filter.trim().toLowerCase())).map(row =>
        <option key={row[key]} value={row[key]}>{label(row)}</option>)}
    </select>
    {error && <p role="status" className="small">Could not load references. <button type="button" className="btn btn-link btn-sm" onClick={() => setVersion(n => n + 1)}>Retry</button></p>}
  </div>;
}
