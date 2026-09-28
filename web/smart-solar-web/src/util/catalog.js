// Datetime-local controls represent browser local time; the API remains UTC.
export function toUtcInput(value) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) throw new Error('Enter a valid date and time.');
  const pad = part => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${String(date.getMilliseconds()).padStart(3, '0')}`;
}
export function fromUtcInput(value) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(value)) throw new Error('Enter a valid local date and time.');
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) throw new Error('Enter a valid date and time.');
  const [datePart, timePart] = value.split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  const [hour, minute] = timePart.split(':').map(Number);
  if (date.getFullYear() !== year || date.getMonth() + 1 !== month || date.getDate() !== day || date.getHours() !== hour || date.getMinutes() !== minute)
    throw new Error('Enter a valid date and time.');
  return date.toISOString();
}
export function stationPayload(form) {
  return { name: form.name.trim(), address: form.address.trim(), latitude: Number(form.latitude), longitude: Number(form.longitude),
    capacityKwh: Number(form.capacityKwh), totalBatterySlots: Number(form.totalBatterySlots),
    operatingSchedule: form.operatingSchedule.map(day => ({ ...day, opensAt: day.isClosed ? null : day.opensAt, closesAt: day.isClosed ? null : day.closesAt })),
    expectedUpdatedAtUtc: form.updatedAtUtc };
}
