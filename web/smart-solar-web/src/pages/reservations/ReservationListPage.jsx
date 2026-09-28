import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listReservations } from '../../api/reservations.js';
import { ErrorNotice, Loading, ReservationTable } from './ReservationComponents';
import { localTimeZone, reservationStatuses } from './reservationUi.js';
import { useReservationData } from './useReservationData.js';

export default function ReservationListPage() {
  const [searchParams] = useSearchParams();
  const blank = { status: '', prosumerNic: '', stationId: '' };
  const initial = { ...blank, status: searchParams.get('status') ?? '' };
  const [draft, setDraft] = useState(initial);
  const [filters, setFilters] = useState(initial);
  const load = useCallback(signal => listReservations(filters, { signal }), [filters]);
  const { data, error, loading, reload } = useReservationData(load);
  const queryStatus = searchParams.get('status') ?? '';
  useEffect(() => { setDraft({ ...blank, status: queryStatus }); setFilters({ ...blank, status: queryStatus }); }, [queryStatus]);
  const filtered = Object.values(filters).some(Boolean);
  function apply(event) {
    event.preventDefault();
    setFilters(Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, value.trim()])));
  }
  return <>
    <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
      <div><p className="eyebrow">RESERVATION MANAGEMENT</p><h1 className="h2">{queryStatus === 'Pending' ? 'Pending Queue' : 'Manage Reservations'}</h1>
        <p className="text-secondary mb-0">Inspect and assist with energy reservations. Times shown in your local timezone ({localTimeZone()}).</p></div>
      <Link className="btn btn-primary" to="new">New reservation</Link>
    </div>
    <form className="surface-card mb-4" onSubmit={apply} aria-label="Reservation filters">
      <div className="row g-3">
        <div className="col-md-3"><label className="form-label" htmlFor="filter-status">Status</label>
          <select id="filter-status" className="form-select" value={draft.status} onChange={e => setDraft({ ...draft, status: e.target.value })}>
            <option value="">All statuses</option>{reservationStatuses.map(status => <option key={status}>{status}</option>)}
          </select></div>
        <div className="col-md-4"><label className="form-label" htmlFor="filter-nic">Prosumer NIC</label>
          <input id="filter-nic" className="form-control" value={draft.prosumerNic} onChange={e => setDraft({ ...draft, prosumerNic: e.target.value })} /></div>
        <div className="col-md-5"><label className="form-label" htmlFor="filter-station">Station ID</label>
          <input id="filter-station" className="form-control" value={draft.stationId} onChange={e => setDraft({ ...draft, stationId: e.target.value })} /></div>
      </div>
      <div className="d-flex flex-wrap gap-2 mt-3">
        <button className="btn btn-outline-primary" type="submit">Apply filters</button>
        <button className="btn btn-outline-secondary" type="button" onClick={() => { setDraft(blank); setFilters(blank); }}>Clear filters</button>
        <button className="btn btn-outline-secondary" type="button" disabled={loading} onClick={reload}>Refresh</button>
      </div>
    </form>
    <ErrorNotice error={error} retry={reload} />
    {loading ? <Loading /> : !error && (data?.length ? <ReservationTable items={data} reviewPending caption="Reservations ordered newest first. View a reservation to modify or cancel it."/> : <section className="surface-card text-center py-5">
      <h2 className="h4">{filtered ? 'No matching reservations' : 'No reservations yet'}</h2>
      <p>{filtered ? 'Adjust or clear the filters to see other reservations.' : 'Create an assisted reservation for an active Prosumer to get started.'}</p>
    </section>)}
  </>;
}
