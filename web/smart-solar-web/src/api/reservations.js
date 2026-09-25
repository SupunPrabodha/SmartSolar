import { apiFetch } from './apiClient.js';

/** @typedef {import('../models/reservation.js').Reservation} Reservation */
/** @typedef {import('../models/reservation.js').ReservationRequest} ReservationRequest */
/** @typedef {import('../models/reservation.js').ReservationCallOptions} ReservationCallOptions */

// Centralize path encoding so caller input cannot create extra path/query segments.
function pathSegment(value, name) {
  if (typeof value !== 'string' || !value.trim()) throw new TypeError(`${name} is required.`);
  return encodeURIComponent(value);
}

// Never forward identity, station, schedule, status or QR fields supplied by UI state.
function requestBody(request) {
  return JSON.stringify({ slotId: request.slotId, energyAmountKwh: request.energyAmountKwh });
}

/**
 * @param {string} reservationId
 * @param {ReservationCallOptions} [options]
 * @returns {Promise<Reservation>}
 */
export function getReservation(reservationId, { signal } = {}) {
  return apiFetch(`/reservations/${pathSegment(reservationId, 'Reservation ID')}`, { signal });
}

/**
 * GridOperator assisted creation. Prosumer self-booking belongs to the Android flow.
 * @param {string} prosumerNic
 * @param {ReservationRequest} request
 * @param {ReservationCallOptions} [options]
 * @returns {Promise<Reservation>}
 */
export function createReservation(prosumerNic, request, { signal } = {}) {
  return apiFetch(`/reservations/prosumers/${pathSegment(prosumerNic, 'Prosumer NIC')}`, {
    method: 'POST', body: requestBody(request), signal
  });
}

/**
 * @param {string} reservationId
 * @param {ReservationRequest} request
 * @param {ReservationCallOptions} [options]
 * @returns {Promise<Reservation>}
 */
export function updateReservation(reservationId, request, { signal } = {}) {
  return apiFetch(`/reservations/${pathSegment(reservationId, 'Reservation ID')}`, {
    method: 'PUT', body: requestBody(request), signal
  });
}

/**
 * Returns the cancellation summary; repeat cancellation is a server conflict.
 * @param {string} reservationId
 * @param {ReservationCallOptions} [options]
 * @returns {Promise<Reservation>}
 */
export function cancelReservation(reservationId, { signal } = {}) {
  return apiFetch(`/reservations/${pathSegment(reservationId, 'Reservation ID')}/cancel`, {
    method: 'PATCH', signal
  });
}

/**
 * GridOperator approval for a pending reservation.
 * @param {string} reservationId
 * @param {ReservationCallOptions} [options]
 * @returns {Promise<Reservation>}
 */
export function approveReservation(reservationId, { signal } = {}) {
  return apiFetch(`/reservations/${pathSegment(reservationId, 'Reservation ID')}/approve`, {
    method: 'PATCH', signal
  });
}

/**
 * GridOperator rejection for a pending reservation with a mandatory remark.
 * @param {string} reservationId
 * @param {{ remark: string }} data
 * @param {ReservationCallOptions} [options]
 * @returns {Promise<Reservation>}
 */
export function rejectReservation(reservationId, { remark }, { signal } = {}) {
  if (!remark || typeof remark !== 'string' || !remark.trim()) {
    throw new TypeError('Rejection remark is required.');
  }
  return apiFetch(`/reservations/${pathSegment(reservationId, 'Reservation ID')}/reject`, {
    method: 'PATCH',
    body: JSON.stringify({ remark: remark.trim() }),
    signal
  });
}


/**
 * GridOperator-only management listing; filters are exact matches combined by the API.
 * @param {import('../models/reservation.js').ReservationFilters} [filters]
 * @param {ReservationCallOptions} [options]
 * @returns {Promise<Reservation[]>}
 */
export function listReservations({ status, prosumerNic, stationId } = {}, { signal } = {}) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries({ status, prosumerNic, stationId })) {
    if (value != null && String(value).trim()) query.set(key, String(value).trim());
  }
  return apiFetch('/reservations' + (query.size ? `?${query}` : ''), { signal });
}

/**
 * Lists active booking slots with capacity starting within 7 days.
 * @param {ReservationCallOptions} [options]
 * @returns {Promise<Array<{ slotId: string, stationId: string, startAtUtc: string, endAtUtc: string, availableSlots: number, totalSlots: number }>>}
 */
export function listAvailableSlots({ signal } = {}) {
  return apiFetch('/reservations/slots', { signal });
}

