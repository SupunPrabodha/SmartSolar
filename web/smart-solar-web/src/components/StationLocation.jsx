import { useEffect, useId, useRef, useState } from 'react';
import { validLocation, parseLocation, googleMapsUrl } from '../util/location';
import { createStationMap } from './stationMap';

export function StationMap({ latitude, longitude, onChange, disabled = false }) {
  const host = useRef(null), map = useRef(null), latest = useRef({ latitude, longitude, onChange, disabled });
  latest.current = { latitude, longitude, onChange, disabled };
  const [error, setError] = useState('');
  const id = useId();
  useEffect(() => {
    let disposed = false, observer;
    import('leaflet').then(module => {
      if (disposed) return;
      try {
        map.current = createStationMap(module.default ?? module, host.current, {
          onSelect: onChange ? value => { if (!latest.current.disabled) latest.current.onChange?.(value); } : null,
          onTileError: () => { if (!disposed) setError('Map tiles are unavailable. The selected coordinates are retained; you can still enter them manually.'); }
        });
        map.current.update(validLocation(latest.current.latitude, latest.current.longitude), latest.current.disabled);
        if (typeof ResizeObserver !== 'undefined') {
          observer = new ResizeObserver(() => map.current?.resize());
          observer.observe(host.current);
        }
      } catch { map.current?.destroy(); map.current = null; setError('Map unavailable. Enter latitude and longitude manually.'); }
    }).catch(() => { if (!disposed) setError('Map unavailable. Enter latitude and longitude manually.'); });
    return () => { disposed = true; observer?.disconnect(); map.current?.destroy(); map.current = null; };
  }, []);
  useEffect(() => { map.current?.update(validLocation(latitude, longitude), disabled); }, [latitude, longitude, disabled]);
  const location = validLocation(latitude, longitude);
  return <div className="station-location-map">
    <p id={id} className="small text-secondary">{onChange ? 'Click the map or drag the marker. Keyboard users can use the latitude and longitude fields.' : 'Stored station location.'}</p>
    <div ref={host} className="station-map-canvas" role="region" aria-label="Station location map" aria-describedby={id}/>
    <p className="small mt-2 mb-0">{location ? 'Selected: ' + location.latitude.toFixed(6) + ', ' + location.longitude.toFixed(6) : 'No location selected. The map shows a world overview.'}</p>
    {error && <p className="small text-secondary" role="status">{error}</p>}
  </div>;
}
export default function StationLocation({ latitude, longitude, onChange, disabled = false }) {
  const [input, setInput] = useState(''), [feedback, setFeedback] = useState('');
  const id = useId();
  function apply() {
    try { onChange(parseLocation(input)); setFeedback('Location applied. Check the selected point before saving.'); }
    catch (error) { setFeedback(error.message); }
  }
  return <section className="station-location-editor">
    <label htmlFor={id} className="form-label">Google Maps link or coordinates</label>
    <div className="station-location-helper"><input id={id} className="form-control" value={input} disabled={disabled} onChange={event => setInput(event.target.value)} placeholder="6.9271,79.8612" />
      <button type="button" className="btn btn-outline-primary" disabled={disabled} onClick={apply}>Use location</button></div>
    {feedback && <p className="small mt-2" role="status">{feedback}</p>}
    <StationMap latitude={latitude} longitude={longitude} onChange={onChange} disabled={disabled}/>
  </section>;
}
export function OpenStationMap({ latitude, longitude }) {
  const href = googleMapsUrl(latitude, longitude);
  return href ? <a className="btn btn-outline-primary" href={href} target="_blank" rel="noopener noreferrer">Open in Google Maps</a> : null;
}
