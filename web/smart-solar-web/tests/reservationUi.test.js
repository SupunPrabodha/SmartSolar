import assert from 'node:assert/strict';
import { test } from 'node:test';
import { changeRestriction, errorMessage, fieldMessages, formatUtc, validateReservationForm } from '../src/pages/reservations/reservationUi.js';

const now = Date.parse('2030-01-01T00:00:00Z');
test('client cutoff includes exactly twelve hours and rejects one second below', () => {
  assert.equal(changeRestriction({ status: 'Approved', scheduledStartAtUtc: '2030-01-01T12:00:00Z' }, now), '');
  assert.match(changeRestriction({ status: 'Pending', scheduledStartAtUtc: '2030-01-01T11:59:59Z' }, now), /cutoff/);
});
test('terminal statuses and missing schedules disable changes without inventing dates', () => {
  for (const status of ['Cancelled', 'Rejected', 'Completed', 'unknown'])
    assert.match(changeRestriction({ status, scheduledStartAtUtc: '2030-01-03T00:00:00Z' }, now), /Only Pending/);
  assert.match(changeRestriction({ status: 'Pending', scheduledStartAtUtc: 'invalid' }, now), /unavailable/);
  assert.equal(formatUtc('invalid'), 'Schedule unavailable');
  assert.match(formatUtc('2030-01-01T12:00:00Z'), /12:00 UTC$/);
});
test('form validates NIC, slot GUID and finite positive energy before review', () => {
  const good = { prosumerNic: '200012345678', slotId: '11111111111111111111111111111111', energyAmountKwh: '1.5' };
  assert.deepEqual(validateReservationForm(good, true), {});
  assert.deepEqual(validateReservationForm({ ...good, prosumerNic: '123456789v' }, true), {});
  for (const value of ['', '0', '-1', 'Infinity', 'abc'])
    assert.ok(validateReservationForm({ ...good, energyAmountKwh: value }, true).energyAmountKwh);
  assert.ok(validateReservationForm({ ...good, slotId: '00000000-0000-0000-0000-000000000000' }, true).slotId);
  assert.ok(validateReservationForm({ ...good, prosumerNic: 'other' }, true).prosumerNic);
  assert.equal(validateReservationForm({ ...good, prosumerNic: 'existing-server-identity' }, false).prosumerNic, undefined);
});
test('server field validation maps case-insensitively and ambiguous writes warn against blind retry', () => {
  assert.deepEqual(fieldMessages({ errors: { 'request.EnergyAmountKwh': ['Invalid amount'] } }, 'energyAmountKwh'), ['Invalid amount']);
  assert.match(errorMessage(new TypeError('Failed to fetch'), true), /may have reached/);
  assert.match(errorMessage({ status: 500 }, true), /before retrying/);
  assert.equal(errorMessage({ status: 409, message: 'Slot is full.' }, true), 'Slot is full.');
  assert.match(errorMessage({ status: 401 }), /Sign in/);
});
