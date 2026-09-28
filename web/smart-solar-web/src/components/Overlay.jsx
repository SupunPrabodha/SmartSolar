import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';

// Native modal top-layer supplies focus containment and an inert background.
export default function Overlay({ title, children, onClose, busy = false, wide = false }) {
  const dialog = useRef(null), heading = useRef(null), titleId = useId();
  useEffect(() => {
    const node = dialog.current, trigger = document.activeElement;
    node.showModal();
    heading.current?.focus();
    return () => {
      node.close();
      if (trigger?.isConnected) trigger.focus({ preventScroll: true });
      else document.getElementById('main')?.focus({ preventScroll: true });
    };
  }, []);
  return createPortal(<dialog ref={dialog} className={'solar-dialog' + (wide ? ' solar-dialog-wide' : '')}
    aria-labelledby={titleId} aria-busy={busy}
    onKeyDown={event => {
      if (event.key !== 'Tab') return;
      const controls = [...dialog.current.querySelectorAll('button, input, select, textarea, a[href], [tabindex="0"]')]
        .filter(el => !el.matches(':disabled') && el.getClientRects().length);
      const first = controls[0], last = controls[controls.length - 1];
      if (!first) { event.preventDefault(); heading.current?.focus(); return; }
      if (event.shiftKey && (document.activeElement === first || document.activeElement === heading.current)) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus();
      }
    }}
    onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <header className="dialog-heading"><div><p className="eyebrow">SMART SOLAR</p>
      <h2 id={titleId} ref={heading} tabIndex="-1">{title}</h2></div>
      <button type="button" className="btn btn-outline-secondary icon-button" aria-label="Close dialog" disabled={busy} onClick={onClose}>×</button>
    </header>
    <div className="dialog-content">{children}</div>
  </dialog>, document.body);
}

export function ConfirmDialog({ title, children, onConfirm, onClose, busy = false, action = 'Confirm', danger = true }) {
  return <Overlay title={title} onClose={onClose} busy={busy}>
    <span className={'confirmation-symbol' + (danger ? ' destructive' : '')} aria-hidden="true">{danger ? '!' : '↗'}</span>
    <div className="mb-4">{children}</div>
    <div className="dialog-actions"><button type="button" autoFocus className="btn btn-outline-secondary" disabled={busy} onClick={onClose}>Go back</button>
      <button type="button" className={danger ? 'btn btn-danger' : 'btn btn-primary'} disabled={busy} onClick={onConfirm}>{busy ? 'Please wait…' : action}</button></div>
  </Overlay>;
}
