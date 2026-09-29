import assert from 'node:assert/strict';
import { after, afterEach, test } from 'node:test';
import React from 'react';
import { create, act } from 'react-test-renderer';
import { createServer } from 'vite';

const server = await createServer({
  logLevel: 'silent',
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('https://api.example.invalid/api/v1/') },
  server: { middlewareMode: true, watch: null, hmr: false }
});
const { default: VerifyEmailPage } = await server.ssrLoadModule('/src/pages/VerifyEmailPage.jsx');
const originalWindow = globalThis.window;
const originalFetch = globalThis.fetch;
let view;
afterEach(() => {
  if (view) act(() => view.unmount());
  view = null;
  globalThis.window = originalWindow;
  globalThis.fetch = originalFetch;
});
after(() => server.close());

function openLink(hash) {
  const replaced = [];
  globalThis.window = { location: { hash, pathname: '/verify-email' }, history: { replaceState: (...args) => replaced.push(args) } };
  act(() => { view = create(React.createElement(VerifyEmailPage)); });
  return replaced;
}

test('opening verification links does not activate; explicit confirmation sends anonymous POST', async () => {
  const token = 'a'.repeat(64);
  const calls = [];
  globalThis.fetch = async (...args) => { calls.push(args); return new Response(null, { status: 204 }); };
  const replaced = openLink(`#nic=200012345678&token=${token}`);
  assert.equal(calls.length, 0);
  assert.equal(replaced[0][2], '/verify-email');
  await act(async () => { await view.root.findByType('button').props.onClick(); });
  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], 'https://api.example.invalid/api/v1/auth/verify-email');
  assert.equal(calls[0][1].method, 'POST');
  assert.equal(calls[0][1].credentials, 'omit');
  assert.equal(calls[0][1].headers.Authorization, undefined);
  assert.deepEqual(JSON.parse(calls[0][1].body), { nic: '200012345678', token });
  assert.match(JSON.stringify(view.toJSON()), /account is active/);
});

test('missing token disables confirmation and server errors keep retry available', async () => {
  openLink('');
  assert.equal(view.root.findByType('button').props.disabled, true);
  act(() => view.unmount());
  globalThis.fetch = async () => new Response(JSON.stringify({ detail: 'Link expired. Ask Backoffice to resend.' }), { status: 400 });
  openLink(`#nic=200012345678&token=${'a'.repeat(64)}`);
  await act(async () => { await view.root.findByType('button').props.onClick(); });
  assert.match(JSON.stringify(view.toJSON()), /Link expired/);
  assert.equal(view.root.findByType('button').props.disabled, false);
});
