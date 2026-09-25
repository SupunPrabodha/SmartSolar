import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import { getReservationDashboardSummary } from '../../api/reservations.js';
import { ErrorNotice, Loading } from './ReservationComponents.jsx';
import { formatUtc } from './reservationUi.js';
import { useReservationData } from './useReservationData.js';

export default function OperationsDashboardPage() {
  const load = useCallback(signal => getReservationDashboardSummary({ signal }), []);
  const { data: summary, error, loading, reload } = useReservationData(load);

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
        <div>
          <p className="eyebrow">OPERATIONS DASHBOARD</p>
          <h1 className="h2">Reservation Operations</h1>
          <p className="text-secondary mb-0">Live microgrid reservation metrics and quick operational navigation.</p>
        </div>
        <button className="btn btn-outline-secondary" type="button" disabled={loading} onClick={reload}>
          Refresh metrics
        </button>
      </div>

      <ErrorNotice error={error} retry={reload} />

      {loading ? (
        <Loading />
      ) : !error && summary ? (
        <>
          <section aria-label="Reservation metrics" className="session-grid mb-4">
            <article className="surface-card">
              <p className="card-label">PENDING RESERVATIONS</p>
              <h2 className="display-6 fw-bold text-primary mb-1">{summary.pendingReservations}</h2>
              <p className="text-secondary small mb-0">Awaiting operator review or approval.</p>
            </article>

            <article className="surface-card">
              <p className="card-label">APPROVED FUTURE RESERVATIONS</p>
              <h2 className="display-6 fw-bold text-success mb-1">{summary.approvedFutureReservations}</h2>
              <p className="text-secondary small mb-0">Scheduled for future transfer timeslots.</p>
            </article>

            <article className="surface-card">
              <p className="card-label">METRICS SNAPSHOT</p>
              <h2 className="h4 mb-1">Server Calculated</h2>
              <p className="text-secondary small mb-0">
                {summary.generatedAtUtc ? `Generated at ${formatUtc(summary.generatedAtUtc)}` : 'Live API metrics'}
              </p>
            </article>
          </section>

          <section aria-labelledby="quick-actions-heading" className="mb-4">
            <h2 id="quick-actions-heading" className="h4 mb-3">Operational Views</h2>
            <div className="module-grid">
              <article className="surface-card module-card">
                <span className="module-number" aria-hidden="true">01</span>
                <h3>Current Bookings</h3>
                <p>View active reservations (Pending or Approved) scheduled now or in the future.</p>
                <Link className="btn btn-outline-primary w-100 mt-auto" to="/operator/reservations/current">
                  Open Current Bookings
                </Link>
              </article>

              <article className="surface-card module-card">
                <span className="module-number" aria-hidden="true">02</span>
                <h3>Pending Queue</h3>
                <p>Inspect all pending reservations awaiting operator action.</p>
                <Link className="btn btn-outline-primary w-100 mt-auto" to="/operator/reservations/pending">
                  Open Pending Queue
                </Link>
              </article>

              <article className="surface-card module-card">
                <span className="module-number" aria-hidden="true">03</span>
                <h3>Booking History</h3>
                <p>Review past and terminal reservations across the microgrid.</p>
                <Link className="btn btn-outline-primary w-100 mt-auto" to="/operator/reservations/history">
                  Open Booking History
                </Link>
              </article>

              <article className="surface-card module-card">
                <span className="module-number" aria-hidden="true">04</span>
                <h3>Search &amp; Filter</h3>
                <p>Query reservations by ID, Prosumer NIC, Station ID, status, or date range.</p>
                <Link className="btn btn-outline-primary w-100 mt-auto" to="/operator/reservations/search">
                  Search Bookings
                </Link>
              </article>
            </div>
          </section>
        </>
      ) : null}
    </div>
  );
}
