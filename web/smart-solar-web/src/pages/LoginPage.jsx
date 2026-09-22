import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Brand from '../components/Brand';

export default function LoginPage() {
  const { user, login, sessionError, loading: restoring } = useAuth();
  const navigate = useNavigate();
  const [nic, setNic] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setLoading(true);
    const submittedPassword = password;
    setPassword('');
    try {
      await login(nic, submittedPassword);
      navigate('/', { replace: true });
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }

  if (restoring) return <main className="container py-5" role="status">Checking your session...</main>;
  if (user) return <Navigate to="/" replace />;
  return <main className="login-page">
    <section className="login-story"><Brand />
      <div><p className="eyebrow">SMART SOLAR MICROGRID</p><h1>Shared energy.<br />A connected community.</h1>
        <p>Your workspace for a smarter local energy network.</p></div><small>Phase 0 / Common foundation</small>
    </section>
    <section className="login-panel" aria-labelledby="login-title"><div className="login-form">
      <p className="eyebrow">WELCOME BACK</p><h2 id="login-title">Sign in to your workspace</h2>
      <p className="text-secondary mb-4">For Backoffice and GridOperator accounts. Prosumers use the Android app.</p>
      {(error || sessionError) && <div className="alert alert-danger" role="alert">{error || sessionError}</div>}
      <form onSubmit={handleSubmit}>
        <div className="mb-3"><label className="form-label" htmlFor="nic">NIC</label>
          <input id="nic" autoComplete="username" className="form-control form-control-lg" value={nic}
            onChange={e => setNic(e.target.value)} required disabled={loading} /></div>
        <div className="mb-4"><label className="form-label" htmlFor="password">Password</label>
          <input id="password" type="password" autoComplete="current-password" className="form-control form-control-lg"
            value={password} onChange={e => setPassword(e.target.value)} required disabled={loading} /></div>
        <button className="btn btn-primary btn-lg w-100" disabled={loading}>{loading ? 'Signing in...' : 'Sign in'}</button>
        <div className="visually-hidden" role="status">{loading ? 'Signing in, please wait.' : ''}</div>
      </form>
      <p className="small text-secondary mt-4">Your account must be active. Contact your Backoffice administrator if you need access.</p>
    </div></section>
  </main>;
}
