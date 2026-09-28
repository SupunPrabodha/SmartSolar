import { useEffect, useState } from 'react';
import { apiFetch } from '../api/apiClient';
import { useAuth } from '../auth/AuthContext';
import HomePage from './HomePage';
import { StatusBadge } from './reservations/ReservationComponents';
import { localTimeZone } from './reservations/reservationUi';
import { fromUtcInput, toUtcInput, stationPayload } from '../util/catalog';
const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const emptyStation = () => ({ name: '', address: '', latitude: '', longitude: '', capacityKwh: '',
  totalBatterySlots: '', operatingSchedule: days.map((_, i) => ({ day: i + 1, isClosed: true, opensAt: null, closesAt: null })) });
const showTime = value => new Date(value).toLocaleString();
function ErrorMessage({ value }) { return value && <div role="alert" className="alert alert-danger">{value}</div>; }
function Field({ label, ...props }) { return <label className="form-label d-block">{label}<input className="form-control" {...props} /></label>; }

function StationForm({ station, onSaved, onCancel }) {
  const [form, setForm] = useState(() => station ? { ...station, operatingSchedule: station.operatingSchedule?.length === 7
    ? station.operatingSchedule : emptyStation().operatingSchedule } : emptyStation());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  function field(key) { return { value: form[key], onChange: e => setForm({ ...form, [key]: e.target.value }) }; }
  function changeDay(index, change) {
    setForm({ ...form, operatingSchedule: form.operatingSchedule.map((day, i) => i === index ? { ...day, ...change } : day) });
  }
  async function save(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const result = await apiFetch(station ? '/stations/' + station.stationId : '/stations', {
        method: station ? 'PUT' : 'POST', body: JSON.stringify(stationPayload(form))
      });
      onSaved(result);
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }
  return <section className="surface-card my-3"><h2>{station ? 'Edit station' : 'Add station'}</h2>
    <ErrorMessage value={error} /><form onSubmit={save}><fieldset disabled={busy}>
      <div className="row"><div className="col-md-6"><Field label="Name" required minLength={2} maxLength={120} {...field('name')} /></div>
        <div className="col-md-6"><Field label="Address" required minLength={3} maxLength={300} {...field('address')} /></div>
        <div className="col-md-6"><Field label="Latitude" required type="number" step="any" min="-90" max="90" {...field('latitude')} /></div>
        <div className="col-md-6"><Field label="Longitude" required type="number" step="any" min="-180" max="180" {...field('longitude')} /></div>
        <div className="col-md-6"><Field label="Energy capacity (kWh)" required type="number" step="any" min="0.000001" {...field('capacityKwh')} /></div>
        <div className="col-md-6"><Field label="Total battery slots" required type="number" step="1" min="1" max="2147483647" {...field('totalBatterySlots')} /></div></div>
      <h3 className="h5 mt-3">Weekly operating schedule (UTC)</h3>
      <p>Use 24-hour HH:mm. Closing at 24:00 means midnight at the end of that day. Split overnight hours across two days.</p>
      {form.operatingSchedule.map((day, i) => <div className="row align-items-center mb-2" key={day.day}>
        <div className="col-sm-4"><label><input type="checkbox" checked={day.isClosed} onChange={e => changeDay(i,
          { isClosed: e.target.checked, opensAt: e.target.checked ? null : '08:00', closesAt: e.target.checked ? null : '17:00' })} /> {days[day.day - 1]} closed</label></div>
        {!day.isClosed && <><div className="col-sm-4"><Field label={days[day.day - 1] + ' opens'} required placeholder="08:00"
          pattern="([01][0-9]|2[0-3]):[0-5][0-9]" value={day.opensAt ?? ''} onChange={e => changeDay(i, { opensAt: e.target.value })} /></div>
          <div className="col-sm-4"><Field label={days[day.day - 1] + ' closes'} required placeholder="17:00"
            pattern="([01][0-9]|2[0-3]):[0-5][0-9]|24:00" value={day.closesAt ?? ''} onChange={e => changeDay(i, { closesAt: e.target.value })} /></div></>}
      </div>)}
      <div className="d-flex gap-2 mt-3"><button className="btn btn-primary">{busy ? 'Saving…' : 'Save station'}</button>
        <button className="btn btn-outline-secondary" type="button" onClick={onCancel}>Cancel</button></div>
    </fieldset></form></section>;
}

function SlotForm({ slot, station, onSaved, onCancel }) {
  const [form, setForm] = useState({ start: slot ? toUtcInput(slot.startAtUtc) : '', end: slot ? toUtcInput(slot.endAtUtc) : '',
    total: slot?.totalSlots ?? '', available: slot?.availableSlots ?? '' });
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const field = key => ({ value: form[key], onChange: e => setForm({ ...form, [key]: e.target.value }) });
  async function save(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await apiFetch(slot ? '/slots/' + slot.slotId : '/stations/' + station.stationId + '/slots', {
        method: slot ? 'PUT' : 'POST', body: JSON.stringify({ startAtUtc: fromUtcInput(form.start), endAtUtc: fromUtcInput(form.end),
          totalSlots: Number(form.total), availableSlots: Number(form.available), expectedUpdatedAtUtc: slot?.updatedAtUtc })
      }); onSaved();
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }
  return <form className="surface-card my-3" onSubmit={save}><h3>{slot ? 'Edit slot' : 'Add slot'}</h3>
    <ErrorMessage value={error} /><fieldset disabled={busy}><p>Enter local times ({localTimeZone()}). Active windows at a station cannot overlap.</p>
      <div className="row"><div className="col-md-6"><Field label="Start (local time)" type="datetime-local" step="0.001" required {...field('start')} /></div>
        <div className="col-md-6"><Field label="End (local time)" type="datetime-local" step="0.001" required {...field('end')} /></div>
        <div className="col-md-6"><Field label="Total slots" type="number" min="1" max={station.totalBatterySlots} step="1" required {...field('total')} /></div>
        <div className="col-md-6"><Field label="Available slots" type="number" min="0" max={form.total || station.totalBatterySlots} step="1" required {...field('available')} /></div></div>
      <div className="d-flex gap-2"><button className="btn btn-primary">{busy ? 'Saving...' : 'Save slot'}</button>
        <button type="button" className="btn btn-outline-secondary" onClick={onCancel}>Cancel</button></div></fieldset></form>;
}
function SlotRow({ slot, canManage, busy, onEdit, onAvailability, onDeactivate }) {
  const [available, setAvailable] = useState(slot.availableSlots);
  useEffect(() => { setAvailable(slot.availableSlots); }, [slot.availableSlots, slot.updatedAtUtc]);
  return <tr><td>{showTime(slot.startAtUtc)}<br />to {showTime(slot.endAtUtc)}</td><td>{slot.availableSlots} / {slot.totalSlots}</td>
    <td><StatusBadge status={slot.isActive ? 'Active' : 'Inactive'}/></td>{canManage && <td>
      <button type="button" disabled={busy} className="btn btn-sm btn-outline-primary me-2" onClick={onEdit}>Edit</button>
      {slot.isActive && <><button type="button" disabled={busy} className="btn btn-sm btn-outline-danger" onClick={onDeactivate}>Deactivate</button>
        <form className="d-flex flex-wrap align-items-end gap-2 mt-2" onSubmit={e => { e.preventDefault(); onAvailability(Number(available)); }}>
          <label>Available slots<input aria-label={'Available slots for ' + showTime(slot.startAtUtc)} className="form-control" style={{ maxWidth: '9rem' }}
            type="number" min="0" max={slot.totalSlots} step="1" required disabled={busy} value={available} onChange={e => setAvailable(e.target.value)} /></label>
          <button disabled={busy} className="btn btn-sm btn-outline-primary">Update availability</button></form></>}
    </td>}</tr>;
}
function StationDetails({ id, onClose, onChanged }) {
  const { user } = useAuth();
  const [station, setStation] = useState(null), [slots, setSlots] = useState([]);
  const [version, setVersion] = useState(0), [loading, setLoading] = useState(true), [busy, setBusy] = useState(false);
  const [error, setError] = useState(''), [success, setSuccess] = useState('');
  const [editing, setEditing] = useState(false), [slotForm, setSlotForm] = useState(null);
  const canManage = user.role === 'GridOperator';
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError(''); setStation(null);
    Promise.all([apiFetch('/stations/' + id, { signal: abort.signal }), apiFetch('/stations/' + id + '/slots?includeInactive=true', { signal: abort.signal })])
      .then(([nextStation, nextSlots]) => { if (!abort.signal.aborted) { setStation(nextStation); setSlots(nextSlots); } })
      .catch(err => { if (!abort.signal.aborted) setError(err.message); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [id, version]);
  function saved(message) { setEditing(false); setSlotForm(null); setSuccess(message); setVersion(v => v + 1); onChanged(); }
  async function mutate(path, body, message) {
    setBusy(true); setError(''); setSuccess('');
    try { await apiFetch(path, { method: 'PATCH', body: JSON.stringify(body) }); saved(message); }
    catch (err) { setError(err.message); } finally { setBusy(false); }
  }
  return <section className="mt-4">
    <div className="d-flex gap-2 mb-3"><button className="btn btn-outline-secondary" onClick={onClose}>Back to stations</button>
      <button className="btn btn-outline-secondary" disabled={busy || loading} onClick={() => { setEditing(false); setSlotForm(null); setVersion(v => v + 1); }}>Reload details</button></div>
    <ErrorMessage value={error} />{success && <div role="status" className="alert alert-success">{success}</div>}
    {loading && <p role="status">Loading station and slots...</p>}
    {station && <><article className="surface-card"><h2>{station.name}</h2><p>{station.address}</p>
      <dl className="row"><dt className="col-sm-4">Status</dt><dd className="col-sm-8">{station.isActive ? 'Active' : 'Inactive'}</dd>
        <dt className="col-sm-4">Coordinates</dt><dd className="col-sm-8">{station.latitude}, {station.longitude}</dd>
        <dt className="col-sm-4">Capacity</dt><dd className="col-sm-8">{station.capacityKwh} kWh / {station.totalBatterySlots} battery slots</dd></dl>
      <h3 className="h5">Operating schedule (UTC)</h3>
      {station.operatingSchedule?.length ? <ul>{station.operatingSchedule.map(day => <li key={day.day}>
        {days[day.day - 1]}: {day.isClosed ? 'Closed' : day.opensAt + '-' + day.closesAt}</li>)}</ul> : <p>Schedule not configured. Provide all seven days when editing.</p>}
      {user.role === 'Backoffice' && <div className="d-flex flex-wrap gap-2">
        <button className="btn btn-primary" disabled={busy} onClick={() => setEditing(true)}>Edit station</button>
        {station.isActive && <button className="btn btn-outline-danger" disabled={busy} onClick={() => {
          if (window.confirm('Deactivate ' + station.name + '? It will disappear from station discovery. Historical records are retained.'))
            mutate('/stations/' + id + '/deactivate', { expectedUpdatedAtUtc: station.updatedAtUtc }, 'Station deactivated.');
        }}>Deactivate station</button>}</div>}</article>
      {editing && <StationForm key={station.updatedAtUtc} station={station} onSaved={() => saved('Station saved.')} onCancel={() => setEditing(false)} />}
      <div className="d-flex flex-wrap justify-content-between gap-2 mt-4 mb-2"><h2 className="h4">Booking slots</h2>
        {canManage && station.isActive && <button className="btn btn-primary" disabled={busy} onClick={() => setSlotForm({})}>Add slot</button>}</div>
      <p className="text-secondary">Times shown in your local timezone ({localTimeZone()}). Availability is confirmed when a reservation is saved.</p>
      {slotForm && <SlotForm key={slotForm.slotId || 'new'} slot={slotForm.slotId ? slotForm : null} station={station}
        onSaved={() => saved('Slot saved.')} onCancel={() => setSlotForm(null)} />}
      {!slots.length ? <p className="surface-card">No booking slots have been created for this station.</p> :
        <div className="table-responsive surface-card"><table className="table align-middle"><caption>Slots for {station.name}</caption><thead><tr>
          <th scope="col">Time window</th><th scope="col">Available / total</th><th scope="col">Status</th>{canManage && <th scope="col">Actions</th>}</tr></thead>
          <tbody>{slots.map(slot => <SlotRow key={slot.slotId} slot={slot} busy={busy} canManage={canManage}
            onEdit={() => setSlotForm(slot)} onAvailability={availableSlots => mutate('/slots/' + slot.slotId + '/availability',
              { availableSlots, expectedUpdatedAtUtc: slot.updatedAtUtc }, 'Availability updated.')}
            onDeactivate={() => { if (window.confirm('Deactivate this slot? Its history will be retained.'))
              mutate('/slots/' + slot.slotId + '/deactivate', { expectedUpdatedAtUtc: slot.updatedAtUtc }, 'Slot deactivated.'); }} />)}</tbody></table></div>}
    </>}
  </section>;
}
export default function StationsPage() {
  const { user } = useAuth();
  const [search, setSearch] = useState(''), [filter, setFilter] = useState(''), [inactive, setInactive] = useState(false);
  const [stations, setStations] = useState([]), [selected, setSelected] = useState(null), [adding, setAdding] = useState(false);
  const [loading, setLoading] = useState(true), [error, setError] = useState(''), [version, setVersion] = useState(0), [success, setSuccess] = useState('');
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError('');
    apiFetch('/stations?' + new URLSearchParams({ search: filter, includeInactive: String(inactive) }), { signal: abort.signal })
      .then(result => { if (!abort.signal.aborted) setStations(result); })
      .catch(err => { if (!abort.signal.aborted) setError(err.message); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [filter, inactive, version]);
  return <HomePage><div className="page-heading"><div><p className="eyebrow">STATION NETWORK</p><h1>Microgrid stations</h1>
    <p>Station information and energy slot inventory.</p></div>
    {user.role === 'Backoffice' && !selected && <button className="btn btn-primary" onClick={() => setAdding(true)}>Add station</button>}</div>
    {selected ? <StationDetails key={selected} id={selected} onClose={() => setSelected(null)} onChanged={() => setVersion(v => v + 1)} /> : <>
      {success && <div role="status" className="alert alert-success">{success}</div>}
      {adding && <StationForm onCancel={() => setAdding(false)} onSaved={station => { setAdding(false); setSelected(station.stationId); setSuccess('Station created.'); setVersion(v => v + 1); }} />}
      <form className="surface-card d-flex flex-wrap align-items-end gap-3 my-3" onSubmit={e => { e.preventDefault(); setFilter(search.trim()); setVersion(v => v + 1); }}>
        <label className="flex-grow-1">Search name or address<input className="form-control" maxLength={120} value={search} onChange={e => setSearch(e.target.value)} /></label>
        <label className="mb-2"><input type="checkbox" checked={inactive} onChange={e => setInactive(e.target.checked)} /> Include inactive</label>
        <button className="btn btn-outline-primary">Search / reload</button></form>
      <ErrorMessage value={error} />{loading ? <p role="status">Loading stations...</p> : !error && (
        !stations.length ? <p className="surface-card">No stations match this search.</p> :
          <div className="module-grid">{stations.map(station => <article className="surface-card station-card" key={station.stationId}>
            <StatusBadge status={station.isActive ? 'Active' : 'Inactive'}/><h2>{station.name}</h2><p>{station.address}</p><div className="station-specs"><p><strong>{station.capacityKwh}</strong>kWh capacity</p><p><strong>{station.totalBatterySlots}</strong>battery slots</p></div><button className="btn btn-outline-primary" onClick={() => { setSelected(station.stationId); setAdding(false); }}>
              Manage station</button></article>)}</div>)}
    </>}
  </HomePage>;
}
