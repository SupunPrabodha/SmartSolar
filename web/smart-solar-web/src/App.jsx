import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';

import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import StationsPage from './pages/StationsPage';
import UserManagementPage from './pages/UserManagementPage';

import { ReservationLayout } from './pages/reservations/ReservationComponents';
import OperationsDashboardPage from './pages/reservations/OperationsDashboardPage';
import CurrentBookingsPage from './pages/reservations/CurrentBookingsPage';
import BookingHistoryPage from './pages/reservations/BookingHistoryPage';
import SearchBookingsPage from './pages/reservations/SearchBookingsPage';
import ReservationListPage from './pages/reservations/ReservationListPage';
import ReservationDetailsPage from './pages/reservations/ReservationDetailsPage';
import ReservationFormPage from './pages/reservations/ReservationFormPage';

import ProtectedRoute from './routes/ProtectedRoute';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/unauthorized" element={<UnauthorizedPage />} />

          {/* Shared staff workspace */}
          <Route
            path="/"
            element={
              <ProtectedRoute roles={['Backoffice', 'GridOperator']}>
                <HomePage />
              </ProtectedRoute>
            }
          />

          {/* Member 2 - Backoffice user management */}
          <Route
            path="/users"
            element={
              <ProtectedRoute roles={['Backoffice']}>
                <UserManagementPage />
              </ProtectedRoute>
            }
          />

          {/* Member 1 - Station and slot management */}
          <Route
            path="/stations"
            element={
              <ProtectedRoute roles={['Backoffice', 'GridOperator']}>
                <StationsPage />
              </ProtectedRoute>
            }
          />

          {/* Member 3 / Member 4 - GridOperator reservation operations */}
          <Route
            path="/operator/reservations"
            element={
              <ProtectedRoute roles={['GridOperator']}>
                <ReservationLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<ReservationListPage />} />
            <Route path="dashboard" element={<OperationsDashboardPage />} />
            <Route path="current" element={<CurrentBookingsPage />} />
            <Route path="history" element={<BookingHistoryPage />} />
            <Route path="search" element={<SearchBookingsPage />} />
            <Route path="new" element={<ReservationFormPage creating />} />
            <Route
              path=":reservationId"
              element={<ReservationDetailsPage />}
            />
            <Route
              path=":reservationId/edit"
              element={<ReservationFormPage />}
            />
          </Route>

          {/* Unknown routes */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}