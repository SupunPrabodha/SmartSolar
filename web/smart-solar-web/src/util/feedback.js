let listeners = new Set();
let items = [];
let sequence = 0;
export const durations = { success: 4500, info: 5500, warning: 7000, error: 9000, loading: 0 };
export function notify(message, type = 'success', id = message, title) {
  if (items.some(x => x.id === id && x.message === message && x.type === type && x.title === title)) return id;
  const item = { id, message, title, type: Object.hasOwn(durations, type) ? type : 'info', sequence: ++sequence };
  items = [...items.filter(x => x.id !== id), item].slice(-3);
  listeners.forEach(fn => fn(items));
  return id;
}
export function dismiss(id) { items = items.filter(x => x.id !== id); listeners.forEach(fn => fn(items)); }
export function clearFeedback() { items = []; listeners.forEach(fn => fn(items)); }
export function subscribeFeedback(fn) { listeners.add(fn); fn(items); return () => listeners.delete(fn); }
