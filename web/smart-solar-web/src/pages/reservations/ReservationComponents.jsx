import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useEffect, useRef } from 'react';
import { useAuth } from '../../auth/AuthContext';
import Brand from '../../components/Brand';
import { errorMessage, formatUtc } from './reservationUi.js';

export function ReservationLayout() {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const main = useRef(null);
  useEffect(() => { main.current?.focus(); }, [pathname]);
  return <div className="reservation-workspace container py-4">
    <a className="skip-link" href="#reservation-main">Skip to content</a>
    <header className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
      <Brand />
      <div className="d-flex flex-wrap align-items-center gap-3">
        <span>{user.fullName}</span><span className="role-pill">Grid Operator</span>
        <button className="btn btn-outline-secondary btn-sm" onClick={logout}>Sign out</button>
      </div>
    </header>
    <nav className="d-flex flex-wrap gap-3 mb-4" aria-label="Reservation navigation">
      <Link to="/">Back to workspace</Link>
      <NavLink end to="/operator/reservations">Reservations</NavLink>
      <NavLink to="/operator/reservations/new">New reservation</NavLink>
    </nav>
    <main ref={main} id="reservation-main" tabIndex="-1"><Outlet /></main>
  </div>;
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
