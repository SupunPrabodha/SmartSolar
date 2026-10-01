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
  return <main className="login-page">
    <section className="login-story"><Brand />
      <div><p className="eyebrow">SMART SOLAR MICROGRID</p><h2 className="login-story-title">Powering local<br />energy exchange.</h2>
        <p>Secure access to community energy operations, reservations and grid coordination.</p>
        <svg className="login-grid-motif" viewBox="0 0 440 120" fill="none" aria-hidden="true" focusable="false">
          <path d="M12 90H88L132 46H216L260 90H342L396 36H428M88 90V110M216 46V12M342 90V110" stroke="currentColor" strokeWidth="1.5"/>
          <circle cx="216" cy="46" r="29" stroke="currentColor" strokeOpacity=".35"/>
          <circle cx="216" cy="46" r="17" stroke="#F6C344" strokeWidth="2"/>
          <path d="M216 21V17M216 75V71M191 46H187M245 46H241" stroke="#F6C344" strokeWidth="2" strokeLinecap="round"/>
          <g fill="currentColor"><circle cx="88" cy="90" r="4"/><circle cx="132" cy="46" r="4"/><circle cx="260" cy="90" r="4"/><circle cx="342" cy="90" r="4"/><circle cx="396" cy="36" r="4"/></g>
        </svg>
      </div><small>CONNECTED ENERGY · LOCAL IMPACT</small>
    </section>
    <section className="login-panel" aria-labelledby="login-title"><div className="login-form">
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
