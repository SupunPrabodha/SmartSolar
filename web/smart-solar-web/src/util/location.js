const numeric = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/;
export function validLocation(latitude, longitude) {
  const a = String(latitude ?? '').trim(), b = String(longitude ?? '').trim();
  if (!numeric.test(a) || !numeric.test(b)) return null;
  const lat = Number(a), lng = Number(b);
  return Number.isFinite(lat) && Number.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180
    ? { latitude: lat, longitude: lng } : null;
}
const pair = '([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*,\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))';
const shortMessage = 'Open the shortened link in Google Maps and paste the full browser URL, or select the location directly on the map.';
export function parseLocation(input) {
  const text = String(input ?? '').trim();
  let match = text.match(new RegExp('^' + pair + '$'));
  if (!match) {
    let url;
    try { url = new URL(text); } catch { throw new Error('Paste a coordinate pair or a full Google Maps URL.'); }
    if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) throw new Error('Use a public Google Maps URL or coordinates.');
    if (url.hostname === 'maps.app.goo.gl' || url.hostname === 'goo.gl') throw new Error(shortMessage);
    if (!/^(?:www\.|maps\.)?google\.(?:com|[a-z]{2}|com\.[a-z]{2}|co\.[a-z]{2})$/i.test(url.hostname))
      throw new Error('Paste a Google Maps URL or coordinates.');
    for (const key of ['q', 'query']) {
      const query = url.searchParams.get(key);
      if (query) { match = query.trim().match(new RegExp('^' + pair + '$')); if (match) break; }
    }
    let decoded;
    try { decoded = decodeURIComponent(url.pathname + url.search + url.hash); }
    catch { throw new Error('The Google Maps URL could not be read. Paste coordinates instead.'); }
    // Place coordinates take priority over the URL's camera center.
    if (!match) match = decoded.match(/!3d([+-]?[\d.]+)!4d([+-]?[\d.]+)/);
    if (!match) match = decoded.match(new RegExp('@' + pair + '(?:,|/|$)'));
  }
  const location = match && validLocation(match[1], match[2]);
  if (!location) throw new Error('Enter latitude from −90 to 90 and longitude from −180 to 180.');
  return location;
}
export function googleMapsUrl(latitude, longitude) {
  const value = validLocation(latitude, longitude);
  return value ? 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(value.latitude + ',' + value.longitude) : null;
}
