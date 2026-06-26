import { useEffect, useState } from 'react'
import { getDevices } from '../api'

export default function DeviceList({ selectedId, onSelect }) {
  const [devices, setDevices] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    getDevices()
      .then(setDevices)
      .catch(() => setError('Could not load devices'))
  }, [])

  return (
    <aside className="panel device-list">
      <p className="panel-title">Devices</p>
      {error && <p className="muted">{error}</p>}
      {!error && devices.length === 0 && <p className="muted">No devices registered.</p>}
      {devices.map(d => (
        <button
          key={d.device_id}
          className={`device-card${d.device_id === selectedId ? ' active' : ''}`}
          onClick={() => onSelect(d)}
        >
          <span className="device-name">{d.name}</span>
          <span className="device-location">{d.location}</span>
          {d.sensors?.length > 0 && (
            <div className="device-sensors">
              {d.sensors.map(s => <span key={s} className="sensor-tag">{s}</span>)}
            </div>
          )}
        </button>
      ))}
    </aside>
  )
}
