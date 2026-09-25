import { useCallback, useState } from 'react';
import { getPendingBookings } from '../../api/reservations.js';
import { ErrorNotice, Loading, PaginationControls, ReservationTable } from './ReservationComponents.jsx';
import { useReservationData } from './useReservationData.js';

export default function PendingBookingsPage() {
  const [page, setPage] = useState(1);
  const pageSize = 20;
  const load = useCallback(signal => getPendingBookings({ page, pageSize }, { signal }), [page]);
  const { data, error, loading, reload } = useReservationData(load);

  const items = data?.items ?? [];
  const hasMore = Boolean(data?.hasMore);

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
        <div>
          <p className="eyebrow">APPROVAL QUEUE</p>
          <h1 className="h2">Pending Bookings</h1>
          <p className="text-secondary mb-0">Reservations awaiting review or approval. All times UTC.</p>
        </div>
        <button className="btn btn-outline-secondary" type="button" disabled={loading} onClick={reload}>
          Refresh
        </button>
      </div>

      <ErrorNotice error={error} retry={reload} />

      {loading ? (
        <Loading />
      ) : !error && (
        items.length > 0 ? (
          <>
            <ReservationTable items={items} caption="Pending energy reservations awaiting operator approval." />
            <PaginationControls page={page} hasMore={hasMore} onPageChange={setPage} loading={loading} />
          </>
        ) : (
          <section className="surface-card text-center py-5">
            <h2 className="h4">No pending bookings</h2>
            <p className="text-secondary mb-0">The pending reservation queue is currently empty.</p>
          </section>
        )
      )}
    </div>
  );
}
