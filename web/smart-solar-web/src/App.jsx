import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import HomePage from './pages/HomePage';
import { ReservationLayout } from './pages/reservations/ReservationComponents';
import OperationsDashboardPage from './pages/reservations/OperationsDashboardPage';
import CurrentBookingsPage from './pages/reservations/CurrentBookingsPage';
import PendingBookingsPage from './pages/reservations/PendingBookingsPage';
import BookingHistoryPage from './pages/reservations/BookingHistoryPage';
import SearchBookingsPage from './pages/reservations/SearchBookingsPage';
import ReservationListPage from './pages/reservations/ReservationListPage';
import ReservationDetailsPage from './pages/reservations/ReservationDetailsPage';
import ReservationFormPage from './pages/reservations/ReservationFormPage';
import LoginPage from './pages/LoginPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import ProtectedRoute from './routes/ProtectedRoute';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/unauthorized" element={<UnauthorizedPage />} />
          <Route
            path="/"
            element={(
              <ProtectedRoute roles={['Backoffice', 'GridOperator']}>
                <HomePage />
              </ProtectedRoute>
            )}
          />
          <Route path="/operator/reservations" element={(
            <ProtectedRoute roles={['GridOperator']}><ReservationLayout /></ProtectedRoute>
          )}>
            <Route index element={<ReservationListPage />} />
            <Route path="dashboard" element={<OperationsDashboardPage />} />
            <Route path="current" element={<CurrentBookingsPage />} />
            <Route path="pending" element={<PendingBookingsPage />} />
            <Route path="history" element={<BookingHistoryPage />} />
            <Route path="search" element={<SearchBookingsPage />} />
            <Route path="new" element={<ReservationFormPage creating />} />
            <Route path=":reservationId" element={<ReservationDetailsPage />} />
            <Route path=":reservationId/edit" element={<ReservationFormPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

