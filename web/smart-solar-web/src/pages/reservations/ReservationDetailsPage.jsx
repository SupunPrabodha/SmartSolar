import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { cancelReservation, getReservation } from '../../api/reservations.js';
import { ErrorNotice, Loading, ReservationSummary } from './ReservationComponents';
import { changeRestriction } from './reservationUi.js';
import { useReservationData, useReservationMutation } from './useReservationData.js';

export default function ReservationDetailsPage() {
  const { reservationId } = useParams();
  return <Details key={reservationId} reservationId={reservationId} />;
}

function Details({ reservationId }) {
  const load = useCallback(signal => getReservation(reservationId, { signal }), [reservationId]);
  const { data, error, loading, reload, replace } = useReservationData(load);
  const mutation = useReservationMutation();
  const [confirming, setConfirming] = useState(false);
  const [success, setSuccess] = useState(false);
  const [now, setNow] = useState(Date.now);
  const dialog = useRef(null);
  const successRef = useRef(null);
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    if (confirming) dialog.current?.showModal();
    else dialog.current?.close();
  }, [confirming]);
  useEffect(() => { if (success) successRef.current?.focus(); }, [success]);
  const restriction = data ? changeRestriction(data, now) : '';
  function confirmCancel() {
    if (restriction) return;
    mutation.run(() => cancelReservation(reservationId), result => {
      replace(result);
      setConfirming(false);
      setSuccess(true);
    });
  }
  return <>
    <h1 className="h2 mb-4">Reservation details</h1>
    <ErrorNotice error={error} retry={reload} />
    {loading ? <Loading /> : data && <>
      {success && <div ref={successRef} tabIndex="-1" className="alert alert-success" role="status">
        Reservation cancelled. The confirmed summary is shown below.
      </div>}
      <section className="surface-card">
        <ReservationSummary reservation={data} />
        <p className="mt-2">Changes require at least 12 hours’ notice. The server checks the final cutoff.</p>
        {restriction && <div className="alert alert-info mt-3" id="change-restriction">{restriction}</div>}
        <div className="d-flex flex-wrap gap-2 mt-4">
          {restriction ? <button className="btn btn-primary" disabled aria-describedby="change-restriction">Modify reservation</button>
            : <Link className="btn btn-primary" to="edit">Modify reservation</Link>}
          <button className="btn btn-outline-danger" disabled={!!restriction || mutation.pending}
            aria-describedby={restriction ? 'change-restriction' : undefined}
            onClick={() => { mutation.clearError(); setConfirming(true); }}>Cancel reservation</button>
          <button className="btn btn-outline-secondary" disabled={mutation.pending} onClick={() => { setSuccess(false); reload(); }}>Refresh details</button>
          <Link className="btn btn-link" to="/operator/reservations">Back to reservations</Link>
        </div>
      </section>
      <dialog ref={dialog} className="reservation-dialog" aria-labelledby="cancel-title"
        onCancel={event => { if (mutation.pending) event.preventDefault(); else setConfirming(false); }}>
        <h2 id="cancel-title" className="h4">Cancel this reservation?</h2>
        <p className="text-break">Reservation {data.reservationId} for {data.prosumerNic}, {data.energyAmountKwh} kWh.</p>
        <p>This releases its reserved capacity. This action cannot be undone.</p>
        <ErrorNotice error={mutation.error} mutation />
        {restriction && <div className="alert alert-info">{restriction}</div>}
        <div className="d-flex flex-wrap gap-2 mt-3">
          <button className="btn btn-outline-secondary" autoFocus disabled={mutation.pending} onClick={() => setConfirming(false)}>Keep reservation</button>
          <button className="btn btn-danger" disabled={mutation.pending || !!restriction} onClick={confirmCancel}>
            {mutation.pending ? 'Cancelling…' : 'Confirm cancellation'}
          </button>
        </div>
        {mutation.pending && <p className="mt-3" role="status">Waiting for confirmation…</p>}
      </dialog>
    </>}
  </>;
}
