import { useEffect, useState } from 'react';
import { apiFetch } from '../api/apiClient';
import { displayReference } from '../util/displayReference';
// View-local lookup: no cached station identity can cross an account/session change.
export function useStationNames() {
  const [names, setNames] = useState({});
  useEffect(() => {
    const controller = new AbortController();
    apiFetch('/stations?includeInactive=true', {signal:controller.signal}).then(rows => {
      if (!controller.signal.aborted && Array.isArray(rows))
        setNames(Object.fromEntries(rows.filter(row => row.stationId && row.name).map(row => [row.stationId,row.name])));
    }).catch(() => {}); // Readable STN reference remains when metadata cannot be loaded.
    return () => controller.abort();
  }, []);
  return names;
}
export default function StationCaption({ id, name }) {
  return <span>{name || 'Station'}<small className="d-block text-secondary display-reference">{displayReference(id,'station')}</small></span>;
}
