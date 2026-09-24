export const reservationStatuses = ['Pending', 'Approved', 'Rejected', 'Cancelled', 'Completed'];

export function formatUtc(value) {
  const date = new Date(value);
  return Number.isFinite(date.getTime())
    ? new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(date) + ' UTC'
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
