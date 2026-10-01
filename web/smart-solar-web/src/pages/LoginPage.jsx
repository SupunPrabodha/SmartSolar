import { ActionLabel, BootScreen } from '../components/LoadingExperience';
import { useRef, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Icon from '../components/Icon';
import { notify, dismiss } from '../util/feedback';
import { signInFeedback } from '../util/signInFeedback';
import Brand from '../components/Brand';

export default function LoginPage() {
  const { user, login, sessionError, loading: restoring } = useAuth();
  const navigate = useNavigate();
  const [nic, setNic] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const submitting = useRef(false);
  const submitButton = useRef(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting.current) return;
    submitting.current = true;
    dismiss('sign-in');
    setLoading(true);
    const submittedPassword = password;
    setPassword('');
    try {
      await login(nic, submittedPassword);
      navigate('/', { replace: true });
    } catch (err) {
      const feedback = signInFeedback(err);
      notify(feedback.message, 'error', 'sign-in', feedback.title);
      requestAnimationFrame(() => submitButton.current?.focus());
    }
    finally { submitting.current = false; setLoading(false); }
  }

  if (restoring) return <BootScreen />;
  if (user) return <Navigate to="/" replace />;
  return <main className="login-page login-photo-page">
    <section className="login-story">
      <span className="login-photo-kicker">SMART SOLAR MICROGRID</span>
      <div className="login-story-copy">
        <p className="eyebrow">CONNECTED ENERGY · LOCAL IMPACT</p>
        <h2 className="login-story-title">Powering local<br />energy exchange.</h2>
        <p>Secure access to community energy operations, reservations and grid coordination.</p>
      </div>
      <svg className="login-photo-divider" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true" focusable="false">
        <path d="M100 0H42C100 28 0 64 42 100H100Z" fill="currentColor"/>
      </svg>
    </section>
    <section className="login-panel" aria-labelledby="login-title"><div className="login-form">
      <div className="login-form-brand"><Brand /></div>
      <p className="eyebrow">WELCOME BACK</p><h1 id="login-title">Sign in to your workspace</h1>
      <p className="text-secondary mb-4">For Backoffice and Grid Operator accounts. Prosumers use the Android app.</p>
      {sessionError && <p className="auth-session-note" role="status">We couldn’t restore your session. Check your connection and sign in again.</p>}
      <form onSubmit={handleSubmit} aria-busy={loading}>
        <div className="mb-3"><label className="form-label" htmlFor="nic">NIC</label>
          <input id="nic" autoComplete="username" className="form-control form-control-lg" value={nic}
            onChange={e => setNic(e.target.value)} required disabled={loading} /></div>
        <div className="mb-4"><label className="form-label" htmlFor="password">Password</label>
          <div className="password-control"><input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" className="form-control form-control-lg"
            value={password} onChange={e => setPassword(e.target.value)} required disabled={loading} />
          <button type="button" className="password-toggle" aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} disabled={loading} onClick={() => setShowPassword(v => !v)}><Icon name={showPassword ? 'eyeOff' : 'eye'}/></button></div></div>
        <Link className="auth-recovery-link" to="/forgot-password">Forgot password?</Link>
        <button ref={submitButton} className="btn btn-primary btn-lg w-100" disabled={loading}><ActionLabel busy={loading} pending="Signing in…">Sign in</ActionLabel></button>
        <div className="visually-hidden" role="status">{loading ? 'Signing in, please wait.' : ''}</div>
      </form>
      <p className="small text-secondary mt-4">Your account must be active. Contact your Backoffice administrator if you need access.</p>
    </div></section>
  </main>;
}
