import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { transformWithEsbuild } from 'vite';

// Use the production module, substituting only the same public build-time setting Vite supplies.
const source = await readFile(new URL('../src/api/apiClient.js', import.meta.url), 'utf8');
const { code } = await transformWithEsbuild(source, 'apiClient.js', {
  loader: 'js', format: 'esm',
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('https://api.example.invalid/api/v1') }
});
const { apiFetch } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
const originalFetch = globalThis.fetch;
let expired;

beforeEach(() => {
  const storage = new Map([['accessToken', 'test-session']]);
  globalThis.sessionStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key)
  };
  globalThis.window = new EventTarget();
  expired = 0;
  window.addEventListener('session-expired', () => {
    expired += 1;
    sessionStorage.removeItem('accessToken');
  });
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  delete globalThis.window;
  delete globalThis.sessionStorage;
});

test('matching 401 clears the session even when its JSON body is malformed', async () => {
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.get('Authorization'), 'Bearer test-session');
    return new Response('{invalid', { status: 401, headers: { 'content-type': 'application/problem+json' } });
  };
  await assert.rejects(apiFetch('/users/me'), error => error.status === 401);
  assert.equal(expired, 1);
  assert.equal(sessionStorage.getItem('accessToken'), null);
});

test('a delayed 401 from an old request preserves a newer login', async () => {
  let complete;
  globalThis.fetch = () => new Promise(resolve => { complete = resolve; });
  const pending = apiFetch('/users/me');
  sessionStorage.setItem('accessToken', 'new-test-session');
  complete(new Response('', { status: 401 }));
  await assert.rejects(pending, error => error.status === 401);
  assert.equal(expired, 0);
  assert.equal(sessionStorage.getItem('accessToken'), 'new-test-session');
});

test('anonymous login neither sends an Authorization header nor invalidates an existing session', async () => {
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.get('Authorization'), null);
    return new Response('', { status: 401 });
  };
  await assert.rejects(apiFetch('/auth/login', {
    method: 'POST', headers: { Authorization: 'must-be-removed' }, body: '{}'
  }), error => error.status === 401);
  assert.equal(expired, 0);
  assert.equal(sessionStorage.getItem('accessToken'), 'test-session');
});

test('a server outage reports its status without clearing the session', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ detail: 'Unavailable' }), {
    status: 503, headers: { 'content-type': 'application/problem+json' }
  });
  await assert.rejects(apiFetch('/users/me'), error => error.status === 503 && error.message === 'Unavailable');
  assert.equal(expired, 0);
  assert.equal(sessionStorage.getItem('accessToken'), 'test-session');
});

test('validation ProblemDetails preserves useful field messages and session', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ errors: { Name: ['Name is required.'], Latitude: ['Latitude is invalid.'] } }), {
    status: 400, headers: { 'content-type': 'application/problem+json' }
  });
  await assert.rejects(apiFetch('/stations', { method: 'POST', body: '{}' }),
    error => error.status === 400 && error.message.includes('Name is required.') && error.message.includes('Latitude is invalid.'));
  assert.equal(expired, 0);
});

test('password recovery is anonymous even with a saved session', async () => {
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.get('Authorization'), null);
    return new Response('{"message":"Generic response"}', {headers:{'content-type':'application/json'}});
  };
  await apiFetch('/auth/forgot-password',{method:'POST',body:'{"identifier":"fixture"}'});
  await apiFetch('/auth/reset-password',{method:'POST',body:'{}'});
  assert.equal(expired,0);
});
test('multipart avatar leaves content type boundary to the browser', async () => {
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.has('Content-Type'),false);
    assert.equal(options.headers.get('Authorization'),'Bearer test-session');
    return new Response(null,{status:204});
  };
  const data=new FormData();data.append('file',new Blob(['fixture'],{type:'image/png'}),'fixture.png');
  await apiFetch('/users/me/avatar',{method:'PUT',body:data});
});
test('export returns binary data through the same authenticated client', async () => {
  globalThis.fetch=async()=>new Response('id,name\r\n1,Fixture',{headers:{'content-type':'text/csv'}});
  const data=await apiFetch('/exports/stations.csv',{responseType:'blob'});
  assert.equal(await data.text(),'id,name\r\n1,Fixture');
});
test('safe server reference is preserved without trusting arbitrary header text', async () => {
  globalThis.fetch=async()=>new Response('{"detail":"Unavailable","correlationId":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',
    {status:503,headers:{'content-type':'application/problem+json'}});
  await assert.rejects(apiFetch('/users/me'),e=>e.correlationId==='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'&&e.message.includes('Reference:'));
  globalThis.fetch=async()=>new Response('{"detail":"Unavailable","correlationId":"private filesystem path"}',
    {status:503,headers:{'content-type':'application/problem+json'}});
  await assert.rejects(apiFetch('/users/me'),e=>e.correlationId===undefined&&!e.message.includes('filesystem'));
});
