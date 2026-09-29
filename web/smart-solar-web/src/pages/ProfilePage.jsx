import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import HomePage from './HomePage';
import { PasswordField } from './PasswordRecoveryPage';
import { useAuth } from '../auth/AuthContext';
import { apiFetch } from '../api/apiClient';
import { notify } from '../util/feedback';
import { AuditHistory } from '../components/Experience';

export default function ProfilePage() {
  const { user, refreshProfile, logout } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({fullName:user.fullName,email:user.email,phoneNumber:user.phoneNumber});
  const [avatar, setAvatar] = useState(''), [revision, setRevision] = useState(0), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [current, setCurrent] = useState(''), [password, setPassword] = useState(''), [confirm, setConfirm] = useState('');
  useEffect(() => {
    let alive = true, url;
    apiFetch('/users/me/avatar', {responseType:'blob'}).then(blob => {
      if (!alive) return; url = URL.createObjectURL(blob); setAvatar(url);
    }).catch(() => { if (alive) setAvatar(''); });
    return () => { alive = false; if (url) URL.revokeObjectURL(url); };
  }, [user.nic, revision]);
  async function save(event) {
    event.preventDefault(); setBusy(true); setError('');
    try { await apiFetch('/users/me', {method:'PUT',body:JSON.stringify(form)}); await refreshProfile(); notify('Profile saved.'); }
    catch(e) { setError(e.message); } finally { setBusy(false); }
  }
  async function picture(file) {
    if (!file) return;
    if (!['image/jpeg','image/png','image/webp'].includes(file.type) || file.size > 2000000) { setError('Choose a JPEG, PNG or WebP image under 2 MB.'); return; }
    setBusy(true); setError('');
    try { const data = new FormData(); data.append('file', file); await apiFetch('/users/me/avatar', {method:'PUT',body:data}); setRevision(x=>x+1); await refreshProfile(); notify('Photo updated. Save your profile to finish.'); }
    catch(e) { setError(e.message); } finally { setBusy(false); }
  }
  async function change(event) {
    event.preventDefault(); setError('');
    if (password !== confirm) { setError('Passwords do not match.'); return; }
    setBusy(true);
    try { await apiFetch('/users/me/change-password', {method:'POST',body:JSON.stringify({currentPassword:current,newPassword:password})});
      logout(); navigate('/login', {replace:true}); notify('Password changed. Sign in again.'); }
    catch(e) { setError(e.message); } finally { setBusy(false); setCurrent(''); setPassword(''); setConfirm(''); }
  }
  return <HomePage><div className="page-heading"><div><p className="eyebrow">YOUR ACCOUNT</p><h1>My Profile</h1><p>Keep your contact details current and your account secure.</p></div></div>
    {error && <div role="alert" className="alert alert-danger">{error}</div>}
    <section className="surface-card p-4 mb-4"><div className="profile-summary">{avatar ? <img className="profile-avatar" src={avatar} alt="Your profile" /> :
      <span className="profile-avatar avatar-initials" aria-label="No profile photo">{user.fullName.slice(0,1)}</span>}
      <div><h2>{user.fullName}</h2><p>{user.nic} · {user.role} · {user.status}</p><p className="small text-secondary">NIC, role and account state are managed by the service.</p></div></div>
      <label className="form-label" htmlFor="avatar">Profile photo</label><input id="avatar" className="form-control mb-2" type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={e=>{picture(e.target.files[0]); e.target.value='';}} />
      <p className="form-text">JPEG, PNG or WebP, up to 2 MB. Images are resized to 512 pixels.</p>
      {avatar && <button className="btn btn-outline-secondary mb-3" disabled={busy} onClick={async()=>{setBusy(true);try{await apiFetch('/users/me/avatar',{method:'DELETE'});setRevision(x=>x+1);await refreshProfile();notify('Photo removed.');}catch(e){setError(e.message);}finally{setBusy(false);}}}>Remove photo</button>}
      <form onSubmit={save}>{[['fullName','Full name',120],['email','Email',254],['phoneNumber','Phone number',20]].map(([key,label,max])=>
        <div key={key} className="mb-3"><label htmlFor={key} className="form-label">{label}</label><input id={key} className="form-control" value={form[key]} required maxLength={max}
          type={key==='email'?'email':'text'} onChange={e=>setForm({...form,[key]:e.target.value})} disabled={busy}/></div>)}
        <button className="btn btn-primary" disabled={busy}>Save profile</button></form></section>
    <section className="surface-card p-4 mb-4"><h2>Change password</h2><p>Use 8–100 characters. Changing your password will sign you out of your current sessions.</p>
      <form onSubmit={change}><PasswordField label="Current password" name="current-password" current value={current} onChange={e=>setCurrent(e.target.value)} disabled={busy}/>
        <PasswordField label="New password" name="new-password" value={password} onChange={e=>setPassword(e.target.value)} disabled={busy}/>
        <PasswordField label="Confirm password" name="confirm-password" value={confirm} onChange={e=>setConfirm(e.target.value)} disabled={busy}/>
        <button className="btn btn-primary" disabled={busy}>Change password</button></form></section>
    <AuditHistory kind="users" id="me" />
  </HomePage>;
}
