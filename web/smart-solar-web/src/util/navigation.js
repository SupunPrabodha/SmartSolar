// One route key owns selection, including query-based queues and transaction detail routes.
export function activeWorkspaceRoute(pathname, search = '') {
  const status = new URLSearchParams(search).get('status');
  if (pathname === '/users') return status === 'PendingActivation' ? '/users?status=PendingActivation' : '/users';
  if (pathname === '/operator/reservations' && status === 'Pending') return '/operator/reservations?status=Pending';
  const views = ['dashboard', 'current', 'history', 'search'];
  if (views.some(view => pathname === '/operator/reservations/' + view)) return pathname;
  if (pathname.startsWith('/operator/reservations')) return '/operator/reservations';
  return pathname;
}
