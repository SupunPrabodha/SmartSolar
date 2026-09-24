import { useCallback, useEffect, useRef, useState } from 'react';

// Abort obsolete reads and hide data belonging to a previous ID/filter set.
export function useReservationData(load) {
  const [version, setVersion] = useState(0);
  const [state, setState] = useState({ load: null, data: null, error: null, loading: true });
  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    setState({ load, data: null, error: null, loading: true });
    Promise.resolve().then(() => load(controller.signal)).then(
      data => { if (current) setState({ load, data, error: null, loading: false }); },
      error => { if (current && error.name !== 'AbortError') setState({ load, data: null, error, loading: false }); }
    );
    return () => { current = false; controller.abort(); };
  }, [load, version]);
  const reload = useCallback(() => setVersion(value => value + 1), []);
  const replace = useCallback(data => setState({ load, data, error: null, loading: false }), [load]);
  return { ...(state.load === load ? state : { data: null, error: null, loading: true }), reload, replace };
}

// Do not abort or automatically retry writes: a disconnected request may still commit.
export function useReservationMutation() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState(null);
  const busy = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  async function run(action, success) {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError(null);
    try {
      const result = await action();
      if (mounted.current) success(result);
    } catch (failure) {
      if (mounted.current) setError(failure);
    } finally {
      busy.current = false;
      if (mounted.current) setPending(false);
    }
  }
  return { pending, error, run, clearError: () => setError(null) };
}
