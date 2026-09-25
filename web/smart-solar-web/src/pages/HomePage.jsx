import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Brand from '../components/Brand';

const modulesByRole = {
  Backoffice: [
    ['User Management', 'Manage community accounts and access.'],
    ['Microgrid Stations', 'Oversee the stations in your network.'],
    ['Reservations', 'Review energy reservations.'],
    ['Transactions', 'Follow completed energy transactions.']
  ],
  GridOperator: [
    ['Operations', 'Your station operations workspace.'],
    ['Microgrid Stations', 'Access your assigned station information.'],
    ['Transactions', 'Review and complete energy transactions.']
  ]
};

export default function HomePage() {
  const { user, logout, refreshProfile, refreshing, sessionError, lastVerifiedAt } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  if (!user) return null;
  const modules = modulesByRole[user.role] ?? [];
  return <div className="workspace">
    <a className="skip-link" href="#main">Skip to content</a>
    <aside className="workspace-sidebar">
      <div className="sidebar-heading"><Brand />
        <button className="nav-toggle" aria-expanded={menuOpen} aria-controls="workspace-navigation"
          onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? 'Close menu' : 'Menu'}</button></div>
      <nav id="workspace-navigation" className={menuOpen ? 'workspace-nav open' : 'workspace-nav'} aria-label="Workspace">
        <span className="nav-caption">WORKSPACE</span>
        <NavLink to="/" end className={({ isActive }) => `workspace-nav-item${isActive ? ' active' : ''}`} onClick={() => setMenuOpen(false)}>
          <span aria-hidden="true">01</span>Home</NavLink>
        {user.role === 'GridOperator' && (
          <NavLink
            to="/operator/reservations"
            className={({ isActive }) => `workspace-nav-item${isActive ? ' active' : ''}`}
            onClick={() => setMenuOpen(false)}
          >
            <span aria-hidden="true">02</span>Manage Reservations
          </NavLink>
        )}
        <span className="nav-caption mt-4">UPCOMING MODULES</span>
        {modules.map(([name], index) => <button className="workspace-nav-item" key={name} disabled>
          <span aria-hidden="true">0{index + (user.role === 'GridOperator' ? 3 : 2)}</span>{name}<small>Planned</small></button>)}
      </nav>
      <div className="sidebar-footer"><span className="status-dot" />Phase 0 foundation
        <small>Shared starting point for the team</small></div>
    </aside>
    <div className="workspace-body">
      <header className="workspace-topbar">
        <span className="environment-label">{import.meta.env.DEV ? 'Development' : 'Production build'}</span>
        <div className="topbar-account"><span className="account-name">{user.fullName}</span>
          <button className="btn btn-outline-secondary btn-sm" onClick={logout}>Sign out</button></div>
      </header>
      <main id="main" className="workspace-main" tabIndex="-1">
        <div className="page-heading"><div><p className="eyebrow">YOUR WORKSPACE</p>
          <h1>Welcome, {user.fullName}</h1><p className="text-secondary mb-0">Your account and shared workspace, in one place.</p></div>
          <span className="role-pill">{user.role}</span></div>
        {user.role === 'GridOperator' && <div className="d-flex flex-wrap gap-2 mb-4">
          <Link className="btn btn-primary" to="/operator/reservations/dashboard">Operations Dashboard</Link>
          <Link className="btn btn-outline-primary" to="/operator/reservations/current">Current Bookings</Link>
          <Link className="btn btn-outline-primary" to="/operator/reservations/pending">Pending Queue</Link>
          <Link className="btn btn-outline-primary" to="/operator/reservations/history">History</Link>
          <Link className="btn btn-outline-primary" to="/operator/reservations/search">Search</Link>
          <Link className="btn btn-outline-secondary" to="/operator/reservations">Manage reservations</Link>
        </div>}
        <section className="foundation-banner" aria-labelledby="foundation-title">
          <span className="banner-orbit" aria-hidden="true" />
          <div className="position-relative"><p className="eyebrow">CONNECTED COMMUNITY. SHARED ENERGY.</p>
            <h2 id="foundation-title">System foundation ready.</h2>
            <p>Your common account workspace is available. Business modules will arrive during feature development.</p>
            <span className="banner-tag">Phase 0 / Common foundation</span></div>
        </section>
        <section aria-label="Account and session" className="session-grid">
          <article className="surface-card"><p className="card-label">ACCOUNT ROLE</p>
            <h2>{user.role}</h2><p>Workspace access follows your API account.</p></article>
          <article className="surface-card"><p className="card-label">ACCOUNT STATUS</p>
            <h2><span className="status-dot" />{user.status}</h2><p>Last received from your account profile.</p></article>
          <article className="surface-card"><p className="card-label">SESSION VERIFICATION</p>
            <h2 className="session-heading">{refreshing ? 'Checking profile...' : sessionError ? 'Check connection' : 'Session verified'}</h2>
            <p>{lastVerifiedAt ? `Last verified at ${lastVerifiedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}.` : 'Profile verification is pending.'}</p>
            <button className="text-action" disabled={refreshing} onClick={refreshProfile}>Refresh profile &rarr;</button></article>
        </section>
        {sessionError && <div className="alert alert-warning mt-3" role="alert">{sessionError} Previously verified profile shown.</div>}
        <section className="modules-section" aria-labelledby="modules-title">
          <div className="section-heading"><div><p className="eyebrow">COMING NEXT</p><h2 id="modules-title">Your workspace modules</h2></div>
            <span className="text-secondary small">Planned for feature development</span></div>
          <div className="module-grid">{modules.map(([name, description], index) => <article className="surface-card module-card" key={name}>
            <span className="module-number" aria-hidden="true">0{index + 1}</span><span className="planned-badge">Not implemented</span>
            <h3>{name}</h3><p>{description}</p>
            <button className="btn btn-light w-100 mt-auto" disabled>Coming in feature development</button>
          </article>)}</div>
        </section>
        <footer className="workspace-footer">Smart Solar Microgrid <span>Shared Phase-0 foundation</span></footer>
      </main>
    </div>
  </div>;
}
