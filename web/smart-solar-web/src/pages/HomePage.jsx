import { useCallback, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { apiFetch } from '../api/apiClient';
import Brand from '../components/Brand';
import Icon from '../components/Icon';
import { activeWorkspaceRoute } from '../util/navigation';
import OperationsDashboardPage from './reservations/OperationsDashboardPage';
import { useReservationData } from './reservations/useReservationData';

const operatorLinks = [
  ['/operator/reservations', 'Manage Reservations', 'bookings'],
  ['/operator/reservations/dashboard', 'Operations Dashboard', 'dashboard'],
  ['/operator/reservations/current', 'Current Bookings', 'current'],
  ['/operator/reservations?status=Pending', 'Pending Queue', 'pending'],
  ['/operator/reservations/history', 'Booking History', 'history'],
  ['/operator/reservations/search', 'Search Bookings', 'search']
];

export default function HomePage({ children }) {
  const { user, logout, refreshProfile, refreshing, sessionError, lastVerifiedAt } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  if (!user) return null;
  const links = [['/', 'Home', 'home'],
    ...(user.role === 'Backoffice' ? [['/users', 'User Management', 'users'], ['/users?status=PendingActivation', 'Pending Activations', 'pending']] : []),
    ['/stations', 'Microgrid Stations', 'station'],
    ...(user.role === 'GridOperator' ? operatorLinks : [])];
  const active = activeWorkspaceRoute(location.pathname, location.search);
  const context = links.find(([path]) => path === active)?.[1] || 'Workspace';
  const hour = new Date().getHours();
  const greeting = `Good ${hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening'}, ${user.fullName}`;
  return <div className="workspace">
    <a className="skip-link" href="#main">Skip to content</a>
    <aside className="workspace-sidebar">
      <div className="sidebar-heading"><Brand /><button className="nav-toggle" aria-expanded={menuOpen} aria-controls="workspace-navigation" onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? 'Close menu' : 'Menu'}</button></div>
      <nav id="workspace-navigation" className={menuOpen ? 'workspace-nav open' : 'workspace-nav'} aria-label="Workspace">
        <span className="nav-caption">WORKSPACE</span>
        {links.map(([path, label, icon]) => <Link key={path} to={path} aria-current={active === path ? 'page' : undefined}
          className={`workspace-nav-item${active === path ? ' active' : ''}`} onClick={() => setMenuOpen(false)}><Icon name={icon} />{label}</Link>)}
      </nav>
      <div className="sidebar-footer"><span className="status-dot" />Connected community<small>Shared energy. Local impact.</small></div>
    </aside>
    <div className="workspace-body">
      <header className="workspace-topbar"><span className="topbar-context">{context}</span><div className="topbar-account"><span className="account-name">{user.fullName}</span><span className="role-pill">{user.role === 'GridOperator' ? 'Grid Operator' : 'Backoffice'}</span><button className="btn btn-outline-secondary btn-sm" onClick={logout}>Sign out</button></div></header>
      <main id="main" className="workspace-main" tabIndex="-1">
        {children || <>
          {user.role === 'GridOperator' ? <OperationsDashboardPage greeting={greeting} /> : <BackofficeOverview greeting={greeting} />}
          <div className="session-note">
            <span>{refreshing ? 'Checking profile…' : lastVerifiedAt ? `Profile updated ${lastVerifiedAt.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}` : user.status}</span>
            <button className="text-action" disabled={refreshing} onClick={refreshProfile}>Refresh profile</button>
          </div>
          {sessionError && <div className="alert alert-warning" role="alert">{sessionError} Previously verified profile shown.</div>}
        </>}
        <footer className="workspace-footer">Smart Solar Microgrid<span>Local energy. Connected operations.</span></footer>
      </main>
    </div>
  </div>;
}
function BackofficeOverview({ greeting }) {
  const load = useCallback(async signal => {
    const [users, stations] = await Promise.all([apiFetch('/users', {signal}), apiFetch('/stations?includeInactive=true', {signal})]);
    return { users: users.length, pending: users.filter(u => u.role === 'Prosumer' && u.status === 'PendingActivation').length, stations: stations.length };
  }, []);
  const {data, error, loading, reload} = useReservationData(load);
  return <>
    <div className="page-heading"><div><p className="eyebrow">NETWORK ADMINISTRATION</p><h1>{greeting}</h1><p className="text-secondary mb-0">Manage community access and your microgrid station network.</p></div><button className="btn btn-outline-secondary" onClick={reload} disabled={loading}>Refresh</button></div>
    {error && <div className="alert alert-danger" role="alert">Unable to load the overview. Try refreshing.</div>}
    <section className="metrics-grid" aria-label="Network overview">
      {[['Community accounts', data?.users], ['Pending activations', data?.pending], ['Microgrid stations', data?.stations]].map(([label,value]) =>
        <article className="surface-card metric-card" key={label}><p className="card-label">{label}</p><strong className="metric-value">{loading ? '…' : error ? '—' : value ?? '—'}</strong></article>)}
    </section>
    <h2 className="section-title">Administration</h2>
    <div className="quick-actions">
      {[['/users','User Management','Manage accounts and staff access.','users'],['/users?status=PendingActivation','Pending Activations','Review Prosumer access requests.','pending'],['/stations','Microgrid Stations','Maintain your energy network.','station']].map(([path,title,description,icon]) =>
        <Link className="quick-action" to={path} key={path}><Icon name={icon}/><span><strong>{title}</strong><small>{description}</small></span><span aria-hidden="true">→</span></Link>)}
    </div>
  </>;
}
