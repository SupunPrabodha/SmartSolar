import { useEffect, useState } from 'react';

export default function VerifyEmailPage() {
  const [link] = useState(() => new URLSearchParams(window.location.hash.slice(1)));
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { window.history.replaceState(null, '', window.location.pathname); }, []);
  const valid = Boolean(link.get('nic')) && /^[a-fA-F0-9]{64}$/.test(link.get('token') || '');
  async function verify() {
    setBusy(true); setError('');
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '');
      if (!baseUrl) throw new Error('The API address is not configured. Contact Backoffice.');
      const response = await fetch(`${baseUrl}/auth/verify-email`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'omit',
        body: JSON.stringify({ nic: link.get('nic'), token: link.get('token') })
      });
      if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail || 'Verification failed. Ask Backoffice to resend your link.');
      }
      setDone(true);
    } catch (failure) { setError(failure.message || 'Unable to connect. Please try again.'); }
    finally { setBusy(false); }
  }
  return <main className="container py-5" style={{ maxWidth: 560 }}><section className="card p-4 shadow-sm">
    <h1 className="h3">Verify your Smart Solar email</h1>
    {done ? <div role="status" className="alert alert-success">Your email is verified and your account is active. Open the Android app and sign in using your NIC and password.</div> : <>
      <p>Confirm your email to activate the account approved by Backoffice. Links expire after 24 hours.</p>
      {!valid && <div className="alert alert-warning">Open the complete link from your verification email. If it has expired, ask Backoffice to resend it.</div>}
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      <button className="btn btn-primary" disabled={!valid || busy} onClick={verify}>{busy ? 'Verifying…' : 'Verify email'}</button>
    </>}
  </section></main>;
}
