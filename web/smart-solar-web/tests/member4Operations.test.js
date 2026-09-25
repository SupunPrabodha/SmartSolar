import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, test } from 'node:test';
import React from 'react';
import { create, act } from 'react-test-renderer';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { createServer } from 'vite';
import { readFile } from 'node:fs/promises';
import { transformWithEsbuild } from 'vite';

// Load shared client and API module
const clientSource = await readFile(new URL('../src/api/apiClient.js', import.meta.url), 'utf8');
const { code: clientCode } = await transformWithEsbuild(clientSource, 'apiClient.js', {
  loader: 'js', format: 'esm',
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('https://api.example.invalid/api/v1') }
});
const clientUrl = 'data:text/javascript;base64,' + Buffer.from(clientCode).toString('base64');
const apiSource = (await readFile(new URL('../src/api/reservations.js', import.meta.url), 'utf8'))
  .replace("'./apiClient.js'", JSON.stringify(clientUrl));
const {
  getCurrentBookings,
  getPendingBookings,
  getBookingHistory,
  searchBookings,
  getReservationDashboardSummary
} = await import('data:text/javascript;base64,' + Buffer.from(apiSource).toString('base64'));

// Set up SSR load module for React components
const server = await createServer({
  logLevel: 'silent',
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('https://api.example.invalid/api/v1') },
  server: { middlewareMode: true, watch: null, hmr: false }
});

const { default: OperationsDashboard } = await server.ssrLoadModule('/src/pages/reservations/OperationsDashboardPage.jsx');
const { default: CurrentBookings } = await server.ssrLoadModule('/src/pages/reservations/CurrentBookingsPage.jsx');
const { default: PendingBookings } = await server.ssrLoadModule('/src/pages/reservations/PendingBookingsPage.jsx');
const { default: BookingHistory } = await server.ssrLoadModule('/src/pages/reservations/BookingHistoryPage.jsx');
const { default: SearchBookings } = await server.ssrLoadModule('/src/pages/reservations/SearchBookingsPage.jsx');

after(() => server.close());

const originalFetch = globalThis.fetch;
let calls;
let expired;

const sampleReservation = {
  reservationId: '11111111-2222-3333-4444-555555555555',
  prosumerNic: '200012345678',
  stationId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  slotId: 'ffffffff-0000-1111-2222-333333333333',
  energyAmountKwh: 4.5,
  scheduledStartAtUtc: '2030-05-10T10:00:00Z',
  scheduledEndAtUtc: '2030-05-10T11:00:00Z',
  status: 'Pending',
  createdAtUtc: '2030-05-01T00:00:00Z',
  updatedAtUtc: '2030-05-01T00:00:00Z'
};

const samplePage = {
  items: [sampleReservation],
  page: 1,
  pageSize: 20,
  hasMore: false
};

const sampleDashboard = {
  pendingReservations: 7,
  approvedFutureReservations: 12,
  generatedAtUtc: '2030-05-10T09:30:00Z'
};

function response(body, status = 200, type = 'application/json') {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': status >= 400 ? 'application/problem+json' : type }
  });
}

function text(node) {
  if (typeof node === 'string') return node;
  return (node?.children ?? []).map(text).join('');
}

let view;
async function settle() {
  await act(async () => { await new Promise(setImmediate); });
}

async function mountComponent(element) {
  await act(async () => {
    view = create(
      React.createElement(MemoryRouter, null, element),
      { createNodeMock: () => ({ focus() {}, querySelector() { return { focus() {} }; } }) }
    );
  });
  await settle();
}

beforeEach(() => {
  const storage = new Map([['accessToken', 'test-session']]);
  globalThis.sessionStorage = {
    getItem: key => storage.get(key) ?? null,
    removeItem: key => storage.delete(key)
  };
  globalThis.window = new EventTarget();
  expired = 0;
  window.addEventListener('session-expired', () => {
    expired++;
    sessionStorage.removeItem('accessToken');
  });
  calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response(samplePage);
  };
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  delete globalThis.window;
  delete globalThis.sessionStorage;
});

// API layer tests
test('getReservationDashboardSummary fetches /reservations/dashboard-summary', async () => {
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response(sampleDashboard);
  };
  const result = await getReservationDashboardSummary();
  assert.deepEqual(result, sampleDashboard);
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations/dashboard-summary');
  assert.equal(calls[0].options.headers.get('Authorization'), 'Bearer test-session');
});

test('getCurrentBookings builds query and calls /reservations/current', async () => {
  await getCurrentBookings({ page: 2, pageSize: 10 });
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations/current?page=2&pageSize=10');
});

test('getPendingBookings calls /reservations/pending', async () => {
  await getPendingBookings({ prosumerNic: '200012345678' });
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations/pending?prosumerNic=200012345678');
});

test('getBookingHistory calls /reservations/history', async () => {
  await getBookingHistory({ status: 'Cancelled' });
  assert.equal(calls[0].url, 'https://api.example.invalid/api/v1/reservations/history?status=Cancelled');
});

test('searchBookings supports all backend filters and omits undefined/blank', async () => {
  await searchBookings({
    reservationId: '11111111-2222-3333-4444-555555555555',
    status: 'Approved',
    stationId: '',
    prosumerNic: null
  });
  const url = new URL(calls[0].url);
  assert.equal(url.pathname, '/api/v1/reservations/search');
  assert.equal(url.searchParams.get('reservationId'), '11111111-2222-3333-4444-555555555555');
  assert.equal(url.searchParams.get('status'), 'Approved');
  assert.equal(url.searchParams.has('stationId'), false);
  assert.equal(url.searchParams.has('prosumerNic'), false);
});

// UI Component tests
test('dashboard renders live API counts', async () => {
  globalThis.fetch = async () => response(sampleDashboard);
  await mountComponent(React.createElement(OperationsDashboard));
  const rendered = text(view.root);
  assert.ok(rendered.includes('7'), 'Must show pending count 7');
  assert.ok(rendered.includes('12'), 'Must show approved future count 12');
  assert.ok(rendered.includes('Current Bookings'));
  assert.ok(rendered.includes('Pending Queue'));
  assert.ok(rendered.includes('Booking History'));
  assert.ok(rendered.includes('Search & Filter'));
});

test('zero dashboard counts render as 0', async () => {
  globalThis.fetch = async () => response({ pendingReservations: 0, approvedFutureReservations: 0, generatedAtUtc: '2030-01-01T00:00:00Z' });
  await mountComponent(React.createElement(OperationsDashboard));
  const rendered = text(view.root);
  assert.ok(rendered.includes('0'), 'Must render 0 count');
});

test('dashboard API failure does not show fake values and shows error message', async () => {
  globalThis.fetch = async () => response({ status: 500, detail: 'Dashboard service unavailable' }, 500);
  await mountComponent(React.createElement(OperationsDashboard));
  const rendered = text(view.root);
  assert.ok(rendered.includes('Dashboard service unavailable'));
  assert.ok(!rendered.includes('7'), 'Must not display fake or old values on failure');
});

test('current bookings renders returned records', async () => {
  globalThis.fetch = async () => response(samplePage);
  await mountComponent(React.createElement(CurrentBookings));
  const rendered = text(view.root);
  assert.ok(rendered.includes('11111111-2222-3333-4444-555555555555'));
  assert.ok(rendered.includes('200012345678'));
  assert.ok(rendered.includes('4.5 kWh'));
});

test('pending list handles empty results', async () => {
  globalThis.fetch = async () => response({ items: [], page: 1, pageSize: 20, hasMore: false });
  await mountComponent(React.createElement(PendingBookings));
  const rendered = text(view.root);
  assert.ok(rendered.includes('No pending bookings'));
});

test('history renders returned records', async () => {
  globalThis.fetch = async () => response({
    items: [{ ...sampleReservation, status: 'Completed' }],
    page: 1,
    pageSize: 20,
    hasMore: false
  });
  await mountComponent(React.createElement(BookingHistory));
  const rendered = text(view.root);
  assert.ok(rendered.includes('Completed'));
  assert.ok(rendered.includes('11111111-2222-3333-4444-555555555555'));
});

test('search sends the expected query/filter values', async () => {
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response(samplePage);
  };
  await mountComponent(React.createElement(SearchBookings));

  // Change input
  await act(async () => {
    view.root.findByProps({ id: 'search-reservation-id' }).props.onChange({
      target: { value: '11111111-2222-3333-4444-555555555555' }
    });
  });

  // Submit form
  await act(async () => {
    view.root.findByProps({ 'aria-label': 'Search reservation filters' }).props.onSubmit({
      preventDefault() {}
    });
  });
  await settle();

  const lastCall = calls[calls.length - 1];
  const url = new URL(lastCall.url);
  assert.equal(url.searchParams.get('reservationId'), '11111111-2222-3333-4444-555555555555');
});

test('401 follows existing session-expiry behavior on query failure', async () => {
  globalThis.fetch = async () => response({ status: 401, detail: 'Unauthorized' }, 401);
  await mountComponent(React.createElement(CurrentBookings));
  assert.equal(expired, 1);
  assert.equal(sessionStorage.getItem('accessToken'), null);
});
