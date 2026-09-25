const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '');
if (!API_BASE_URL) throw new Error('VITE_API_BASE_URL is not configured.');

export async function apiFetch(path, options = {}) {
  const token = path === '/auth/login' ? null : sessionStorage.getItem('accessToken');
  const headers = new Headers(options.headers ?? {});
  if (!headers.has('Content-Type') && options.body) headers.set('Content-Type', 'application/json');
  headers.delete('Authorization');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  // Clear before parsing, including malformed 401 bodies; ignore responses from an older session.
  if (response.status === 401 && token && token === sessionStorage.getItem('accessToken')) {
    window.dispatchEvent(new Event('session-expired'));
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') ?? '';
  let payload;
  try {
    payload = contentType.includes('application/json') || contentType.includes('+json')
      ? await response.json() : await response.text();
  } catch {
    const error = new Error(`The API returned an unreadable response (HTTP ${response.status}).`);
    error.status = response.status;
    throw error;
  }
  if (!response.ok) {
    const validationMessages = typeof payload === 'object' && payload?.errors
      ? Object.values(payload.errors).flat().filter(Boolean).join(' ') : '';
    const error = new Error(typeof payload === 'object' && (payload?.detail || validationMessages || payload?.title)
      ? (payload.detail || validationMessages || payload.title) : `Request failed with status ${response.status}.`);
    error.status = response.status;
    throw error;
  }
  return payload;
}
