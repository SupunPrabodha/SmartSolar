import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { createReservation, getReservation, updateReservation } from '../../api/reservations.js';
import { ErrorNotice, Loading, OperationSuccess, ReservationSummary } from './ReservationComponents';
import { changeRestriction, fieldMessages, validateReservationForm } from './reservationUi.js';
import { useReservationData, useReservationMutation } from './useReservationData.js';

export default function ReservationFormPage({ creating = false }) {
  const { reservationId } = useParams();
  return creating ? <ReservationForm key="new" /> : <Edit key={reservationId} reservationId={reservationId} />;
}

function Edit({ reservationId }) {
  const load = useCallback(signal => getReservation(reservationId, { signal }), [reservationId]);
  const { data, error, loading, reload } = useReservationData(load);
  return <><ErrorNotice error={error} retry={reload} />{loading ? <Loading />
    : data && <ReservationForm key={data.updatedAtUtc} existing={data} />}</>;
}

function ReservationForm({ existing }) {
  const creating = !existing;
  const [values, setValues] = useState({
    prosumerNic: existing?.prosumerNic ?? '', slotId: existing?.slotId ?? '',
    energyAmountKwh: existing ? String(existing.energyAmountKwh) : ''
  });
  const [errors, setErrors] = useState({});
  const [reviewing, setReviewing] = useState(false);
  const [result, setResult] = useState(null);
  const [now, setNow] = useState(Date.now);
  const mutation = useReservationMutation();
  const form = useRef(null);
  const reviewHeading = useRef(null);
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    if (reviewing) reviewHeading.current?.focus();
    else if (Object.keys(errors).length) form.current?.querySelector('[aria-invalid="true"]')?.focus();
  }, [reviewing, errors]);
  const restriction = existing ? changeRestriction(existing, now) : '';
  const back = existing ? `/operator/reservations/${encodeURIComponent(existing.reservationId)}` : '/operator/reservations';
  function onReview(event) {
    event.preventDefault();
    const validation = validateReservationForm(values, creating);
    setErrors(validation);
    mutation.clearError();
    if (!Object.keys(validation).length && !restriction) setReviewing(true);
  }
  function save() {
    if (restriction) return;
    const request = { slotId: values.slotId.trim(), energyAmountKwh: Number(values.energyAmountKwh) };
    mutation.run(() => creating
      ? createReservation(values.prosumerNic.trim().toUpperCase(), request)
      : updateReservation(existing.reservationId, request), setResult);
  }
  function field(name, label, extra = {}) {
    const messages = [errors[name], ...fieldMessages(mutation.error, name)].filter(Boolean);
    return <div className="mb-3">
      <label className="form-label" htmlFor={name}>{label}</label>
      <input id={name} name={name} className={`form-control${messages.length ? ' is-invalid' : ''}`}
        value={values[name]} onChange={event => setValues({ ...values, [name]: event.target.value })}
        required aria-invalid={messages.length ? 'true' : undefined}
        aria-describedby={messages.length ? name + '-error' : undefined} {...extra} />
      {!!messages.length && <div id={name + '-error'} className="invalid-feedback">{messages.join(' ')}</div>}
    </div>;
  }
  if (result) return <OperationSuccess title={creating ? 'Reservation created' : 'Reservation updated'} reservation={result} />;
  return <section className="surface-card reservation-form">
    <h1 className="h2">{creating ? 'New assisted reservation' : 'Modify reservation'}</h1>
    <p className="mb-4">{creating ? 'Create a reservation for an active Prosumer.' : 'A successful modification returns the reservation to Pending for approval.'}</p>
    {existing && <details className="mb-4"><summary>Current reservation</summary><div className="mt-3"><ReservationSummary reservation={existing} /></div></details>}
    {restriction && <div className="alert alert-info">{restriction} <Link to={back}>Back to details</Link></div>}
    <ErrorNotice error={mutation.error} mutation />
    {!reviewing ? <form ref={form} onSubmit={onReview} noValidate>
      <fieldset disabled={!!restriction || mutation.pending}>
        <legend className="visually-hidden">Reservation information</legend>
        {field('prosumerNic', 'Prosumer NIC', { readOnly: !creating, autoComplete: 'off' })}
        {field('slotId', 'Slot ID', { autoComplete: 'off', spellCheck: false })}
        <p className="mb-3">Use a known active slot ID from your station records. The server confirms its station, schedule and availability.</p>
        {field('energyAmountKwh', 'Energy amount (kWh)', { type: 'number', step: 'any', min: '0', inputMode: 'decimal' })}
        <p className="mb-4">The slot must start within seven days. Modifications require at least 12 hours’ notice for both the current and replacement starts.</p>
        <button className="btn btn-primary" type="submit">Review reservation</button>
      </fieldset>
      <Link className="btn btn-link mt-2" to={back}>Back without changes</Link>
    </form> : <div>
      <h2 ref={reviewHeading} tabIndex="-1" className="h4">Review your request</h2>
      <dl>
        <dt>Prosumer NIC</dt><dd>{values.prosumerNic.trim().toUpperCase()}</dd>
        <dt>Requested slot ID</dt><dd className="text-break">{values.slotId.trim()}</dd>
        <dt>Energy amount</dt><dd>{values.energyAmountKwh} kWh</dd>
      </dl>
      <p>The accepted station and schedule will appear in the confirmation after the server validates your request.</p>
      <div className="d-flex flex-wrap gap-2 mt-4">
        <button className="btn btn-primary" disabled={mutation.pending || !!restriction} onClick={save}>
          {mutation.pending ? 'Saving…' : creating ? 'Confirm reservation' : 'Confirm changes'}
        </button>
        <button className="btn btn-outline-secondary" disabled={mutation.pending} onClick={() => setReviewing(false)}>Back to form</button>
      </div>
      {mutation.pending && <p className="mt-3" role="status">Waiting for confirmation…</p>}
    </div>}
  </section>;
}
