import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { transformWithEsbuild } from 'vite';

// Execute the real helpers and shared client; replace only Vite's public API URL.
const clientSource = await readFile(new URL('../src/api/apiClient.js', import.meta.url), 'utf8');
const { code: clientCode } = await transformWithEsbuild(clientSource, 'apiClient.js', {
  loader: 'js', format: 'esm',
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('https://api.example.invalid/api/v1') }
});
const clientUrl = 'data:text/javascript;base64,' + Buffer.from(clientCode).toString('base64');
const apiSource = (await readFile(new URL('../src/api/reservations.js', import.meta.url), 'utf8'))
  .replace("'./apiClient.js'", JSON.stringify(clientUrl));
const { listReservations, getReservation, createReservation, updateReservation, cancelReservation } =
  await import('data:text/javascript;base64,' + Buffer.from(apiSource).toString('base64'));

const originalFetch = globalThis.fetch;
const summary = {
  reservationId: 'reservation-id', prosumerNic: '200012345678', stationId: 'station-id',
  slotId: '11111111111111111111111111111111', energyAmountKwh: 1.5,
  scheduledStartAtUtc: '2030-01-02T00:00:00Z', scheduledEndAtUtc: '2030-01-02T01:00:00Z',
  status: 'Pending', createdAtUtc: '2030-01-01T00:00:00Z', updatedAtUtc: '2030-01-01T00:00:00Z'
};
let calls;
let expired;
function response(body, status = 200, type = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': type } });
}

beforeEach(() => {
  const storage = new Map([['accessToken', 'test-session']]);
  globalThis.sessionStorage = {
    getItem: key => storage.get(key) ?? null,
    removeItem: key => storage.delete(key)
  };
  globalThis.window = new EventTarget();
  expired = 0;
  window.addEventListener('session-expired', () => { expired++; sessionStorage.removeItem('accessToken'); });
  calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response(summary);
  };
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  delete globalThis.window;
  delete globalThis.sessionStorage;
});

test('get uses the authenticated shared client and preserves the server summary', async () => {
  assert.deepEqual(await getReservation('reservation-id'), summary);
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations/reservation-id');
  assert.equal(calls[0].options.headers.get('Authorization'), 'Bearer test-session');
  assert.equal(calls[0].options.body, undefined);
});

test('operator creation uses the assisted route and only reviewed request fields', async () => {
  assert.deepEqual(await createReservation('200012345678', {
    slotId: summary.slotId, energyAmountKwh: 1.5,
    prosumerNic: 'other', stationId: 'other', status: 'Completed', qrToken: 'injected',
    scheduledStartAtUtc: '2099-01-01T00:00:00Z'
  }), summary);
  const { url, options } = calls[0];
  assert.equal(url, 'https://api.example.invalid/api/v1/reservations/prosumers/200012345678');
  assert.equal(options.method, 'POST');
  assert.equal(options.headers.get('Content-Type'), 'application/json');
  assert.deepEqual(JSON.parse(options.body), { slotId: summary.slotId, energyAmountKwh: 1.5 });
});

test('update sends PUT with only slot and energy and returns the server summary', async () => {
  assert.deepEqual(await updateReservation('reservation-id', {
    slotId: summary.slotId, energyAmountKwh: 2, status: 'Approved', qrToken: 'injected'
  }), summary);
  assert.equal(calls[0].options.method, 'PUT');
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations/reservation-id');
  assert.deepEqual(JSON.parse(calls[0].options.body), { slotId: summary.slotId, energyAmountKwh: 2 });
});

test('cancel sends a bodyless PATCH and returns the cancellation summary', async () => {
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response({ ...summary, status: 'Cancelled' });
  };
  assert.equal((await cancelReservation('reservation-id')).status, 'Cancelled');
  assert.equal(calls[0].options.method, 'PATCH');
  assert.equal(calls[0].options.body, undefined);
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations/reservation-id/cancel');
});

test('identifiers are encoded as single path segments', async () => {
  await getReservation('id/with?query#fragment');
  await createReservation('nic/with?query', { slotId: summary.slotId, energyAmountKwh: 1 });
  assert.ok(calls[0].url.endsWith('/id%2Fwith%3Fquery%23fragment'));
  assert.ok(calls[1].url.endsWith('/prosumers/nic%2Fwith%3Fquery'));
});

test('missing identifiers fail before sending a request', () => {
  assert.throws(() => getReservation(' '), /Reservation ID is required/);
  assert.throws(() => createReservation('', {}), /Prosumer NIC is required/);
  assert.throws(() => updateReservation(undefined, {}), /Reservation ID is required/);
  assert.throws(() => cancelReservation(null), /Reservation ID is required/);
  assert.equal(calls.length, 0);
});

test('list calls the protected collection endpoint and preserves array responses', async () => {
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response([summary]);
  };
  assert.deepEqual(await listReservations(), [summary]);
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations');
  assert.equal(calls[0].options.headers.get('Authorization'), 'Bearer test-session');
});

test('list encodes only supported filters and forwards the AbortSignal', async () => {
  const { signal } = new AbortController();
  await listReservations({ status: 'Pending', prosumerNic: ' P1&status=Completed ',
    stationId: summary.stationId, dashboard: true }, { signal });
  const url = new URL(calls[0].url);
  assert.equal(url.searchParams.get('status'), 'Pending');
  assert.equal(url.searchParams.get('prosumerNic'), 'P1&status=Completed');
  assert.equal(url.searchParams.get('stationId'), summary.stationId);
  assert.equal(url.searchParams.has('dashboard'), false);
  assert.equal([...url.searchParams].length, 3);
  assert.equal(calls[0].options.signal, signal);
});

test('list omits blank filters and preserves a real empty result', async () => {
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response([]);
  };
  assert.deepEqual(await listReservations({ status: undefined, prosumerNic: ' ', stationId: null }), []);
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations');
});

test('list forwards server authorization and validation problems without returning an empty list', async () => {
  for (const status of [400, 403, 409]) {
    globalThis.fetch = async () => response({ status, detail: 'Listing denied.' }, status, 'application/problem+json');
    await assert.rejects(listReservations(), error => error.status === status && error.message === 'Listing denied.');
  }
});

test('all supported calls forward the AbortSignal', async () => {
  const { signal } = new AbortController();
  await getReservation('id', { signal });
  await createReservation('nic', { slotId: summary.slotId, energyAmountKwh: 1 }, { signal });
  await updateReservation('id', { slotId: summary.slotId, energyAmountKwh: 1 }, { signal });
  await cancelReservation('id', { signal });
  for (const call of calls) assert.equal(call.options.signal, signal);
});

test('409 preserves server detail and trace ID without expiring the session', async () => {
  globalThis.fetch = async () => response({
    status: 409, title: 'Conflict', detail: 'Reservation changes require at least twelve hours of notice.',
    traceId: 'test-trace'
  }, 409, 'application/problem+json');
  await assert.rejects(cancelReservation('id'), error => {
    assert.equal(error.status, 409);
    assert.equal(error.message, 'Reservation changes require at least twelve hours of notice.');
    assert.equal(error.traceId, 'test-trace');
    return true;
  });
  assert.equal(expired, 0);
});

test('400 retains field validation messages for future forms', async () => {
  const errors = { EnergyAmountKwh: ['EnergyAmountKwh must be greater than zero.'] };
  globalThis.fetch = async () => response({
    title: 'One or more validation errors occurred.', status: 400, errors
  }, 400, 'application/problem+json');
  await assert.rejects(createReservation('nic', { slotId: summary.slotId, energyAmountKwh: 0 }), error => {
    assert.equal(error.status, 400);
    assert.deepEqual(error.errors, errors);
    assert.equal(error.message, errors.EnergyAmountKwh[0]);
    return true;
  });
});

test('403 falls back to ProblemDetails title and does not clear the session', async () => {
  globalThis.fetch = async () => response({ title: 'Forbidden', status: 403 }, 403, 'application/problem+json');
  await assert.rejects(getReservation('id'), error => error.status === 403 && error.message === 'Forbidden');
  assert.equal(expired, 0);
});

test('HTTP status stays authoritative when an error body has malformed fields', async () => {
  globalThis.fetch = async () => response({
    status: 200, detail: {}, title: ['unsafe'], errors: { slotId: [42], other: 'unsafe' }
  }, 502, 'application/problem+json');
  await assert.rejects(getReservation('id'), error => {
    assert.equal(error.status, 502);
    assert.equal(error.message, 'Request failed with status 502.');
    assert.deepEqual(error.errors, {});
    return true;
  });
});

test('malformed 401 still clears the current session through the existing client', async () => {
  globalThis.fetch = async () => new Response('{bad', {
    status: 401, headers: { 'content-type': 'application/problem+json' }
  });
  await assert.rejects(getReservation('id'), error => error.status === 401);
  assert.equal(expired, 1);
  assert.equal(sessionStorage.getItem('accessToken'), null);
});

test('network and cancellation failures propagate without invented HTTP status', async () => {
  for (const failure of [new TypeError('Failed to fetch'), new DOMException('Cancelled', 'AbortError')]) {
    globalThis.fetch = async () => { throw failure; };
    await assert.rejects(getReservation('id'), error => error === failure && error.status === undefined);
  }
  assert.equal(expired, 0);
});

test('plain-text server failures receive a safe status-based message', async () => {
  globalThis.fetch = async () => new Response('internal proxy text', { status: 503 });
  await assert.rejects(getReservation('id'), error =>
    error.status === 503 && error.message === 'Request failed with status 503.');
});
