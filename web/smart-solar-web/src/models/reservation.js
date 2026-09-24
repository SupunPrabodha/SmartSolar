/**
 * Reservation contracts mirror the Member 3 API. UTC timestamps stay as ISO strings;
 * authoritative timing, ownership and capacity decisions remain on the server.
 *
 * @typedef {'Pending'|'Approved'|'Rejected'|'Cancelled'|'Completed'} ReservationStatus
 *
 * @typedef {Object} ReservationRequest
 * @property {string} slotId Existing slot GUID string, preserved exactly.
 * @property {number} energyAmountKwh Positive kWh amount.
 *
 * @typedef {Object} Reservation
 * @property {string} reservationId
 * @property {string} prosumerNic
 * @property {string} stationId
 * @property {string} slotId
 * @property {number} energyAmountKwh
 * @property {string} scheduledStartAtUtc Accepted start, not a client-calculated slot time.
 * @property {string} scheduledEndAtUtc
 * @property {ReservationStatus} status
 * @property {string} createdAtUtc
 * @property {string} updatedAtUtc
 *
 * @typedef {Object} ReservationFilters
 * @property {ReservationStatus} [status]
 * @property {string} [prosumerNic]
 * @property {string} [stationId]
 *
 * @typedef {Object} ReservationCallOptions
 * @property {AbortSignal} [signal]
 */
export {};
