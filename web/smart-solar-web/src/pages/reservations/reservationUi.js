export const reservationStatuses = ['Pending', 'Approved', 'Rejected', 'Cancelled', 'Completed'];

export function localTimeZone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'local time';
}

export function formatUtc(value) {
  const date = new Date(value);
  return Number.isFinite(date.getTime())
    ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
    : 'Schedule unavailable';
}

export function changeRestriction(reservation, now = Date.now()) {
  if (!['Pending', 'Approved'].includes(reservation.status)) return 'Only Pending or Approved reservations can be modified or cancelled.';
  const start = Date.parse(reservation.scheduledStartAtUtc);
  if (!Number.isFinite(start)) return 'The accepted schedule is unavailable. Refresh the reservation.';
  if (start - now < 12 * 60 * 60 * 1000) return 'The 12-hour modification and cancellation cutoff has passed.';
  return '';
}

export function validateReservationForm({ prosumerNic, slotId, energyAmountKwh }, creating) {
  const errors = {};
  if (creating && !/^([0-9]{9}[VvXx]|[0-9]{12})$/.test(prosumerNic.trim()))
    errors.prosumerNic = 'Enter a valid Prosumer NIC (12 digits, or 9 digits followed by V/X).';
  const id = slotId.trim();
  if (!/^(?:[a-f0-9]{32}|[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})$/i.test(id) || /^0+$/.test(id.replaceAll('-', '')))
    errors.slotId = 'Enter a nonempty slot GUID.';
  if (!energyAmountKwh.trim() || !Number.isFinite(Number(energyAmountKwh)) || Number(energyAmountKwh) <= 0)
    errors.energyAmountKwh = 'Enter an energy amount greater than zero.';
  return errors;
}

export function fieldMessages(error, name) {
  return Object.entries(error?.errors ?? {})
    .filter(([key]) => key.toLowerCase().split('.').at(-1) === name.toLowerCase())
    .flatMap(([, messages]) => messages);
}

export function errorMessage(error, mutation = false) {
  if (error?.status === 401) return 'Your session has expired. Sign in again.';
  if (error?.status === 403) return 'Your account does not have permission for this operation.';
  if (error?.status === 404) return error.message || 'The requested record was not found.';
  if (mutation && (!error?.status || error.status >= 500))
    return 'The result could not be confirmed. Check the reservation list or refresh its details before retrying; the request may have reached the server.';
  return error?.message || 'Unable to load reservations. Check your connection and try again.';
}

export function shortReference(value) {
  if (!value) return 'Unavailable';
  return value.length > 14 ? value.slice(0,8).toUpperCase() + '…' + value.slice(-4).toUpperCase() : value;
}
export function scheduleParts(start, end) {
  const a = new Date(start), b = new Date(end);
  if (!start || !end || !Number.isFinite(a.getTime()) || !Number.isFinite(b.getTime())) return { date:'Schedule unavailable', time:'' };
  const day = value => new Intl.DateTimeFormat(undefined,{day:'numeric',month:'short',year:'numeric'}).format(value);
  const time = value => new Intl.DateTimeFormat(undefined,{hour:'numeric',minute:'2-digit'}).format(value);
  return { date: day(a) === day(b) ? day(a) : day(a) + ' – ' + day(b), time: time(a) + ' – ' + time(b) };
}
