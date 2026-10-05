import { displayReference } from '../util/displayReference';
import { ActionLabel, SkeletonRegion } from './LoadingExperience';
import Icon from './Icon';
import { createContext, useContext, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { apiFetch } from '../api/apiClient';
import Overlay from './Overlay';
import { notify } from '../util/feedback';

const Context = createContext(null);
export function ExperienceProvider({ children }) {
  const { user, sessionRevision } = useAuth();
  const [inboxLoading, setInboxLoading] = useState(Boolean(user));
  const [avatar, setAvatar] = useState('');
  const [inbox, setInbox] = useState({ items: [], unreadCount: 0 }), [unavailable, setUnavailable] = useState(false), [error, setError] = useState('');
  useEffect(() => {
    const down = () => setUnavailable(true), up = () => setUnavailable(false);
    window.addEventListener('service-unavailable', down); window.addEventListener('service-restored', up);
    return () => { window.removeEventListener('service-unavailable', down); window.removeEventListener('service-restored', up); };
  }, []);
  useEffect(() => {
    setInbox({ items: [], unreadCount: 0 }); setError(''); setInboxLoading(Boolean(user));
    if (!user) return;
    const controller = new AbortController();
    let failed = false;
    async function load() {
      if (typeof document !== 'undefined' && document.hidden) return;
      setInboxLoading(true);
      try { const data = await apiFetch('/notifications', {signal:controller.signal}); if (!controller.signal.aborted) {setInbox(data);setError('');failed=false;} }
      catch(e) { if (!controller.signal.aborted) {setError(e.message);failed=true;} }
      finally { if (!controller.signal.aborted) setInboxLoading(false); }
    }
    load(); const timer = setInterval(()=>{if(!failed)load();}, 30000);
    window.addEventListener('focus', load); window.addEventListener('inbox-refresh', load);
    return () => { controller.abort(); clearInterval(timer); window.removeEventListener('focus', load); window.removeEventListener('inbox-refresh', load); };
  }, [user?.nic,sessionRevision]);
  useEffect(() => {
    const controller = new AbortController(); let url;
    setAvatar('');
    if (user?.avatarVersion) apiFetch('/users/me/avatar', {signal:controller.signal,responseType:'blob'}).then(blob=>{
      if(!controller.signal.aborted){url=URL.createObjectURL(blob);setAvatar(url);}
    }).catch(()=>{});
    return ()=>{controller.abort();if(url)URL.revokeObjectURL(url);};
  },[user?.nic,user?.avatarVersion,sessionRevision]);
  return <Context.Provider value={{inbox,error,unavailable,avatar,inboxLoading}}>{children}</Context.Provider>;
}
export function ExperienceTools({ onSignOut }) {
  const { inbox, unavailable, avatar } = useContext(Context);
  const { user, refreshProfile } = useAuth();
  const location = useLocation(), navigate = useNavigate();
  const [palette, setPalette] = useState(false);
  const key = 'profile-prompt:' + user.nic;
  const [prompt, setPrompt] = useState(() => !user.profileComplete && !sessionStorage.getItem(key) && location.pathname === '/');
  useEffect(() => {
    const press = e => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setPalette(x=>!x); } };
    window.addEventListener('keydown', press); return () => window.removeEventListener('keydown', press);
  }, []);
  function closePrompt() { sessionStorage.setItem(key, 'seen'); setPrompt(false); }
  return <>
    <div className="experience-toolbar">
      <button className="toolbar-search" title="Search workspace (Ctrl or Command + K)" onClick={()=>setPalette(true)}><Icon name="search"/><span>Search</span><kbd>Ctrl K</kbd></button>
      <Link className="toolbar-icon notification-trigger" to="/notifications" title="Notifications" aria-label={'Notifications, '+inbox.unreadCount+' unread'}><Icon name="bell"/>{inbox.unreadCount>0 && <span className="notification-badge">{inbox.unreadCount>99?'99+':inbox.unreadCount}</span>}</Link>
      <details className="account-disclosure" onKeyDown={e=>{if(e.key==='Escape'){e.currentTarget.open=false;e.currentTarget.querySelector('summary').focus();}}}>
        <summary aria-label="Account options" title="Account options">{avatar?<img className="shell-avatar" src={avatar} alt=""/>:<span className="shell-avatar avatar-initials" aria-hidden="true">{user.fullName.slice(0,1)}</span>}<span className="toolbar-identity"><strong>{user.fullName}</strong><small>{user.role==='GridOperator'?'Grid Operator':user.role}</small></span><Icon name="chevron"/></summary>
        <div className="account-popover"><Link to="/profile"><Icon name="profile"/>My Profile</Link><Link to="/profile#account-security"><Icon name="shield"/>Account security</Link><button onClick={onSignOut}><Icon name="logout"/>Sign out</button></div>
      </details>
    </div>
    {unavailable && <aside className="service-banner" role="status">Service unavailable. Displayed information may be out of date. <button onClick={()=>{refreshProfile();window.dispatchEvent(new Event('inbox-refresh'));}} className="btn btn-sm btn-outline-secondary">Retry connection</button></aside>}
    {palette && <CommandPalette onClose={()=>setPalette(false)}/>}
    {prompt && !user.profileComplete && <Overlay title="Make this workspace yours" onClose={closePrompt}>
      <p>Add your photo and confirm your contact details. You can continue using the workspace now.</p><div className="dialog-actions">
        <button className="btn btn-outline-secondary" onClick={closePrompt}>Skip for this session</button>
        <button className="btn btn-primary" onClick={()=>{closePrompt();navigate('/profile');}}>Complete Profile</button></div></Overlay>}
  </>;
}
function destination(item, role) {
  if (item.action === 'Reservation' && role === 'GridOperator') return '/operator/reservations/' + encodeURIComponent(item.resourceId);
  if (item.action === 'Station') return '/stations?id='+encodeURIComponent(item.resourceId || '');
  return role === 'Backoffice' && item.resourceId ? '/users?q='+encodeURIComponent(item.resourceId) : '/profile';
}
export function RecentActivity() {
  const { inbox, error, inboxLoading } = useContext(Context), {user} = useAuth();
  return <section className="surface-card activity-surface p-4 my-4"><div className="d-flex justify-content-between"><h2 className="section-icon-title"><Icon name="history"/>Recent activity</h2><Link to="/notifications">View all</Link></div>
    {error ? <p role="status">Activity could not be refreshed. <button className="text-action" onClick={()=>window.dispatchEvent(new Event('inbox-refresh'))}>Retry</button></p> :
      inboxLoading && !inbox.items.length ? <SkeletonRegion label="Loading recent activity" rows={3} /> : inbox.items.length ? <ul className="activity-list">{inbox.items.slice(0,5).map(x=><li key={x.id}><Link to={destination(x,user.role)}>{x.message}</Link><small>{new Date(x.atUtc).toLocaleString()}</small></li>)}</ul> :
      <p className="text-secondary">Your business and security updates will appear here.</p>}</section>;
}
export function InboxContents() {
  const { inbox, error, inboxLoading } = useContext(Context), {user} = useAuth();
  const [priority,setPriority] = useState(''), [unread,setUnread] = useState(false), [busy,setBusy] = useState(false);
  async function read(id) {
    setBusy(true);
    try { await apiFetch(id ? '/notifications/'+id+'/read' : '/notifications/read-all',{method:'POST'}); window.dispatchEvent(new Event('inbox-refresh')); }
    catch(e) { notify(e.message,'error','inbox-error'); } finally {setBusy(false);}
  }
  const items = inbox.items.filter(x=>(!priority || x.priority===priority) && (!unread || !x.readAtUtc));
  return <><div className="page-heading"><div><h1>Notifications</h1><p>Business and security updates for your account.</p></div><button className="btn btn-outline-secondary" disabled={busy || !inbox.unreadCount} onClick={()=>read()}><ActionLabel busy={busy}><Icon name="checkAll"/>Mark all read</ActionLabel></button></div>
    <div className="surface-card inbox-filters"><label>Priority <select className="form-select" value={priority} onChange={e=>setPriority(e.target.value)}><option value="">All priorities</option>{['High','Medium','Low'].map(x=><option key={x}>{x}</option>)}</select></label>
    <label><input type="checkbox" checked={unread} onChange={e=>setUnread(e.target.checked)}/> Unread only</label><button className="btn btn-outline-secondary" onClick={()=>window.dispatchEvent(new Event('inbox-refresh'))} aria-label="Refresh notifications" title="Refresh notifications"><Icon name="refresh"/></button></div>
    {error && <p role="alert" className="alert alert-warning">{error} Previously loaded notifications may be stale.</p>}
    <section className="surface-card p-4">{inboxLoading && !inbox.items.length && !error ? <SkeletonRegion label="Loading notifications" /> : items.length ? <ul className="notification-list">{items.map(x=><li key={x.id} className={x.readAtUtc?'':'notification-unread'}>
      <span className={'priority-pill priority-'+x.priority.toLowerCase()}>{x.priority}</span><strong className="notification-category"><Icon name={x.category==='Security'?'shield':'bell'}/>{x.category}{!x.readAtUtc && <span className="unread-label">Unread</span>}</strong><p>{x.message}</p><small>{new Date(x.atUtc).toLocaleString()}</small>
      <div className="d-flex gap-3 mt-2"><Link to={destination(x,user.role)} onClick={()=>read(x.id)}>Open</Link>{!x.readAtUtc && <button disabled={busy} className="text-action" onClick={()=>read(x.id)}>Mark read</button>}</div>
    </li>)}</ul> : <div className="polished-empty"><Icon name="bell"/><h2>You’re all caught up</h2><p>No notifications match these filters.</p></div>}</section></>;
}
export function CommandPalette({onClose}) {
  const {user} = useAuth(), navigate = useNavigate();
  const [query,setQuery] = useState(''), [hits,setHits] = useState([]), [error,setError] = useState(''), [loading,setLoading] = useState(false), [index,setIndex] = useState(0);
  const navigation = [['Home','/'],['My Profile','/profile'],['Notifications','/notifications'],['Stations','/stations'],
    ...(user.role==='Backoffice'?[['Users','/users']]:[['Reservations','/operator/reservations'],['Booking history','/operator/reservations/history']])];
  const choices = [...navigation.filter(([label])=>label.toLowerCase().includes(query.toLowerCase())).map(([label,path])=>({label,path})),...hits.map(x=>({label:(x.kind==='reservations'?displayReference(x.id):x.label)+' · '+x.kind,
    path:x.kind==='reservations'?'/operator/reservations/'+encodeURIComponent(x.id):x.kind==='users'?'/users?q='+encodeURIComponent(x.id):'/stations?id='+encodeURIComponent(x.id)}))];
  useEffect(()=>{
    const controller=new AbortController(); setHits([]);setError('');setIndex(0);
    if(query.trim().length<2){setLoading(false);return ()=>controller.abort();}
    setLoading(true);
    const timer=setTimeout(async()=>{try{const result=await apiFetch('/search?q='+encodeURIComponent(query.trim()),{signal:controller.signal});if(!controller.signal.aborted)setHits(result);}
      catch(e){if(!controller.signal.aborted)setError(e.message);}finally{if(!controller.signal.aborted)setLoading(false);}},300);
    return ()=>{clearTimeout(timer);controller.abort();};
  },[query]);
  function open(item){if(item){onClose();navigate(item.path);}}
  return <Overlay title="Search your workspace" onClose={onClose}><label className="form-label" htmlFor="global-search">Navigation, names or Prosumer NIC</label>
    <input id="global-search" className="form-control mb-3" value={query} maxLength={80} onChange={e=>setQuery(e.target.value)} role="combobox" aria-expanded="true" aria-controls="search-options" aria-activedescendant={choices[index]?'search-option-'+index:undefined}
      onKeyDown={e=>{if(e.key==='ArrowDown'){e.preventDefault();setIndex(i=>Math.min(i+1,choices.length-1));}if(e.key==='ArrowUp'){e.preventDefault();setIndex(i=>Math.max(0,i-1));}if(e.key==='Enter'){e.preventDefault();open(choices[index]);}}}/>
    <p className="small text-secondary">Enter at least 2 characters. Record matching is case-sensitive. Use Search Bookings to find a booking by its REF or STN reference.</p>
    {loading&&<SkeletonRegion label="Searching…" rows={2} />}{error&&<p role="alert">{error}</p>}
    <ul className="command-results" id="search-options" role="listbox">{choices.map((x,i)=><li role="option" aria-selected={i===index} id={'search-option-'+i} key={x.path+i}><button className={i===index?'active':''} onClick={()=>open(x)}>{x.label}</button></li>)}</ul>
    {!loading&&!choices.length&&<p>No matching results.</p>}</Overlay>;
}
export function AuditHistory({kind,id}) {
  const [auditLoading,setAuditLoading]=useState(true);
  const [items,setItems]=useState([]),[error,setError]=useState(''),[revision,setRevision]=useState(0),[filter,setFilter]=useState('');
  useEffect(()=>{const controller=new AbortController();setItems([]);setAuditLoading(true);apiFetch('/audit/'+kind+'/'+encodeURIComponent(id),{signal:controller.signal}).then(x=>{setItems(x);setError('');}).catch(e=>{if(!controller.signal.aborted)setError(e.message);}).finally(()=>{if(!controller.signal.aborted)setAuditLoading(false);});return()=>controller.abort();},[kind,id,revision]);
  return <section className="surface-card p-4 mb-4"><div className="d-flex justify-content-between"><h2 className="section-icon-title"><Icon name="history"/>Audit history</h2><button className="text-action" onClick={()=>setRevision(x=>x+1)} aria-label="Refresh audit history" title="Refresh audit history"><Icon name="refresh"/></button></div>
    <label className="form-label d-block">Filter retained events by action or actor<input className="form-control" value={filter} onChange={e=>setFilter(e.target.value)}/></label>
    {auditLoading?<SkeletonRegion label="Loading security history" rows={2}/>:error?<p role="alert">{error}</p>:items.length?<ol className="activity-list">{items.filter(x=>(x.event+' '+x.actorNic).toLowerCase().includes(filter.toLowerCase())).map(x=><li key={x.id}><strong>{x.event.replace(/([a-z])([A-Z])/g,'$1 $2')}</strong><small>{new Date(x.atUtc).toLocaleString()} · {x.actorNic}</small><small>Reference: {x.correlationId}</small></li>)}</ol>:<div className="polished-empty"><Icon name="history"/><p>No retained events yet.</p></div>}</section>;
}
export function ExportButton({kind,query=''}) {
  const [busy,setBusy]=useState(false);
  async function run(){setBusy(true);notify('Preparing export…','loading','export');try{const blob=await apiFetch('/exports/'+kind+'.csv'+(query?'?'+query:''),{responseType:'blob'});const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=kind+'.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);notify('Export downloaded.','success','export');}
    catch(e){notify(e.message,'error','export');}finally{setBusy(false);}}
  return <button type="button" className="btn btn-outline-secondary" disabled={busy} onClick={run}><Icon name="download"/><ActionLabel busy={busy} pending="Exporting…">Export CSV</ActionLabel></button>;
}
