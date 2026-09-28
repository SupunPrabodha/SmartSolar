import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getReservationDashboardSummary } from '../../api/reservations.js';
import Icon from '../../components/Icon';
import { ErrorNotice, Loading } from './ReservationComponents.jsx';
import { formatUtc } from './reservationUi.js';
import { useReservationData } from './useReservationData.js';

export default function OperationsDashboardPage({ greeting }) {
  const load = useCallback(signal => getReservationDashboardSummary({ signal }), []);
  const { data: summary, error, loading, reload } = useReservationData(load);
  return <div>
    <div className="page-heading"><div><p className="eyebrow">ENERGY OPERATIONS</p><h1>{greeting || 'Reservation Operations'}</h1><p className="text-secondary mb-0">Monitor bookings, energy slots and transfer activity.</p></div><button className="btn btn-outline-secondary" disabled={loading} onClick={reload}>Refresh metrics</button></div>
    <ErrorNotice error={error} retry={reload}/>
    {loading ? <Loading/> : !error && summary && <>
      <section className="metrics-grid metrics-pair" aria-label="Reservation metrics">
        <article className="surface-card metric-card metric-pending"><div><p className="card-label">Pending Reservations</p><strong className="metric-value">{summary.pendingReservations}</strong><p>Awaiting operator review</p></div><Icon name="pending"/></article>
        <article className="surface-card metric-card metric-approved"><div><p className="card-label">Approved Future Reservations</p><strong className="metric-value">{summary.approvedFutureReservations}</strong><p>Scheduled energy transfers</p></div><Icon name="current"/></article>
      </section>
      {summary.generatedAtUtc && <p className="snapshot-note">Last updated {formatUtc(summary.generatedAtUtc)}</p>}
    </>}
    <h2 className="section-title">Operational shortcuts</h2>
    <div className="quick-actions">
      {[['','Manage Reservations','Review and assist bookings.','bookings'],['?status=Pending','Pending Queue','Approve or reject requests.','pending'],['/current','Current Bookings','Track upcoming and active transfers.','current'],['/search','Search Bookings','Find a reservation quickly.','search'],['/history','Booking History','Review concluded bookings.','history']].map(([path,title,description,icon]) =>
        <Link key={path} className="quick-action" to={'/operator/reservations'+path}><Icon name={icon}/><span><strong>{title}</strong><small>{description}</small></span><span aria-hidden="true">→</span></Link>)}
      <Link className="quick-action" to="/stations"><Icon name="station"/><span><strong>Stations &amp; Slots</strong><small>Manage published energy availability.</small></span><span aria-hidden="true">→</span></Link>
    </div>
  </div>;
}
