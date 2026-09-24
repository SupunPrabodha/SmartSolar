import { useAuth } from '../auth/AuthContext';
import { Link, Navigate } from 'react-router-dom';
import Brand from '../components/Brand';

export default function UnauthorizedPage() {
  const { user, logout } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  const canUseWorkspace = ['Backoffice', 'GridOperator'].includes(user.role);
  return <main className="container py-5" style={{ maxWidth: 600 }}>
    <Brand /><h1 className="h3 mt-5">{canUseWorkspace
      ? 'This page is not available for your role'
      : 'This account cannot access the web workspace'}</h1>
    <p className="text-secondary">{canUseWorkspace
      ? 'Reservation management is available to Grid Operators.'
      : 'The web workspace is for Backoffice and GridOperator accounts. Prosumers can sign in using the Smart Solar Android app.'}</p>
    {canUseWorkspace && <Link className="btn btn-primary me-2" to="/">Back to workspace</Link>}
    <button className="btn btn-outline-secondary" onClick={logout}>Sign out</button>
  </main>;
}
