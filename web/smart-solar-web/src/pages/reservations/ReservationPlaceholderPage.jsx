import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import Brand from '../../components/Brand';

const pages = {
  list: ['Reservations', 'Reservation management is coming soon.'],
  create: ['New reservation', 'Assisted reservation creation is coming soon.'],
  details: ['Reservation details', 'Reservation details are not available on this page yet.'],
  edit: ['Modify reservation', 'Reservation editing is coming soon.']
};

export default function ReservationPlaceholderPage({ mode }) {
  const { user, logout } = useAuth();
  const [title, message] = pages[mode];
  return <main className="container py-4" style={{ maxWidth: 960 }}>
    <header className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
      <Brand />
      <div className="d-flex align-items-center gap-3">
        <span>{user.fullName}</span>
        <button className="btn btn-outline-secondary btn-sm" onClick={logout}>Sign out</button>
      </div>
    </header>
    <nav className="d-flex flex-wrap gap-3 mb-4" aria-label="Reservation navigation">
      <Link to="/">Back to workspace</Link>
      <Link to="/operator/reservations">Reservations</Link>
      <Link to="/operator/reservations/new">New reservation</Link>
    </nav>
    <section className="surface-card" aria-labelledby="reservation-page-title">
      <p className="eyebrow">GRID OPERATOR</p>
      <h1 id="reservation-page-title" className="h3">{title}</h1>
      <p className="text-secondary mb-0">{message}</p>
    </section>
  </main>;
}
