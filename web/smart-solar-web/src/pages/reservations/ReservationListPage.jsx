import { useCallback, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listReservations } from '../../api/reservations.js';
import { ErrorNotice, Loading, StatusBadge } from './ReservationComponents';
import { formatUtc, reservationStatuses } from './reservationUi.js';
import { useReservationData } from './useReservationData.js';

export default function ReservationListPage() {
  const [searchParams] = useSearchParams();
  const blank = { status: '', prosumerNic: '', stationId: '' };
  const initial = { ...blank, status: searchParams.get('status') ?? '' };
  const [draft, setDraft] = useState(initial);
  const [filters, setFilters] = useState(initial);
  const load = useCallback(signal => listReservations(filters, { signal }), [filters]);
  const { data, error, loading, reload } = useReservationData(load);
  const filtered = Object.values(filters).some(Boolean);
  function apply(event) {
    event.preventDefault();
    setFilters(Object.fromEntries(Object.entries(draft).map(([key, value]) => [key, value.trim()])));
  }
  return <>
    <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
      <div><p className="eyebrow">RESERVATION MANAGEMENT</p><h1 className="h2">Reservations</h1>
        <p className="text-secondary mb-0">Inspect and assist with energy reservations. All times are UTC.</p></div>
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
    {loading ? <Loading /> : !error && (data?.length ? <div className="surface-card p-0 overflow-hidden">
      <div className="table-responsive" tabIndex="0" role="region" aria-label="Reservation table">
        <table className="table table-hover align-middle mb-0 reservation-table">
          <caption className="px-3">Reservations ordered newest first. View a reservation to modify or cancel it.</caption>
          <thead><tr>{['Reservation ID', 'Prosumer', 'Station / slot', 'Accepted schedule (UTC)', 'Energy', 'Status', 'Action'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
          <tbody>{data.map(row => <tr key={row.reservationId}>
            <td className="text-break">{row.reservationId}</td><td>{row.prosumerNic}</td>
            <td className="text-break"><div>{row.stationId}</div><small className="text-secondary">Slot: {row.slotId}</small></td>
            <td><div>{formatUtc(row.scheduledStartAtUtc)}</div><small>to {formatUtc(row.scheduledEndAtUtc)}</small></td>
            <td>{row.energyAmountKwh} kWh</td>
            <td>
              <StatusBadge status={row.status} />
              {row.status === 'Rejected' && row.rejectionRemark && (
                <div className="small text-danger text-truncate" style={{ maxWidth: '160px' }} title={row.rejectionRemark}>
                  {row.rejectionRemark}
                </div>
              )}
            </td>
            <td>
              <Link
                className="btn btn-outline-primary btn-sm"
                aria-label={`${row.status === 'Pending' ? 'Review' : 'View'} reservation ${row.reservationId}`}
                to={encodeURIComponent(row.reservationId)}
              >
                {row.status === 'Pending' ? 'Review' : 'View'}
              </Link>
            </td>
          </tr>)}</tbody>
        </table>
      </div>
    </div> : <section className="surface-card text-center py-5">
      <h2 className="h4">{filtered ? 'No matching reservations' : 'No reservations yet'}</h2>
      <p>{filtered ? 'Adjust or clear the filters to see other reservations.' : 'Create an assisted reservation for an active Prosumer to get started.'}</p>
    </section>)}
  </>;
}
