import Icon from '../components/Icon';
import { AuditHistory, ExportButton } from '../components/Experience';
import { useEffect, useMemo, useState } from 'react';
import { apiFetch } from '../api/apiClient';
import HomePage from './HomePage';
import { useSearchParams } from 'react-router-dom';
import { StatusBadge } from './reservations/ReservationComponents';

import Overlay, { ConfirmDialog } from '../components/Overlay';
import { Toast, LoadingState } from '../components/Feedback';

const blankProfile = { nic: '', fullName: '', email: '', phoneNumber: '', password: '' };
const blankStaff = { ...blankProfile, role: 'GridOperator' };

function ProfileForm({ value, onChange, onSubmit, busy, includeRole = false, submitLabel = 'Create staff' }) {
  return <form className="row g-2" onSubmit={onSubmit}><fieldset className="row g-2 m-0 p-0" disabled={busy}>
    <div className="col-md-6"><label className="form-label d-block">NIC<input required className="form-control" name="nic" placeholder="NIC" value={value.nic} onChange={onChange} /></label></div>
    <div className="col-md-6"><label className="form-label d-block">Full name<input required className="form-control" name="fullName" placeholder="Full name" value={value.fullName} onChange={onChange} /></label></div>
    <div className="col-md-6"><label className="form-label d-block">Email<input required className="form-control" type="email" name="email" placeholder="Email" value={value.email} onChange={onChange} /></label></div>
    <div className="col-md-6"><label className="form-label d-block">Phone number<input required className="form-control" name="phoneNumber" placeholder="Phone number" value={value.phoneNumber} onChange={onChange} /></label></div>
    <div className="col-md-6"><label className="form-label d-block">Password<input required className="form-control" type="password" name="password" minLength="8" placeholder="Password (8+ characters)" value={value.password} onChange={onChange} /></label></div>
    {includeRole && <div className="col-md-6"><select aria-label="Staff role" className="form-select" name="role" value={value.role} onChange={onChange}><option value="GridOperator">GridOperator</option><option value="Backoffice">Backoffice</option></select></div>}
    <div className="col-12"><button className="btn btn-primary" disabled={busy}>{submitLabel}</button></div>
  </fieldset></form>;
}

export default function UserManagementPage() {
  const [searchParams] = useSearchParams();
  const [auditing, setAuditing] = useState(null);
  const [creating, setCreating] = useState(null), [deactivating, setDeactivating] = useState(null);
  const pendingOnly = searchParams.get('status') === 'PendingActivation';
  const [users, setUsers] = useState([]); const [staff, setStaff] = useState(blankStaff); const [prosumer, setProsumer] = useState(blankProfile);
  const [editing, setEditing] = useState(null); const [query, setQuery] = useState(searchParams.get('q') || ''); const [message, setMessage] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  const load = async () => { setBusy(true); setError(''); try { setUsers(await apiFetch('/users')); } catch (e) { setError(e.message); } finally { setBusy(false); } };
  useEffect(() => { load(); }, []);
  const update = (setter, value) => e => setter({ ...value, [e.target.name]: e.target.value });
  const shown = useMemo(() => users.filter(u => (!pendingOnly || u.status === 'PendingActivation') && `${u.nic} ${u.fullName} ${u.email} ${u.role} ${u.status}`.toLowerCase().includes(query.toLowerCase())), [users, query, pendingOnly]);
  const pending = users.filter(u => u.role === 'Prosumer' && u.status === 'PendingActivation');
  const submit = async (event, path, value, successText, reset) => { event.preventDefault(); setBusy(true); setError(''); setMessage(''); try { await apiFetch(path, { method: 'POST', body: JSON.stringify(value) }); reset(); setCreating(null); setMessage(successText); await load(); } catch (e) { setError(e.message); setBusy(false); } };
  const approvalLabel = user => user.role !== 'Prosumer' ? 'Activate' : user.approvedAtUtc ? 'Resend verification' : 'Approve & send email';
  const statusChange = async (user, action) => { setBusy(true); setError(''); setMessage(''); try { await apiFetch(`/users/${encodeURIComponent(user.nic)}/${action}`, { method: 'PATCH' }); setDeactivating(null); setMessage(action === 'activate' && user.role === 'Prosumer' ? `Verification email sent to ${user.email}. The account stays pending until verified.` : `${user.fullName}'s account was ${action === 'activate' ? 'activated' : 'deactivated'}.`); await load(); } catch (e) { setError(e.message); setBusy(false); } };
  const saveProsumer = async event => { event.preventDefault(); setBusy(true); setError(''); try { await apiFetch(`/users/${encodeURIComponent(editing.nic)}`, { method: 'PUT', body: JSON.stringify(editing) }); setEditing(null); setMessage('Prosumer profile updated.'); await load(); } catch (e) { setError(e.message); setBusy(false); } };
  return <HomePage><div><div className="d-flex flex-wrap gap-3 justify-content-between align-items-start mb-4"><div><p className="eyebrow">BACKOFFICE</p><h1>{pendingOnly ? 'Pending Activations' : 'User management'}</h1><p className="text-secondary mb-0">Manage staff, Prosumer profiles and pending activation.</p></div><button className="btn btn-outline-primary" disabled={busy} onClick={load}><Icon name="refresh"/>Refresh</button></div>
    <ExportButton kind="users" query={new URLSearchParams({ search: query, status: pendingOnly ? 'PendingActivation' : '' }).toString()} />
    {auditing && <Overlay title="Account audit history" onClose={()=>setAuditing(null)}><AuditHistory kind="users" id={auditing}/></Overlay>}
    {error && !editing && !creating && !deactivating && <div className="alert alert-danger" role="alert">{error}</div>}<Toast key={message} message={message} />
    <p className="text-secondary">Changing a Prosumer email requires new approval and email verification. Deactivation preserves account history and revokes pending verification links.</p>
    <div className="d-flex flex-wrap gap-2 mb-4"><button className="btn btn-primary" disabled={busy} onClick={() => { setError(''); setCreating('staff'); }}>Create staff user</button><button className="btn btn-outline-primary" disabled={busy} onClick={() => { setError(''); setCreating('prosumer'); }}>Register Prosumer</button></div>
    {creating && <Overlay title={creating === 'staff' ? 'Create staff user' : 'Register Prosumer'} busy={busy} onClose={() => { setCreating(null); setError(''); setStaff(blankStaff); setProsumer(blankProfile); }}>
      <p className="text-secondary">{creating === 'staff' ? 'Create an active Backoffice or GridOperator account.' : 'The account will wait for Backoffice activation.'} All fields are required.</p>
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      <ProfileForm value={creating === 'staff' ? staff : prosumer} onChange={creating === 'staff' ? update(setStaff, staff) : update(setProsumer, prosumer)}
        onSubmit={e => creating === 'staff' ? submit(e, '/users/staff', staff, 'Staff account created.', () => setStaff(blankStaff)) : submit(e, '/auth/register-prosumer', prosumer, 'Prosumer registered and added to pending activation.', () => setProsumer(blankProfile))}
        busy={busy} includeRole={creating === 'staff'} submitLabel={busy ? 'Creating…' : creating === 'staff' ? 'Create staff' : 'Register Prosumer'} />
    </Overlay>}
    {deactivating && <ConfirmDialog title="Deactivate account?" action="Deactivate account" busy={busy} onClose={() => { setDeactivating(null); setError(''); }} onConfirm={() => statusChange(deactivating, 'deactivate')}>
      <p>{deactivating.fullName} will lose access to Smart Solar. Their records will be retained.</p>{error && <div className="alert alert-danger" role="alert">{error}</div>}
    </ConfirmDialog>}
    {busy && !creating && !editing && !deactivating && <LoadingState label="Updating accounts…" />}
    <section className="card p-3 mb-4"><h2 className="h5">Pending Prosumer activation</h2><p className="text-secondary">Approve the account to send a verification email. Login becomes available only after verification. Resending replaces the previous link; wait one minute between attempts.</p>{pending.length ? <div className="table-responsive"><table className="table align-middle mb-0"><thead><tr><th>NIC</th><th>Prosumer</th><th>Contact</th><th>Stage</th><th>Action</th></tr></thead><tbody>{pending.map(u => <tr key={u.nic}><td>{u.nic}</td><td>{u.fullName}</td><td>{u.email}<small className="d-block text-secondary">{u.phoneNumber}</small></td><td>{u.approvedAtUtc ? 'Awaiting email verification' : 'Awaiting approval'}</td><td><button className="btn btn-sm btn-success" disabled={busy} onClick={() => statusChange(u, 'activate')}>{approvalLabel(u)}</button></td></tr>)}</tbody></table></div> : <p className="text-secondary mb-0">No Prosumer accounts are waiting for activation.</p>}</section>
    {editing && <Overlay title={'Edit Prosumer: ' + editing.nic} busy={busy} onClose={() => { setEditing(null); setError(''); }}>{error && <div className="alert alert-danger" role="alert">{error}</div>}<p className="small text-secondary">NIC, role, account status and password cannot be changed here.</p><form className="row g-2" onSubmit={saveProsumer}><fieldset className="row g-2 m-0 p-0" disabled={busy}><div className="col-md-4"><label className="form-label d-block">Full name<input required className="form-control" name="fullName" value={editing.fullName} onChange={update(setEditing, editing)} /></label></div><div className="col-md-4"><label className="form-label d-block">Email<input required className="form-control" type="email" name="email" value={editing.email} onChange={update(setEditing, editing)} /></label></div><div className="col-md-4"><label className="form-label d-block">Phone number<input required className="form-control" name="phoneNumber" value={editing.phoneNumber} onChange={update(setEditing, editing)} /></label></div><div className="col-12 d-flex gap-2"><button className="btn btn-primary" disabled={busy}>Save Prosumer</button><button type="button" className="btn btn-outline-secondary" disabled={busy} onClick={() => setEditing(null)}>Cancel</button></div></fieldset></form></Overlay>}
    <section className="card p-3"><div className="d-flex flex-wrap gap-3 justify-content-between mb-3"><h2 className="h5 mb-0">All accounts</h2><input className="form-control w-auto" aria-label="Search accounts" placeholder="Search accounts" value={query} onChange={e => setQuery(e.target.value)} /></div><div className="table-responsive"><table className="table align-middle polished-user-table"><thead><tr><th>NIC</th><th>Name</th><th>Role</th><th>Status</th><th>Actions</th></tr></thead><tbody>{shown.map(u => <tr key={u.nic}><td>{u.nic}</td><td>{u.fullName}<small className="d-block text-secondary">{u.email}</small></td><td>{u.role}</td><td><StatusBadge status={u.status}/>{u.status === 'PendingActivation' && <small className="d-block text-secondary">{u.approvedAtUtc ? 'Awaiting email verification' : 'Awaiting approval'}</small>}</td><td className="d-flex flex-wrap gap-2"><button className="btn btn-sm btn-outline-secondary" onClick={()=>setAuditing(u.nic)}><Icon name="history"/>History</button>{u.role === 'Prosumer' && <button className="btn btn-sm btn-outline-primary" disabled={busy} onClick={() => { setError(''); setEditing(u); }}><Icon name="edit"/>Edit</button>}{u.status !== 'Deactivated' && <button className="btn btn-sm btn-outline-danger" disabled={busy} onClick={() => { setError(''); setDeactivating(u); }}><Icon name="power"/>Deactivate</button>}{u.status !== 'Active' && <button className="btn btn-sm btn-outline-success" disabled={busy} onClick={() => statusChange(u, 'activate')}>{approvalLabel(u)}</button>}</td></tr>)}</tbody></table>{!busy && !shown.length && <p className="text-secondary mb-0">No matching accounts.</p>}</div></section>
  </div></HomePage>;
}
