// UTC input must never depend on the browser timezone.
export function toUtcInput(value) { return new Date(value).toISOString().slice(0, -1); }
export function fromUtcInput(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(value)) throw new Error('Enter a valid UTC date and time.');
  const date = new Date(value + 'Z');
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 16) !== value.slice(0, 16)) throw new Error('Enter a valid UTC date and time.');
  return date.toISOString();
}
export function stationPayload(form) {
  return { name: form.name.trim(), address: form.address.trim(), latitude: Number(form.latitude), longitude: Number(form.longitude),
    capacityKwh: Number(form.capacityKwh), totalBatterySlots: Number(form.totalBatterySlots),
    operatingSchedule: form.operatingSchedule.map(day => ({ ...day, opensAt: day.isClosed ? null : day.opensAt, closesAt: day.isClosed ? null : day.closesAt })),
    expectedUpdatedAtUtc: form.updatedAtUtc };
}
