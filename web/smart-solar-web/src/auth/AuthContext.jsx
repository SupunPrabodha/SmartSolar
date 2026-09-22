import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { apiFetch } from '../api/apiClient';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const [lastVerifiedAt, setLastVerifiedAt] = useState(null);
  const generation = useRef(0);

  const logout = useCallback(() => {
    generation.current += 1;
    ['accessToken', 'currentUser', 'expiresAtUtc'].forEach(key => sessionStorage.removeItem(key));
    setUser(null);
    setSessionError('');
    setLastVerifiedAt(null);
    setRefreshing(false);
  }, []);

  const refreshProfile = useCallback(async () => {
    const token = sessionStorage.getItem('accessToken');
    const expiry = Date.parse(sessionStorage.getItem('expiresAtUtc'));
    if (!token || !Number.isFinite(expiry) || expiry <= Date.now()) { logout(); return; }
    const requestGeneration = ++generation.current;
    setRefreshing(true);
    setSessionError('');
    try {
      const profile = await apiFetch('/users/me');
      // An older request must not resurrect a logged-out or replaced session.
      if (requestGeneration !== generation.current || token !== sessionStorage.getItem('accessToken')) return;
      if (Date.parse(sessionStorage.getItem('expiresAtUtc')) <= Date.now() || profile?.status !== 'Active') {
        logout(); return;
      }
      setUser(profile);
      setLastVerifiedAt(new Date());
    } catch (error) {
      if (requestGeneration === generation.current && error.status !== 401) {
        setSessionError('Unable to verify your profile. Check the API connection and try again.');
      }
    } finally {
      if (requestGeneration === generation.current) setRefreshing(false);
    }
  }, [logout]);

  useEffect(() => {
    let active = true;
    window.addEventListener('session-expired', logout);
    refreshProfile().finally(() => { if (active) setLoading(false); });
    return () => {
      active = false;
      generation.current += 1;
      window.removeEventListener('session-expired', logout);
    };
  }, [logout, refreshProfile]);

  useEffect(() => {
    if (!user) return undefined;
    const remaining = Date.parse(sessionStorage.getItem('expiresAtUtc')) - Date.now();
    const timer = setTimeout(logout, Math.max(0, Number.isFinite(remaining) ? remaining : 0));
    return () => clearTimeout(timer);
  }, [user, logout]);

  async function login(nic, password) {
    logout();
    const requestGeneration = generation.current;
    const result = await apiFetch('/auth/login', {
      method: 'POST', body: JSON.stringify({ nic: nic.trim(), password })
    });
    if (requestGeneration !== generation.current) return;
    if (!result?.accessToken || !(Date.parse(result.expiresAtUtc) > Date.now()) || result.user?.status !== 'Active') {
      throw new Error('The API did not return a valid active session.');
    }
    sessionStorage.setItem('accessToken', result.accessToken);
    sessionStorage.setItem('expiresAtUtc', result.expiresAtUtc);
    setUser(result.user);
    setLastVerifiedAt(new Date());
    return result.user;
  }

  return <AuthContext.Provider value={{
    user, loading, refreshing, sessionError, lastVerifiedAt, login, logout, refreshProfile
  }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider.');
  return context;
}
