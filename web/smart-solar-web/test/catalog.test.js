import test from 'node:test';
import assert from 'node:assert/strict';
import { fromUtcInput, toUtcInput, stationPayload } from '../src/util/catalog.js';
test('local datetime input round trip preserves the instant and precision', () => {
  const instant = '2026-09-22T10:15:20.123Z';
  assert.equal(fromUtcInput(toUtcInput(instant)), instant);
  assert.equal(fromUtcInput('2026-09-22T10:15'), new Date('2026-09-22T10:15').toISOString());
});
test('local datetime input rejects missing, ambiguous and invalid input', () => {
  for (const value of ['', '22/09/2026 10:15', '2026-09-22T10:15Z', '2026-99-99T10:15', '2026-02-31T10:15']) assert.throws(() => fromUtcInput(value));
});
test('station payload retains concurrency token and removes closed hours', () => {
  const payload = stationPayload({ name: ' Node ', address: ' Road ', latitude: '6.9', longitude: '79.8',
    capacityKwh: '25.5', totalBatterySlots: '4', updatedAtUtc: 'revision',
    operatingSchedule: [{ day: 1, isClosed: true, opensAt: '08:00', closesAt: '17:00' }] });
  assert.equal(payload.expectedUpdatedAtUtc, 'revision'); assert.equal(payload.latitude, 6.9);
  assert.equal(payload.name, 'Node'); assert.equal(payload.operatingSchedule[0].opensAt, null);
});
