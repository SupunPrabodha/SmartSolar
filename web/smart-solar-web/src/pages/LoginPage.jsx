import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Brand from '../components/Brand';

export default function LoginPage() {
  const { user, login, sessionError, loading: restoring } = useAuth();
  const navigate = useNavigate();
  const [nic, setNic] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
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
      <div><p className="eyebrow">SMART SOLAR MICROGRID</p><h1>Powering local<br />energy exchange.</h1>
        <p>A connected workspace for community energy, confident decisions and secure transfers.</p><div className="energy-network" aria-hidden="true"><span>Generation</span><i /><span>Community</span><i /><span>Exchange</span></div></div><small>CONNECTED ENERGY · LOCAL IMPACT</small>
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
          <div className="password-control"><input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" className="form-control form-control-lg"
            value={password} onChange={e => setPassword(e.target.value)} required disabled={loading} />
          <button type="button" className="password-toggle" aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} disabled={loading} onClick={() => setShowPassword(v => !v)}>{showPassword ? 'Hide' : 'Show'}</button></div></div>
        <button className="btn btn-primary btn-lg w-100" disabled={loading}>{loading ? 'Signing in...' : 'Sign in'}</button>
        <div className="visually-hidden" role="status">{loading ? 'Signing in, please wait.' : ''}</div>
      </form>
      <Link className="d-inline-block mt-3" to="/forgot-password">Forgot password?</Link>
      <p className="small text-secondary mt-4">Your account must be active. Contact your Backoffice administrator if you need access.</p>
    </div></section>
  </main>;
}
