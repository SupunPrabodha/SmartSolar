import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { createReservation, getReservation, listAvailableSlots, updateReservation } from '../../api/reservations.js';
import { ErrorNotice, Loading, OperationSuccess, ReservationSummary } from './ReservationComponents';
import { changeRestriction, fieldMessages, formatUtc, shortReference, validateReservationForm } from './reservationUi.js';
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
  const [availableSlots, setAvailableSlots] = useState([]);
  const [slotsLoading, setSlotsLoading] = useState(true);
  const [manualSlot, setManualSlot] = useState(false);
  const mutation = useReservationMutation();
  const form = useRef(null);
  const reviewHeading = useRef(null);

  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    const controller = new AbortController();
    setSlotsLoading(true);
    listAvailableSlots({ signal: controller.signal })
      .then(slots => {
        setAvailableSlots(Array.isArray(slots) ? slots : []);
        setSlotsLoading(false);
      })
      .catch(() => {
        setAvailableSlots([]);
        setSlotsLoading(false);
      });
    return () => controller.abort();
  }, []);

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

  function slotField() {
    const messages = [errors.slotId, ...fieldMessages(mutation.error, 'slotId')].filter(Boolean);
    return <div className="mb-3">
      <div className="d-flex justify-content-between align-items-center mb-1">
        <label className="form-label mb-0" htmlFor="slotId">Slot ID</label>
        <button type="button" className="btn btn-link btn-sm p-0 text-decoration-none"
          onClick={() => setManualSlot(!manualSlot)}>
          {manualSlot ? 'Select from active slots list' : 'Enter slot reference'}
        </button>
      </div>
      {!manualSlot ? (
        <select id="slotId" name="slotId" className={`form-select${messages.length ? ' is-invalid' : ''}`}
          value={values.slotId} onChange={event => setValues({ ...values, slotId: event.target.value })}
          required aria-invalid={messages.length ? 'true' : undefined}
          aria-describedby={messages.length ? 'slotId-error' : undefined}>
          <option value="">
            {slotsLoading
              ? 'Loading active slots...'
              : availableSlots.length === 0
                ? '-- No active slots available (use manual entry) --'
                : '-- Select an active slot --'}
          </option>
          {existing && !availableSlots.some(s => s.slotId === existing.slotId) && (
            <option value={existing.slotId}>Current: {existing.slotId.slice(0, 8)}… — Station: {shortReference(existing.stationId)}</option>
          )}
          {availableSlots.map(s => (
            <option key={s.slotId} value={s.slotId}>
              {s.slotId.slice(0, 8)}… — Station: {shortReference(s.stationId)} ({formatUtc(s.startAtUtc)} | {s.availableSlots} avail)
            </option>
          ))}
        </select>
      ) : (
        <input id="slotId" name="slotId" className={`form-control${messages.length ? ' is-invalid' : ''}`}
          value={values.slotId} onChange={event => setValues({ ...values, slotId: event.target.value })}
          required aria-invalid={messages.length ? 'true' : undefined}
          aria-describedby={messages.length ? 'slotId-error' : undefined}
          placeholder="e.g. 11111111-1111-1111-1111-111111111111"
          autoComplete="off" spellCheck={false} />
      )}
      {!!messages.length && <div id="slotId-error" className="invalid-feedback">{messages.join(' ')}</div>}
      <p className="small text-secondary mt-1 mb-0">Select an active slot or enter a known slot reference. Availability is confirmed when you save.</p>
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
        {slotField()}
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
