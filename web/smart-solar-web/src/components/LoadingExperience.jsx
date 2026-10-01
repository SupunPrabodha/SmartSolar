import Brand from './Brand';

// Presentation only: callers own request timing, errors and retries.
export function BootScreen() {
  return <main className="solar-boot" aria-busy="true">
    <div className="solar-boot-card"><Brand />
      <p role="status">Restoring your workspace</p>
      <div className="boot-track" aria-hidden="true"><span /></div>
      <small>Secure energy operations</small>
    </div>
  </main>;
}
export function MetricSkeleton() {
  return <span className="metric-placeholder" aria-hidden="true"><span className="skeleton skeleton-number" /><span className="skeleton skeleton-caption" /></span>;
}
export function MetricLoadingGrid({ labels }) {
  return <section className="metrics-grid metrics-pair" aria-busy="true" aria-label="Reservation metrics">
    <span className="visually-hidden" role="status">Loading dashboard</span>
    {labels.map(label => <article className="surface-card metric-card" key={label}><div><p className="card-label">{label}</p><MetricSkeleton /></div></article>)}
  </section>;
}
export function SkeletonRegion({ label = 'Loading your workspace…', rows = 3 }) {
  return <div className="skeleton-region" aria-busy="true" aria-label={label}>
    <span className="loading-caption" role="status">{label}</span>
    <div aria-hidden="true">{Array.from({length:rows}, (_, i) => <div className="skeleton-row" key={i}>
      <span className="skeleton skeleton-icon" /><div><span className="skeleton skeleton-title" /><span className="skeleton skeleton-line" /><span className="skeleton skeleton-caption" /></div>
    </div>)}</div>
  </div>;
}
export function ActionLabel({ busy, pending = 'Updating…', children }) {
  return busy ? <><span className="action-progress" aria-hidden="true" />{pending}</> : children;
}
