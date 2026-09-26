import { Link, Outlet, useLocation } from 'react-router-dom';
import { useEffect, useRef } from 'react';
import HomePage from '../HomePage';
import { errorMessage, formatUtc } from './reservationUi.js';

export function ReservationLayout() {
  const { pathname } = useLocation();
  const content = useRef(null);
  useEffect(() => { content.current?.focus(); }, [pathname]);
  return <HomePage><div ref={content} tabIndex="-1" className="pb-5"><Outlet /></div></HomePage>;
}

export function Loading() {
  return <div className="py-5 text-center" role="status"><span className="spinner-border spinner-border-sm me-2" aria-hidden="true" />Loading reservations…</div>;
}

export function ErrorNotice({ error, retry, mutation = false }) {
  const ref = useRef(null);
  useEffect(() => { if (error) ref.current?.focus(); }, [error]);
  if (!error) return null;
  return <div ref={ref} tabIndex="-1" className="alert alert-danger" role="alert">
    <div>{errorMessage(error, mutation)}</div>
    {!!Object.keys(error.errors ?? {}).length && <ul className="mb-0 mt-2">
      {Object.entries(error.errors).flatMap(([key, messages]) => messages.map((message, index) =>
        <li key={key + index}>{message}</li>))}
    </ul>}
    {error.traceId && <small className="d-block text-break mt-2">Support reference: {error.traceId}</small>}
    {retry && <button className="btn btn-outline-danger btn-sm mt-2" onClick={retry}>Try again</button>}
  </div>;
}

export function StatusBadge({ status }) {
  const color = { Pending: 'warning', Approved: 'success', Cancelled: 'secondary', Rejected: 'danger', Completed: 'primary' }[status] ?? 'secondary';
  return <span className={`badge text-bg-${color}`}>{status}</span>;
}

export function ReservationSummary({ reservation }) {
  const fields = [
    ['Reservation ID', reservation.reservationId], ['Prosumer NIC', reservation.prosumerNic],
    ['Station ID', reservation.stationId], ['Slot ID', reservation.slotId],
    ['Accepted start', formatUtc(reservation.scheduledStartAtUtc)], ['Accepted end', formatUtc(reservation.scheduledEndAtUtc)],
    ['Energy amount', `${reservation.energyAmountKwh} kWh`], ['Status', <StatusBadge key="status" status={reservation.status} />],
    ['Change cutoff', formatUtc(new Date(Date.parse(reservation.scheduledStartAtUtc) - 12 * 3600000))]
  ];
  if (reservation.status === 'Rejected' && reservation.rejectionRemark) {
    fields.push(['Rejection Reason', <span key="remark" className="text-danger fw-bold">{reservation.rejectionRemark}</span>]);
  }
  return <dl className="row reservation-summary mb-0">
    {fields.map(([label, value]) => <div className="col-md-6 mb-3" key={label}>
      <dt className="small text-secondary">{label}</dt><dd className="text-break mb-0">{value}</dd>
    </div>)}
  </dl>;
}

export function OperationSuccess({ title, reservation }) {
  const heading = useRef(null);
  useEffect(() => { heading.current?.focus(); }, []);
  return <section className="surface-card">
    <h1 ref={heading} tabIndex="-1" className="h3">{title}</h1>
    <div className="alert alert-success" role="status">The server confirmed this reservation.</div>
    <ReservationSummary reservation={reservation} />
    <div className="d-flex gap-2 flex-wrap mt-3">
      <Link className="btn btn-primary" to={`/operator/reservations/${encodeURIComponent(reservation.reservationId)}`}>View reservation</Link>
      <Link className="btn btn-outline-secondary" to="/operator/reservations">Back to reservations</Link>
    </div>
  </section>;
}

export function PaginationControls({ page, hasMore, onPageChange, loading }) {
  if (page <= 1 && !hasMore) return null;
  return <div className="d-flex justify-content-between align-items-center mt-3 pt-2">
    <button className="btn btn-outline-secondary btn-sm" disabled={page <= 1 || loading}
      onClick={() => onPageChange(page - 1)}>Previous page</button>
    <span className="small text-secondary">Page {page}</span>
    <button className="btn btn-outline-secondary btn-sm" disabled={!hasMore || loading}
      onClick={() => onPageChange(page + 1)}>Next page</button>
  </div>;
}

export function ReservationTable({ items, caption }) {
  return <div className="surface-card p-0 overflow-hidden">
    <div className="table-responsive" tabIndex="0" role="region" aria-label="Reservation table">
      <table className="table table-hover align-middle mb-0 reservation-table">
        {caption && <caption className="px-3">{caption}</caption>}
        <thead><tr>{['Reservation ID', 'Prosumer', 'Station / slot', 'Accepted schedule (UTC)', 'Energy', 'Status', 'Action'].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
        <tbody>{items.map(row => <tr key={row.reservationId}>
          <td className="text-break">{row.reservationId}</td><td>{row.prosumerNic}</td>
          <td className="text-break"><div>{row.stationId}</div><small className="text-secondary">Slot: {row.slotId}</small></td>
          <td><div>{formatUtc(row.scheduledStartAtUtc)}</div><small>to {formatUtc(row.scheduledEndAtUtc)}</small></td>
          <td>{row.energyAmountKwh} kWh</td><td><StatusBadge status={row.status} /></td>
          <td><Link className="btn btn-outline-primary btn-sm" aria-label={`View reservation ${row.reservationId}`} to={`/operator/reservations/${encodeURIComponent(row.reservationId)}`}>View</Link></td>
        </tr>)}</tbody>
      </table>
    </div>
  </div>;
}
