import { useAuth } from '../auth/AuthContext';
import { Navigate } from 'react-router-dom';
import Brand from '../components/Brand';

export default function UnauthorizedPage() {
  const { user, logout } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <main className="container py-5" style={{ maxWidth: 600 }}>
    <Brand /><h1 className="h3 mt-5">This account cannot access the web workspace</h1>
    <p className="text-secondary">The web workspace is for Backoffice and GridOperator accounts.
      Prosumers can sign in using the Smart Solar Android app.</p>
    <button className="btn btn-primary" onClick={logout}>Sign out</button>
  </main>;
}
