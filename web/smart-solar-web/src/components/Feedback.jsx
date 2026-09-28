import { useState } from 'react';
import Icon from './Icon';

// Feedback remains until explicitly dismissed; no short reading-time deadline.
export function Toast({ message }) {
  const [dismissed, setDismissed] = useState('');
  if (!message || dismissed === message) return null;
  return <div className="solar-toast" role="status"><Icon name="check" /><span>{message}</span>
    <button type="button" aria-label="Dismiss notification" onClick={() => setDismissed(message)}>×</button></div>;
}
export function LoadingState({ label = 'Loading your workspace…' }) {
  return <div className="loading-state" role="status"><span className="loading-orbit" aria-hidden="true" /><span>{label}</span></div>;
}
export function EmptyState({ title = 'Nothing here yet', children }) {
  return <div className="empty-state"><Icon name="station" /><h2>{title}</h2><p>{children}</p></div>;
}
