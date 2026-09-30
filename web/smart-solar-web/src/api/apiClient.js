const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '');

if (!API_BASE_URL) {
  throw new Error('VITE_API_BASE_URL is not configured.');
}

export async function apiFetch(path, options = {}) {
  const token =
    path.startsWith('/auth/')
      ? null
      : sessionStorage.getItem('accessToken');

  const headers = new Headers(options.headers ?? {});

  if (!headers.has('Content-Type') && options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  // Always replace any caller-provided Authorization header with
  // the current authenticated session token.
  headers.delete('Authorization');

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  let response;
  try { response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers
  }); } catch (cause) {
    if (cause.name === 'AbortError') throw cause;
    const error = new Error('Service unavailable. Check your connection and retry.');
    error.unavailable = true;
    window.dispatchEvent(new Event('service-unavailable'));
    throw error;
  }
  if (response.status >= 500) window.dispatchEvent(new Event('service-unavailable'));
  else if (response.ok) window.dispatchEvent(new Event('service-restored'));
  if (response.ok && options.responseType === 'blob') return response.blob();

  /*
   * Clear/expire the current session before parsing the response body.
   * Ignore delayed 401 responses belonging to an older session.
   */
  if (
    response.status === 401 &&
    token &&
    token === sessionStorage.getItem('accessToken')
  ) {
    window.dispatchEvent(new Event('session-expired'));
  }

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') ?? '';

  let payload;

  try {
    payload =
      contentType.includes('application/json') ||
      contentType.includes('+json')
        ? await response.json()
        : await response.text();
  } catch {
    const error = new Error(
      `The API returned an unreadable response (HTTP ${response.status}).`
    );

    error.status = response.status;
    throw error;
  }

  if (!response.ok) {
    const problem =
      payload &&
      typeof payload === 'object' &&
      !Array.isArray(payload)
        ? payload
        : {};

    const errors =
      problem.errors &&
      typeof problem.errors === 'object' &&
      !Array.isArray(problem.errors)
        ? Object.fromEntries(
            Object.entries(problem.errors).filter(
              ([, messages]) =>
                Array.isArray(messages) &&
                messages.every(
                  (message) => typeof message === 'string'
                )
            )
          )
        : {};

    const detail =
      typeof problem.detail === 'string'
        ? problem.detail
        : '';

    const title =
      typeof problem.title === 'string'
        ? problem.title
        : '';

    const validationMessage = Object.values(errors)
      .flat()
      .join(' ');

    const error = new Error(
      detail ||
        validationMessage ||
        title ||
        `Request failed with status ${response.status}.`
    );

    error.status = response.status;
    error.errors = errors;
    error.unavailable = response.status >= 500;
    const reference = problem.correlationId ?? response.headers.get('X-Correlation-ID');
    if (typeof reference === 'string' && /^[a-f0-9]{32}$/i.test(reference)) {
      error.correlationId = reference; error.message += ' Reference: ' + reference;
    }

    if (typeof problem.traceId === 'string') {
      error.traceId = problem.traceId;
    }

    throw error;
  }

  return payload;
}
