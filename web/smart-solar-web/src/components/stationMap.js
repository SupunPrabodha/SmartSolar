import { validLocation } from '../util/location.js';
/** Leaflet adapter. No geocoding, URL fetching, credentials or persisted map state. */
export function createStationMap(L, element, { onSelect, onTileError }) {
  const map = L.map(element, { scrollWheelZoom: false, worldCopyJump: true });
  let marker = null, disabled = false, selected = null;
  const tile = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  }).addTo(map);
  tile.on('tileerror', onTileError);
  const icon = L.divIcon({ className: 'station-map-pin', html: '<span aria-hidden="true"></span>', iconSize: [28, 36], iconAnchor: [14, 36] });
  const select = point => {
    if (!onSelect || disabled) return;
    const longitude = point.lng >= -180 && point.lng <= 180 ? point.lng : ((point.lng + 180) % 360 + 360) % 360 - 180;
    const value = validLocation(point.lat, longitude);
    if (value) onSelect(value);
  };
  map.on('click', e => select(e.latlng));
  map.fitWorld();
  return {
    update(location, locked = false) {
      disabled = locked;
      const value = location && validLocation(location.latitude, location.longitude);
      if (!value) {
        if (marker) { map.removeLayer(marker); marker = null; }
        if (selected) map.fitWorld();
        selected = null;
        return;
      }
      const point = [value.latitude, value.longitude];
      if (!marker) {
        marker = L.marker(point, { icon, draggable: !!onSelect && !disabled, title: 'Selected station location', alt: 'Selected station location' }).addTo(map);
        marker.on('dragend', () => select(marker.getLatLng()));
      } else marker.setLatLng(point);
      if (onSelect && !disabled) marker.dragging?.enable(); else marker.dragging?.disable();
      if (!selected || selected.latitude !== value.latitude || selected.longitude !== value.longitude)
        map.setView(point, 15, { animate: false });
      selected = value;
    },
    resize() { map.invalidateSize({ pan: false }); if (selected) map.panTo([selected.latitude, selected.longitude], { animate: false }); },
    destroy() { map.remove(); }
  };
}
