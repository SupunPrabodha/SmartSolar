// Presentation only. Canonical GUID -> FNV-1a 64-bit -> ten base-36 characters.
// Keep synchronized with Android DisplayReference; never use these values as API IDs.
const prefixes = { reservation: 'REF', station: 'STN', slot: 'SLOT' };
export function displayReference(id, kind = 'reservation') {
  if (typeof id !== 'string' || !prefixes[kind]) return 'Unavailable';
  const value = id.trim().toLowerCase();
  if (!/^(?:[a-f0-9]{32}|[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})$/.test(value)) return 'Unavailable';
  const canonical = value.replaceAll('-', '');
  if (/^0+$/.test(canonical)) return 'Unavailable';
  let hash = 14695981039346656037n;
  for (const char of canonical) hash = BigInt.asUintN(64, (hash ^ BigInt(char.charCodeAt(0))) * 1099511628211n);
  return prefixes[kind] + '-' + (hash % 3656158440062976n).toString(36).toUpperCase().padStart(10, '0');
}
