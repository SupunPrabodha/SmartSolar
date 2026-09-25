import { useCallback, useState } from 'react';
import { searchBookings } from '../../api/reservations.js';
import { ErrorNotice, Loading, PaginationControls, ReservationTable } from './ReservationComponents.jsx';
import { reservationStatuses } from './reservationUi.js';
import { useReservationData } from './useReservationData.js';

const initialFilters = {
  reservationId: '',
  prosumerNic: '',
  stationId: '',
  status: '',
  fromUtc: '',
  toUtc: ''
};

export default function SearchBookingsPage() {
  const [draft, setDraft] = useState(initialFilters);
  const [activeFilters, setActiveFilters] = useState(initialFilters);
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const load = useCallback(signal => {
    const params = { page, pageSize };
    for (const [key, value] of Object.entries(activeFilters)) {
      if (value && value.trim()) {
        params[key] = value.trim();
      }
    }
    return searchBookings(params, { signal });
  }, [activeFilters, page]);

  const { data, error, loading, reload } = useReservationData(load);

  const items = data?.items ?? [];
  const hasMore = Boolean(data?.hasMore);
  const hasActiveFilters = Object.values(activeFilters).some(v => Boolean(v && v.trim()));

  function handleSearch(event) {
    event.preventDefault();
    setPage(1);
    setActiveFilters({ ...draft });
  }

  function handleClear() {
    setDraft(initialFilters);
    setActiveFilters(initialFilters);
    setPage(1);
  }

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
        <div>
          <p className="eyebrow">QUERY RESERVATIONS</p>
          <h1 className="h2">Search Bookings</h1>
          <p className="text-secondary mb-0">Search and filter reservations using server-validated exact parameters. All dates UTC.</p>
        </div>
        <button className="btn btn-outline-secondary" type="button" disabled={loading} onClick={reload}>
          Refresh
        </button>
      </div>

      <form className="surface-card mb-4" onSubmit={handleSearch} aria-label="Search reservation filters">
        <div className="row g-3">
          <div className="col-md-4">
            <label className="form-label" htmlFor="search-reservation-id">Reservation ID</label>
            <input
              id="search-reservation-id"
              className="form-control"
              placeholder="e.g. 32-char GUID"
              value={draft.reservationId}
              onChange={e => setDraft({ ...draft, reservationId: e.target.value })}
            />
          </div>

          <div className="col-md-4">
            <label className="form-label" htmlFor="search-nic">Prosumer NIC</label>
            <input
              id="search-nic"
              className="form-control"
              placeholder="12 digits or 9+V/X"
              value={draft.prosumerNic}
              onChange={e => setDraft({ ...draft, prosumerNic: e.target.value })}
            />
          </div>

          <div className="col-md-4">
            <label className="form-label" htmlFor="search-station">Station ID</label>
            <input
              id="search-station"
              className="form-control"
              placeholder="Station GUID"
              value={draft.stationId}
              onChange={e => setDraft({ ...draft, stationId: e.target.value })}
            />
          </div>

          <div className="col-md-4">
            <label className="form-label" htmlFor="search-status">Status</label>
            <select
              id="search-status"
              className="form-select"
              value={draft.status}
              onChange={e => setDraft({ ...draft, status: e.target.value })}
            >
              <option value="">All statuses</option>
              {reservationStatuses.map(status => (
                <option key={status} value={status}>{status}</option>
              ))}
            </select>
          </div>

          <div className="col-md-4">
            <label className="form-label" htmlFor="search-from-utc">From (UTC)</label>
            <input
              id="search-from-utc"
              className="form-control"
              type="datetime-local"
              value={draft.fromUtc}
              onChange={e => setDraft({ ...draft, fromUtc: e.target.value })}
            />
          </div>

          <div className="col-md-4">
            <label className="form-label" htmlFor="search-to-utc">To (UTC)</label>
            <input
              id="search-to-utc"
              className="form-control"
              type="datetime-local"
              value={draft.toUtc}
              onChange={e => setDraft({ ...draft, toUtc: e.target.value })}
            />
          </div>
        </div>

        <div className="d-flex flex-wrap gap-2 mt-3">
          <button className="btn btn-primary" type="submit">
            Search
          </button>
          <button className="btn btn-outline-secondary" type="button" onClick={handleClear}>
            Clear filters
          </button>
        </div>
      </form>

      <ErrorNotice error={error} retry={reload} />

      {loading ? (
        <Loading />
      ) : !error && (
        items.length > 0 ? (
          <>
            <ReservationTable items={items} caption="Search results matching applied query filters." />
            <PaginationControls page={page} hasMore={hasMore} onPageChange={setPage} loading={loading} />
          </>
        ) : (
          <section className="surface-card text-center py-5">
            <h2 className="h4">{hasActiveFilters ? 'No matching bookings' : 'No bookings found'}</h2>
            <p className="text-secondary mb-0">
              {hasActiveFilters
                ? 'Try adjusting or clearing your filter criteria to see other reservations.'
                : 'No reservations are currently recorded.'}
            </p>
          </section>
        )
      )}
    </div>
  );
}
