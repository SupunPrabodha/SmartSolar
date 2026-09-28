import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { approveReservation, cancelReservation, getReservation, rejectReservation } from '../../api/reservations.js';
import { ErrorNotice, Loading, ReservationSummary } from './ReservationComponents';
import { changeRestriction, shortReference } from './reservationUi.js';
import { useReservationData, useReservationMutation } from './useReservationData.js';

export default function ReservationDetailsPage() {
  const { reservationId } = useParams();
  return <Details key={reservationId} reservationId={reservationId} />;
}

function Details({ reservationId }) {
  const load = useCallback(signal => getReservation(reservationId, { signal }), [reservationId]);
  const { data, error, loading, reload, replace } = useReservationData(load);
  const mutation = useReservationMutation();
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [confirmingApprove, setConfirmingApprove] = useState(false);
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectRemark, setRejectRemark] = useState('');
  const [rejectRemarkError, setRejectRemarkError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [now, setNow] = useState(Date.now);

  const cancelDialog = useRef(null);
  const approveDialog = useRef(null);
  const rejectDialog = useRef(null);
  const successRef = useRef(null);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (confirmingCancel) cancelDialog.current?.showModal();
    else cancelDialog.current?.close();
  }, [confirmingCancel]);

  useEffect(() => {
    if (confirmingApprove) approveDialog.current?.showModal();
    else approveDialog.current?.close();
  }, [confirmingApprove]);

  useEffect(() => {
    if (rejectModalOpen) rejectDialog.current?.showModal();
    else rejectDialog.current?.close();
  }, [rejectModalOpen]);

  useEffect(() => {
    if (successMessage) successRef.current?.focus();
  }, [successMessage]);

  const restriction = data ? changeRestriction(data, now) : '';
  const isPending = data?.status === 'Pending';
  const isCancellable = data?.status === 'Pending' || data?.status === 'Approved';

  function confirmCancel() {
    if (restriction) return;
    mutation.run(() => cancelReservation(reservationId), result => {
      replace(result);
      setConfirmingCancel(false);
      setSuccessMessage('Reservation cancelled. The confirmed summary is shown below.');
    });
  }

  function confirmApprove() {
    mutation.run(() => approveReservation(reservationId), result => {
      replace(result);
      setConfirmingApprove(false);
      setSuccessMessage('Reservation approved successfully. The confirmed summary is shown below.');
    });
  }

  function confirmReject(event) {
    event?.preventDefault?.();
    if (!rejectRemark.trim()) {
      setRejectRemarkError('Please provide a reason for rejecting this reservation.');
      return;
    }
    setRejectRemarkError('');
    mutation.run(() => rejectReservation(reservationId, { remark: rejectRemark.trim() }), result => {
      replace(result);
      setRejectModalOpen(false);
      setSuccessMessage('Reservation rejected. 1 slot capacity has been released back to the station.');
    });
  }

  return <>
    <h1 className="h2 mb-4">{data ? `Reservation #${shortReference(data.reservationId)}` : 'Reservation details'}</h1>
    <ErrorNotice error={error} retry={reload} />
    {loading ? <Loading /> : data && <>
      {successMessage && <div ref={successRef} tabIndex="-1" className="alert alert-success" role="status">
        {successMessage}
      </div>}
      <section className="surface-card">
        <ReservationSummary reservation={data} />
        <p className="mt-2 text-secondary small">Changes require at least 12 hours’ notice. The server checks the final cutoff.</p>
        {restriction && <div className="alert alert-info mt-3" id="change-restriction">{restriction}</div>}

        <div className="record-actions d-flex flex-wrap gap-2 mt-4">
          {isPending && <>
            <button className="btn btn-success" disabled={mutation.pending}
              onClick={() => { mutation.clearError(); setConfirmingApprove(true); }}>
              Approve reservation
            </button>
            <button className="btn btn-danger" disabled={mutation.pending}
              onClick={() => { mutation.clearError(); setRejectRemark(''); setRejectRemarkError(''); setRejectModalOpen(true); }}>
              Reject reservation
            </button>
          </>}

          {restriction ? <button className="btn btn-primary" disabled aria-describedby="change-restriction">Modify reservation</button>
            : <Link className="btn btn-primary" to="edit">Modify reservation</Link>}

          <button className="btn btn-outline-danger" disabled={!!restriction || mutation.pending}
            aria-describedby={restriction ? 'change-restriction' : undefined}
            onClick={() => { mutation.clearError(); setConfirmingCancel(true); }}>
            Cancel reservation
          </button>

          <button className="btn btn-outline-secondary" disabled={mutation.pending}
            onClick={() => { setSuccessMessage(''); reload(); }}>
            Refresh details
          </button>
          <Link className="btn btn-link" to="/operator/reservations">Back to reservations</Link>
        </div>
      </section>

      {/* Cancel Dialog */}
      <dialog ref={cancelDialog} className="reservation-dialog" aria-labelledby="cancel-title"
        onCancel={event => { if (mutation.pending) event.preventDefault(); else setConfirmingCancel(false); }}>
        <h2 id="cancel-title" className="h4">Cancel this reservation?</h2>
        <p className="text-break">Reservation {data.reservationId} for {data.prosumerNic}, {data.energyAmountKwh} kWh.</p>
        <p>This releases its reserved capacity. This action cannot be undone.</p>
        <ErrorNotice error={mutation.error} mutation />
        {restriction && <div className="alert alert-info">{restriction}</div>}
        <div className="d-flex flex-wrap gap-2 mt-3">
          <button className="btn btn-outline-secondary" autoFocus disabled={mutation.pending} onClick={() => setConfirmingCancel(false)}>Keep reservation</button>
          <button className="btn btn-danger" disabled={mutation.pending || !!restriction} onClick={confirmCancel}>
            {mutation.pending ? 'Cancelling…' : 'Confirm cancellation'}
          </button>
        </div>
        {mutation.pending && <p className="mt-3" role="status">Waiting for confirmation…</p>}
      </dialog>

      {/* Approve Dialog */}
      <dialog ref={approveDialog} className="reservation-dialog" aria-labelledby="approve-title"
        onCancel={event => { if (mutation.pending) event.preventDefault(); else setConfirmingApprove(false); }}>
        <h2 id="approve-title" className="h4">Approve this reservation?</h2>
        <p className="text-break">Approve reservation {data.reservationId} for Prosumer <strong>{data.prosumerNic}</strong> ({data.energyAmountKwh} kWh).</p>
        <p className="text-secondary small">This changes the reservation status to <strong>Approved</strong> and keeps slot capacity allocated.</p>
        <ErrorNotice error={mutation.error} mutation />
        <div className="d-flex flex-wrap gap-2 mt-3">
          <button className="btn btn-outline-secondary" autoFocus disabled={mutation.pending} onClick={() => setConfirmingApprove(false)}>Cancel</button>
          <button className="btn btn-success" disabled={mutation.pending} onClick={confirmApprove}>
            {mutation.pending ? 'Approving…' : 'Confirm approval'}
          </button>
        </div>
        {mutation.pending && <p className="mt-3" role="status">Processing approval…</p>}
      </dialog>

      {/* Reject Dialog */}
      <dialog ref={rejectDialog} className="reservation-dialog" aria-labelledby="reject-title"
        onCancel={event => { if (mutation.pending) event.preventDefault(); else setRejectModalOpen(false); }}>
        <form onSubmit={confirmReject}>
          <h2 id="reject-title" className="h4 text-danger">Reject this reservation?</h2>
          <p className="text-break">Reject reservation {data.reservationId} for Prosumer <strong>{data.prosumerNic}</strong>.</p>
          <p className="text-secondary small">This releases 1 slot capacity back to the station. Please state why the reservation is being rejected:</p>

          <div className="mb-3">
            <label htmlFor="reject-remark" className="form-label fw-bold">Rejection Reason / Remark <span className="text-danger">*</span></label>
            <textarea
              id="reject-remark"
              className={`form-control ${rejectRemarkError ? 'is-invalid' : ''}`}
              rows="3"
              maxLength="500"
              placeholder="e.g., Station scheduled for grid maintenance during this slot window."
              value={rejectRemark}
              onChange={e => { setRejectRemark(e.target.value); if (rejectRemarkError) setRejectRemarkError(''); }}
              disabled={mutation.pending}
              required
            />
            {rejectRemarkError && <div className="invalid-feedback">{rejectRemarkError}</div>}
          </div>

          <ErrorNotice error={mutation.error} mutation />

          <div className="d-flex flex-wrap gap-2 mt-3">
            <button type="button" className="btn btn-outline-secondary" disabled={mutation.pending} onClick={() => setRejectModalOpen(false)}>Cancel</button>
            <button type="submit" className="btn btn-danger" disabled={mutation.pending} onClick={confirmReject}>
              {mutation.pending ? 'Rejecting…' : 'Confirm rejection'}
            </button>
          </div>
          {mutation.pending && <p className="mt-3" role="status">Processing rejection…</p>}
        </form>
      </dialog>
    </>}
  </>;
}
