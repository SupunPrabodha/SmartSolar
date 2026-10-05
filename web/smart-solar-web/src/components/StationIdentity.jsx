import Icon from './Icon';
import { displayReference } from '../util/displayReference';
export default function StationIdentity({ station, preview = false }) {
  return <div className="station-identity">
    <span className="station-avatar" aria-hidden="true"><Icon name="station"/></span>
    <div><h2 className="h5 mb-1">{station.name?.trim() || (preview ? 'Station preview' : 'Station')}</h2>
      {station.stationId && <small className="d-block text-secondary display-reference">{displayReference(station.stationId, 'station')}</small>}
    </div>
  </div>;
}
