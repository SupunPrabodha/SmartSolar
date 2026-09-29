import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Brand from '../components/Brand';
import { apiFetch } from '../api/apiClient';
import { notify } from '../util/feedback';

export function PasswordField({ label, name, value, onChange, current = false, disabled }) {
  const [show, setShow] = useState(false);
  return <div className="mb-3"><label htmlFor={name} className="form-label">{label}</label><div className="password-control">
    <input id={name} className="form-control" type={show ? 'text' : 'password'} value={value} onChange={onChange}
      required minLength={current ? 1 : 8} maxLength={100} disabled={disabled} autoComplete={current ? 'current-password' : 'new-password'} />
    <button type="button" className="password-toggle" aria-pressed={show} onClick={() => setShow(!show)}>{show ? 'Hide' : 'Show'}</button>
  </div></div>;
}
export default function PasswordRecoveryPage({ reset = false }) {
  const token = useRef(new URLSearchParams(window.location.hash.slice(1)).get('token') || '');
  useEffect(() => { if (reset) window.history.replaceState(null, '', window.location.pathname); }, [reset]);
  const [identifier, setIdentifier] = useState(''), [password, setPassword] = useState(''), [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [done, setDone] = useState(false);
  async function submit(event) {
    event.preventDefault(); setError('');
    if (reset && password !== confirm) { setError('Passwords do not match.'); return; }
    setBusy(true);
    try {
      const result = await apiFetch('/auth/' + (reset ? 'reset-password' : 'forgot-password'), {
        method: 'POST', body: JSON.stringify(reset ? { token: token.current, newPassword: password } : { identifier })
      });
      setDone(true); setPassword(''); setConfirm(''); token.current = ''; notify(result.message);
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <main className="recovery-page"><Brand /><section className="surface-card recovery-card">
    <p className="eyebrow">ACCOUNT SECURITY</p><h1>{reset ? 'Choose a new password' : 'Recover your account'}</h1>
    {done ? <p role="status">{reset ? 'Your password is reset. Sign in with your new password.' : 'If an eligible account exists, reset instructions have been sent. Check your email, including spam.'}</p> :
      reset && !token.current ? <p role="alert">This password reset link is invalid or has expired. <Link to="/forgot-password">Request a new link</Link>.</p> :
      <form onSubmit={submit} aria-busy={busy}>{error && <div role="alert" className="alert alert-danger">{error}{reset && <p><Link to="/forgot-password">Request a new link</Link></p>}</div>}
        {reset ? <><p>Use 8–100 characters. All previous sessions will be signed out.</p>
          <PasswordField label="New password" name="new-password" value={password} onChange={e => setPassword(e.target.value)} disabled={busy} />
          <PasswordField label="Confirm new password" name="confirm-password" value={confirm} onChange={e => setConfirm(e.target.value)} disabled={busy} /></> :
          <div className="mb-3"><label htmlFor="recovery-identifier" className="form-label">Email or NIC</label>
            <input id="recovery-identifier" className="form-control" autoComplete="username" required maxLength={254} value={identifier} onChange={e => setIdentifier(e.target.value)} disabled={busy} />
            <p className="form-text">Enter the email address or NIC associated with your account.</p></div>}
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Please wait…' : reset ? 'Reset password' : 'Send reset instructions'}</button>
      </form>}
    <Link className="d-inline-block mt-4" to="/login">Back to Sign In</Link>
  </section></main>;
}
