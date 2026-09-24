import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import HomePage from './pages/HomePage';
import ReservationPlaceholderPage from './pages/reservations/ReservationPlaceholderPage';
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
            <ProtectedRoute roles={['GridOperator']}><Outlet /></ProtectedRoute>
          )}>
            <Route index element={<ReservationPlaceholderPage mode="list" />} />
            <Route path="new" element={<ReservationPlaceholderPage mode="create" />} />
            <Route path=":reservationId" element={<ReservationPlaceholderPage mode="details" />} />
            <Route path=":reservationId/edit" element={<ReservationPlaceholderPage mode="edit" />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
