import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { build } from 'esbuild';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom';
import { createRoutesFromElements, matchRoutes } from 'react-router-dom';

// Compile real JSX without a browser; only the session provider is substituted.
const bundle = await build({
  stdin: { contents: `export { default as Home } from './src/pages/HomePage';
    export { default as Users } from './src/pages/UserManagementPage';
    export { ReservationLayout } from './src/pages/reservations/ReservationComponents';
    export { default as App } from './src/App';`,
    resolveDir: process.cwd(), loader: 'jsx' },
  bundle: true, write: false, platform: 'node', format: 'cjs', packages: 'external', jsx: 'automatic',
  define: { 'import.meta.env.DEV': 'false', 'import.meta.env.VITE_API_BASE_URL': '"https://example.invalid/api/v1"' },
  plugins: [{ name: 'test-session', setup(builder) {
    builder.onResolve({ filter: /auth\/AuthContext$/ }, () => ({ path: 'session', namespace: 'test-session' }));
    builder.onLoad({ filter: /.*/, namespace: 'test-session' }, () => ({
      contents: 'export const useAuth = () => globalThis.integrationSession; export const AuthProvider = ({children}) => children;'
    }));
  }}]
});
const compiled = { exports: {} };
new Function('module', 'exports', 'require', bundle.outputFiles[0].text)(compiled, compiled.exports, createRequire(import.meta.url));
const { Home, Users, App, ReservationLayout } = compiled.exports;
function session(role) {
  globalThis.integrationSession = { user: { fullName: 'Integration User', role, status: 'Active' },
    loading: false, refreshing: false, lastVerifiedAt: null, logout() {}, refreshProfile() {} };
}
function render(Component) {
  return renderToStaticMarkup(React.createElement(StaticRouter, { location: '/' }, React.createElement(Component)));
}
function navigation(html) { return html.match(/<nav\b[^>]*>[\s\S]*?<\/nav>/)[0]; }

test('Backoffice has one navigation entry per enabled module and working home cards', () => {
  session('Backoffice');
  const html = render(Home), nav = navigation(html);
  for (const path of ['/', '/users', '/stations'])
    assert.equal(nav.split('href="' + path + '"').length - 1, 1);
  assert.match(html, /Open User Management/);
  assert.match(html, /Open Microgrid Stations/);
  assert.doesNotMatch(nav, /Reservations|Transactions|Planned|Coming in feature development/);
});

test('GridOperator gets stations but no User Management entry or card', () => {
  session('GridOperator');
  const html = render(Home);
  assert.equal(navigation(html).split('href="/stations"').length - 1, 1);
  assert.doesNotMatch(html, /href="\/users"|Open User Management/);
  for (const path of ['/operator/reservations', '/operator/reservations/dashboard', '/operator/reservations/current', '/operator/reservations/history', '/operator/reservations/search'])
    assert.equal(navigation(html).split('href="' + path + '"').length - 1, 1);
  assert.match(navigation(html), /href="\/operator\/reservations\?status=Pending"[^>]*>.*Pending Queue/);
  assert.match(html, /Open Operations/);
  assert.match(html, /Open Booking History/);
  assert.doesNotMatch(html, /Not implemented|Planned/);
});

test('User Management retains its forms inside the common navigation and session shell', () => {
  session('Backoffice');
  const html = render(Users);
  assert.match(navigation(html), /href="\/stations"/);
  assert.match(html, /Sign out/);
  assert.match(html, /Create staff user/);
  assert.match(html, /Pending Prosumer activation/);
  assert.equal((html.match(/<main\b/g) ?? []).length, 1);
});

test('merged routes are unique and enforce both members role boundaries', () => {
  const routesElement = App().props.children.props.children;
  const routes = createRoutesFromElements(routesElement.props.children);
  assert.equal(new Set(routes.map(route => route.path)).size, routes.length);
  for (const [path, allowed] of [['/users', ['Backoffice']], ['/stations', ['Backoffice', 'GridOperator']]]) {
    const guard = matchRoutes(routes, path).at(-1).route.element;
    for (const role of ['Backoffice', 'GridOperator', 'Prosumer']) {
      session(role);
      const result = guard.type(guard.props);
      if (allowed.includes(role)) assert.equal(result, guard.props.children);
      else assert.equal(result.props.to, '/unauthorized');
    }
    globalThis.integrationSession = { user: null, loading: false };
    assert.equal(guard.type(guard.props).props.to, '/login');
  }
});

test('reservation workspace preserves station access and all operational links', () => {
  session('GridOperator');
  const html = render(ReservationLayout);
  for (const path of ['/', '/stations', '/operator/reservations', '/operator/reservations/dashboard', '/operator/reservations/current', '/operator/reservations/history', '/operator/reservations/search'])
    assert.equal(navigation(html).split('href="' + path + '"').length - 1, 1);
  assert.match(html, /Sign out/);
  assert.doesNotMatch(html, /href="\/users"|Phase 0|Planned/);
});

test('all reservation routes inherit GridOperator-only access', () => {
  const routes = createRoutesFromElements(App().props.children.props.children.props.children);
  for (const suffix of ['', '/dashboard', '/current', '/history', '/search', '/new', '/fixture-id', '/fixture-id/edit']) {
    const matches = matchRoutes(routes, '/operator/reservations' + suffix);
    assert.ok(matches);
    const guard = matches[0].route.element;
    assert.deepEqual(guard.props.roles, ['GridOperator']);
    for (const role of ['Backoffice', 'Prosumer']) {
      session(role);
      assert.equal(guard.type(guard.props).props.to, '/unauthorized');
    }
    session('GridOperator');
    assert.equal(guard.type(guard.props), guard.props.children);
    globalThis.integrationSession = { user: null, loading: false };
    assert.equal(guard.type(guard.props).props.to, '/login');
  }
});
