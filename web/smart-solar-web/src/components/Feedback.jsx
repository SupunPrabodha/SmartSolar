import { useEffect, useState } from 'react';
import Icon from './Icon';
import { notify, dismiss, subscribeFeedback, durations } from '../util/feedback';

// Compatibility adapter: all existing mutation feedback uses the same bounded host.
export function Toast({ message }) {
  useEffect(() => { if (message) notify(message); }, [message]);
  return null;
}
export function FeedbackHost() {
  const [items, setItems] = useState([]);
  useEffect(() => subscribeFeedback(setItems), []);
  return <section className="feedback-stack" aria-label="Action feedback">{items.map(item => <FeedbackItem key={item.id + item.sequence} item={item} />)}</section>;
}
function FeedbackItem({ item }) {
  const [paused, setPaused] = useState(false);
  useEffect(() => {
    if (paused || !durations[item.type]) return;
    const timer = setTimeout(() => dismiss(item.id), durations[item.type]);
    return () => clearTimeout(timer);
  }, [paused, item.id, item.type]);
  return <div className={'enterprise-toast toast-' + item.type} role={item.type === 'error' ? 'alert' : 'status'}
    onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
    onFocus={() => setPaused(true)} onBlur={() => setPaused(false)}>
    <strong><span aria-hidden="true">{{success:'✓',info:'i',warning:'!',error:'×',loading:'…'}[item.type]}</span> {item.type === 'loading' ? 'Working' : item.type}</strong><span>{item.message}</span>
    <button aria-label="Dismiss feedback" onClick={() => dismiss(item.id)}>×</button>
  </div>;
}
export function LoadingState({ label = 'Loading your workspace…' }) {
  return <div className="loading-state" role="status"><span className="loading-orbit" aria-hidden="true" /><span>{label}</span></div>;
}
export function EmptyState({ title = 'Nothing here yet', children }) {
  return <div className="empty-state"><Icon name="station" /><h2>{title}</h2><p>{children}</p></div>;
}
