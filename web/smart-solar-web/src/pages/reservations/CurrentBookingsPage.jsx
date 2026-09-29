import { ExportButton } from '../../components/Experience';
import { localTimeZone } from './reservationUi.js';
import { useCallback, useState } from 'react';
import { getCurrentBookings } from '../../api/reservations.js';
import { ErrorNotice, Loading, PaginationControls, ReservationTable } from './ReservationComponents.jsx';
import { useReservationData } from './useReservationData.js';

export default function CurrentBookingsPage() {
  const [page, setPage] = useState(1);
  const pageSize = 20;
  const load = useCallback(signal => getCurrentBookings({ page, pageSize }, { signal }), [page]);
  const { data, error, loading, reload } = useReservationData(load);
  const items = data?.items ?? [];

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
        <div>
          <p className="eyebrow">LIVE BOOKINGS</p>
          <h1 className="h2">Active Bookings</h1>
          <p className="text-secondary mb-0">Approved reservations whose accepted end is still ahead. Times shown in your local timezone ({localTimeZone()}).</p>
        </div>
        <ExportButton kind="reservations" query="view=current" />
        <button className="btn btn-outline-secondary" type="button" disabled={loading} onClick={reload}>Refresh</button>
      </div>
      <ErrorNotice error={error} retry={reload} />
      {loading ? <Loading /> : !error && (items.length ? <>
        <ReservationTable items={items} caption="Active reservations ordered by accepted start." />
        <PaginationControls page={page} hasMore={Boolean(data?.hasMore)} onPageChange={setPage} loading={loading} />
      </> : <section className="surface-card text-center py-5">
        <h2 className="h4">No active bookings</h2>
        <p className="text-secondary mb-0">No Approved reservations are currently in progress or scheduled.</p>
      </section>)}
    </div>
  );
}
