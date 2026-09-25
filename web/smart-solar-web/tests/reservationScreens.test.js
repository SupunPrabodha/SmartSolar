import assert from 'node:assert/strict';
import { after, afterEach, beforeEach, test } from 'node:test';
import React from 'react';
import { create, act } from 'react-test-renderer';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { createServer } from 'vite';

const server = await createServer({
  logLevel: 'silent',
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('https://api.example.invalid/api/v1') },
  server: { middlewareMode: true, watch: null, hmr: false }
});
const { default: List } = await server.ssrLoadModule('/src/pages/reservations/ReservationListPage.jsx');
const { default: Form } = await server.ssrLoadModule('/src/pages/reservations/ReservationFormPage.jsx');
const { default: Details } = await server.ssrLoadModule('/src/pages/reservations/ReservationDetailsPage.jsx');
const { ReservationLayout } = await server.ssrLoadModule('/src/pages/reservations/ReservationComponents.jsx');
const { default: HomePage } = await server.ssrLoadModule('/src/pages/HomePage.jsx');
const { AuthProvider } = await server.ssrLoadModule('/src/auth/AuthContext.jsx');
after(() => server.close());

const originalFetch = globalThis.fetch;
const base = '/operator/reservations';
const row = {
  reservationId: 'reservation-1', prosumerNic: '200012345678', stationId: '22222222222222222222222222222222',
  slotId: '11111111111111111111111111111111', energyAmountKwh: 1.5, status: 'Pending',
  scheduledStartAtUtc: '2099-01-03T00:00:00Z', scheduledEndAtUtc: '2099-01-03T01:00:00Z',
  createdAtUtc: '2099-01-01T00:00:00Z', updatedAtUtc: '2099-01-01T00:00:00Z'
};
let view;
let calls;
function response(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': status >= 400 ? 'application/problem+json' : 'application/json' } });
}
function text(node) {
  if (typeof node === 'string') return node;
  return (node?.children ?? []).map(text).join('');
}
function button(name) {
  return view.root.findAllByType('button').find(node => text(node) === name);
}
async function click(name) {
  const target = button(name);
  assert.ok(target, 'Missing button: ' + name);
  await act(async () => { target.props.onClick(); });
}
async function settle() {
  await act(async () => { await new Promise(setImmediate); });
}
async function mount(path) {
  await act(async () => {
    view = create(React.createElement(MemoryRouter, { initialEntries: [path] },
      React.createElement(Routes, null,
        React.createElement(Route, { path: base, element: React.createElement(List) }),
        React.createElement(Route, { path: base + '/new', element: React.createElement(Form, { creating: true }) }),
        React.createElement(Route, { path: base + '/:reservationId/edit', element: React.createElement(Form) }),
        React.createElement(Route, { path: base + '/:reservationId', element: React.createElement(Details) })
      )), { createNodeMock: element => element.type === 'dialog'
        ? { showModal() {}, close() {} } : { focus() {}, querySelector() { return { focus() {} }; } } });
  });
  await settle();
}
async function fill(name, value) {
  await act(async () => { view.root.findByProps({ id: name }).props.onChange({ target: { value } }); });
}
async function review() {
  await act(async () => { view.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
}

beforeEach(() => {
  calls = [];
  const store = new Map([
    ['accessToken', 'test-session'],
    ['currentUser', JSON.stringify({ fullName: 'Operator One', role: 'GridOperator', status: 'Active' })],
    ['expiresAtUtc', new Date(Date.now() + 3600000).toISOString()]
  ]);
  globalThis.sessionStorage = {
    getItem: key => store.get(key) ?? null,
    setItem: (key, val) => store.set(key, String(val)),
    removeItem: key => store.delete(key)
  };
  globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    if (url.includes('/reservations/slots')) {
      return response([{
        slotId: row.slotId,
        stationId: row.stationId,
        startAtUtc: row.startTimeUtc,
        endAtUtc: row.endTimeUtc,
        availableSlots: 4,
        totalSlots: 5
      }]);
    }
    return response(url.endsWith('/reservations') ? [row] : row);
  };
});
afterEach(async () => {
  if (view) await act(async () => { view.unmount(); });
  view = null;
  globalThis.fetch = originalFetch;
  delete globalThis.window;
  delete globalThis.sessionStorage;
});

test('list loads real rows and applies only the operational filters', async () => {
  await mount(base);
  assert.match(text(view.root), /reservation-1/);
  assert.match(text(view.root), /1.5 kWh/);
  // Pending row shows 'Review' action button
  const actionLink = view.root.findAllByType('a').find(a => a.props.href === '/operator/reservations/reservation-1');
  assert.ok(actionLink, 'Expected action link for reservation-1');
  assert.equal(text(actionLink), 'Review');

  await fill('filter-status', 'Pending');
  await fill('filter-nic', '200012345678');
  await review();
  await settle();
  const query = new URL(calls.at(-1).url).searchParams;
  assert.equal(query.get('status'), 'Pending');
  assert.equal(query.get('prosumerNic'), '200012345678');
});

test('list has loading, empty, error and read-retry states', async () => {
  let finish;
  globalThis.fetch = () => new Promise(resolve => { finish = resolve; });
  await mount(base);
  assert.match(text(view.root), /Loading reservations/);
  await act(async () => { finish(response([])); });
  assert.match(text(view.root), /No reservations yet/);
  globalThis.fetch = async () => response({ detail: 'Listing unavailable.' }, 503);
  await click('Refresh');
  await settle();
  assert.match(text(view.root), /Listing unavailable/);
  globalThis.fetch = async () => response([row]);
  await click('Try again');
  await settle();
  assert.match(text(view.root), /reservation-1/);
});

test('obsolete filtered read cannot replace newer results', async () => {
  let oldRead;
  globalThis.fetch = () => new Promise(resolve => { oldRead = resolve; });
  await mount(base);
  globalThis.fetch = async () => response([]);
  await fill('filter-status', 'Rejected');
  await review();
  await settle();
  await act(async () => { oldRead(response([row])); });
  assert.match(text(view.root), /No matching reservations/);
  assert.doesNotMatch(text(view.root), /reservation-1/);
});

test('assisted create validates, reviews, prevents duplicate submission and shows server summary', async () => {
  await mount(base + '/new');
  await review();
  assert.match(text(view.root), /Enter a valid Prosumer NIC/);
  assert.equal(calls.filter(c => c.options?.method === 'POST').length, 0);
  await fill('prosumerNic', '200012345678');
  await fill('slotId', row.slotId);
  await fill('energyAmountKwh', '1.5');
  await review();
  assert.match(text(view.root), /Review your request/);
  assert.equal(calls.filter(c => c.options?.method === 'POST').length, 0);
  let finish;
  globalThis.fetch = (url, options) => { calls.push({ url, options }); return new Promise(resolve => { finish = resolve; }); };
  const confirm = button('Confirm reservation');
  await act(async () => { confirm.props.onClick(); confirm.props.onClick(); });
  const postCalls = calls.filter(c => c.options?.method === 'POST');
  assert.equal(postCalls.length, 1);
  assert.ok(postCalls[0].url.endsWith('/reservations/prosumers/200012345678'));
  assert.deepEqual(JSON.parse(postCalls[0].options.body), { slotId: row.slotId, energyAmountKwh: 1.5 });
  await act(async () => { finish(response(row, 201)); });
  assert.match(text(view.root), /Reservation created/);
  assert.match(text(view.root), /reservation-1/);
  assert.match(text(view.root), /2099/);
});

test('assisted create allows selecting slot from active slots dropdown and toggling manual input', async () => {
  await mount(base + '/new');
  // Initially rendered as select with available slots
  const select = view.root.findByProps({ id: 'slotId' });
  assert.equal(select.type, 'select');
  await fill('slotId', row.slotId);
  assert.equal(select.props.value, row.slotId);
  // Toggle to manual typing mode
  await click('Type custom Slot ID');
  const input = view.root.findByProps({ id: 'slotId' });
  assert.equal(input.type, 'input');
  assert.equal(input.props.value, row.slotId);
  // Toggle back to dropdown
  await click('Select from active slots list');
  const selectAgain = view.root.findByProps({ id: 'slotId' });
  assert.equal(selectAgain.type, 'select');
});

test('edit prefills existing data and displays the server reapproval result', async () => {
  await mount(base + '/reservation-1/edit');
  assert.equal(view.root.findByProps({ id: 'prosumerNic' }).props.readOnly, true);
  assert.equal(view.root.findByProps({ id: 'energyAmountKwh' }).props.value, '1.5');
  await fill('energyAmountKwh', '2');
  await review();
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return response({ ...row, energyAmountKwh: 2 }); };
  await click('Confirm changes');
  assert.equal(calls.at(-1).options.method, 'PUT');
  assert.match(text(view.root), /Reservation updated/);
  assert.match(text(view.root), /2 kWh/);
});

test('create server validation is preserved on returning to the form', async () => {
  await mount(base + '/new');
  await fill('prosumerNic', '200012345678');
  await fill('slotId', row.slotId);
  await fill('energyAmountKwh', '1');
  await review();
  globalThis.fetch = async () => response({ detail: 'Invalid amount.', errors: { EnergyAmountKwh: ['Amount rejected.'] } }, 400);
  await click('Confirm reservation');
  await click('Back to form');
  assert.equal(view.root.findByProps({ id: 'energyAmountKwh' }).props['aria-invalid'], 'true');
  assert.match(text(view.root), /Amount rejected/);
});

test('details cancellation requires confirmation and shows the cancelled summary', async () => {
  await mount(base + '/reservation-1');
  await click('Cancel reservation');
  assert.equal(calls.length, 1);
  await click('Keep reservation');
  assert.equal(calls.length, 1);
  await click('Cancel reservation');
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return response({ ...row, status: 'Cancelled' }); };
  await click('Confirm cancellation');
  assert.equal(calls.at(-1).options.method, 'PATCH');
  assert.match(text(view.root), /Reservation cancelled/);
  assert.equal(button('Cancel reservation').props.disabled, true);
});

test('terminal reservations disable modification and cancellation in details and direct edit', async () => {
  globalThis.fetch = async () => response({ ...row, status: 'Completed' });
  await mount(base + '/reservation-1');
  assert.equal(button('Cancel reservation').props.disabled, true);
  assert.equal(button('Modify reservation').props.disabled, true);
  await act(async () => { view.unmount(); });
  view = null;
  await mount(base + '/reservation-1/edit');
  assert.equal(view.root.findByType('fieldset').props.disabled, true);
});

test('cancellation failure keeps a recoverable confirmation and never claims success', async () => {
  await mount(base + '/reservation-1');
  await click('Cancel reservation');
  globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };
  await click('Confirm cancellation');
  assert.match(text(view.root), /may have reached the server/);
  assert.doesNotMatch(text(view.root), /Reservation cancelled/);
  assert.equal(button('Keep reservation').props.disabled, false);
});

test('detail 404 shows error and allows read retry', async () => {
  globalThis.fetch = async () => response({ detail: 'Reservation not found.' }, 404);
  await mount(base + '/missing');
  assert.match(text(view.root), /Reservation not found/);
  assert.ok(button('Try again'));
});

test('station filter applies and clear filters resets both inputs and results', async () => {
  await mount(base);
  await fill('filter-station', '22222222222222222222222222222222');
  await review();
  await settle();
  const query = new URL(calls.at(-1).url).searchParams;
  assert.equal(query.get('stationId'), '22222222222222222222222222222222');
  await click('Clear filters');
  await settle();
  assert.equal(view.root.findByProps({ id: 'filter-station' }).props.value, '');
  assert.equal(view.root.findByProps({ id: 'filter-nic' }).props.value, '');
  assert.equal(view.root.findByProps({ id: 'filter-status' }).props.value, '');
});

test('create handles 409 conflict ProblemDetails and displays error trace', async () => {
  await mount(base + '/new');
  await fill('prosumerNic', '200012345678');
  await fill('slotId', row.slotId);
  await fill('energyAmountKwh', '1.5');
  await review();
  globalThis.fetch = async () => response({
    title: 'Conflict', status: 409, detail: 'The requested slot has no available capacity.', traceId: 'trace-conflict-123'
  }, 409);
  await click('Confirm reservation');
  assert.match(text(view.root), /The requested slot has no available capacity/);
  assert.match(text(view.root), /trace-conflict-123/);
  assert.doesNotMatch(text(view.root), /Reservation created/);
  assert.equal(button('Confirm reservation').props.disabled, false);
});

test('create displays 401 session expiration error notice', async () => {
  await mount(base + '/new');
  await fill('prosumerNic', '200012345678');
  await fill('slotId', row.slotId);
  await fill('energyAmountKwh', '1.5');
  await review();
  globalThis.fetch = async () => response({ title: 'Unauthorized', status: 401 }, 401);
  await click('Confirm reservation');
  assert.match(text(view.root), /Your session has expired/);
});

test('update handles 409 conflict and keeps editable form available', async () => {
  await mount(base + '/reservation-1/edit');
  await fill('energyAmountKwh', '3.0');
  await review();
  globalThis.fetch = async () => response({
    title: 'Conflict', status: 409, detail: 'Modifications require at least 12 hours notice.'
  }, 409);
  await click('Confirm changes');
  assert.match(text(view.root), /Modifications require at least 12 hours notice/);
  await click('Back to form');
  assert.equal(view.root.findByProps({ id: 'energyAmountKwh' }).props.value, '3.0');
});

test('cancellation 409 conflict stays in dialog and displays server problem', async () => {
  await mount(base + '/reservation-1');
  await click('Cancel reservation');
  globalThis.fetch = async () => response({
    title: 'Conflict', status: 409, detail: 'Reservation cannot be cancelled within 12 hours of start.'
  }, 409);
  await click('Confirm cancellation');
  assert.match(text(view.root), /Reservation cannot be cancelled within 12 hours/);
  assert.doesNotMatch(text(view.root), /Reservation cancelled/);
  assert.equal(button('Keep reservation').props.disabled, false);
});

test('client validation flags all invalid fields with aria-invalid', async () => {
  await mount(base + '/new');
  await fill('prosumerNic', 'invalid-nic');
  await fill('slotId', 'not-a-guid');
  await fill('energyAmountKwh', '-5');
  await review();
  assert.equal(view.root.findByProps({ id: 'prosumerNic' }).props['aria-invalid'], 'true');
  assert.equal(view.root.findByProps({ id: 'slotId' }).props['aria-invalid'], 'true');
  assert.equal(view.root.findByProps({ id: 'energyAmountKwh' }).props['aria-invalid'], 'true');
  assert.match(text(view.root), /Enter a valid Prosumer NIC/);
  assert.match(text(view.root), /Enter a nonempty slot GUID/);
  assert.match(text(view.root), /Enter an energy amount greater than zero/);
});

test('error notice displays multiple validation error messages from ProblemDetails', async () => {
  await mount(base + '/new');
  await fill('prosumerNic', '200012345678');
  await fill('slotId', row.slotId);
  await fill('energyAmountKwh', '1.0');
  await review();
  globalThis.fetch = async () => response({
    title: 'Validation Failed', status: 400,
    errors: {
      SlotId: ['Slot is inactive.', 'Slot does not exist.'],
      EnergyAmountKwh: ['Exceeds station storage capacity.']
    }
  }, 400);
  await click('Confirm reservation');
  assert.match(text(view.root), /Slot is inactive/);
  assert.match(text(view.root), /Slot does not exist/);
  assert.match(text(view.root), /Exceeds station storage capacity/);
});

test('details approval requires confirmation and shows approved summary', async () => {
  await mount(base + '/reservation-1');
  await click('Approve reservation');
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response({ ...row, status: 'Approved' });
  };
  await click('Confirm approval');
  assert.equal(calls.at(-1).options.method, 'PATCH');
  assert.ok(calls.at(-1).url.endsWith('/approve'));
  assert.match(text(view.root), /Reservation approved successfully/);
});

test('details rejection requires remark and shows rejected summary with released capacity note', async () => {
  await mount(base + '/reservation-1');
  await click('Reject reservation');
  // Attempt submit without remark
  await click('Confirm rejection');
  assert.match(text(view.root), /Please provide a reason/);

  // Fill remark and submit
  await fill('reject-remark', 'Grid maintenance');
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return response({ ...row, status: 'Rejected', rejectionRemark: 'Grid maintenance' });
  };
  await click('Confirm rejection');
  assert.equal(calls.at(-1).options.method, 'PATCH');
  assert.ok(calls.at(-1).url.endsWith('/reject'));
  assert.match(text(view.root), /Reservation rejected/);
});

test('home page sidebar renders Manage Reservations link and removes standalone button', async () => {
  sessionStorage.setItem('accessToken', 'mock-token');
  sessionStorage.setItem('expiresAtUtc', new Date(Date.now() + 3600000).toISOString());
  globalThis.fetch = async () => response({ fullName: 'Operator One', role: 'GridOperator', status: 'Active' });
  await act(async () => {
    view = create(React.createElement(MemoryRouter, { initialEntries: ['/'] },
      React.createElement(AuthProvider, null,
        React.createElement(HomePage)
      )
    ));
  });
  await settle();
  const navLinks = view.root.findAllByProps({ className: 'workspace-nav-item' });
  const reservationsLink = navLinks.find(node => text(node).includes('Manage Reservations'));
  assert.ok(reservationsLink, 'Expected Manage Reservations link in sidebar');
  assert.equal(reservationsLink.props.to, '/operator/reservations');

  // Verify the old standalone manage reservations button is removed from home page body
  const links = view.root.findAllByType('a').filter(a => a.props.href === '/operator/reservations');
  assert.equal(links.length, 1, 'Only the sidebar link should point to /operator/reservations');
});

test('reservation layout keeps aligned sidebar with Manage Reservations', async () => {
  sessionStorage.setItem('accessToken', 'mock-token');
  sessionStorage.setItem('expiresAtUtc', new Date(Date.now() + 3600000).toISOString());
  globalThis.fetch = async () => response({ fullName: 'Operator One', role: 'GridOperator', status: 'Active' });
  await act(async () => {
    view = create(React.createElement(MemoryRouter, { initialEntries: ['/operator/reservations'] },
      React.createElement(AuthProvider, null,
        React.createElement(Routes, null,
          React.createElement(Route, { path: '/operator/reservations', element: React.createElement(ReservationLayout) },
            React.createElement(Route, { index: true, element: React.createElement('div', null, 'Reservation content') })
          )
        )
      )
    ));
  });
  await settle();
  const navLinks = view.root.findAllByProps({ className: 'workspace-nav-item active' });
  const activeLink = navLinks.find(node => text(node).includes('Manage Reservations'));
  assert.ok(activeLink, 'Expected Manage Reservations active in sidebar on reservation screens');
});


