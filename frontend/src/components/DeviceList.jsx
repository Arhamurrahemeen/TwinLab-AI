import { useEffect, useState } from 'react'
import { getDevices } from '../api'
import RegisterDevice from './RegisterDevice'
import EditDevice from './EditDevice'

export default function DeviceList({ selectedId, onSelect }) {
  const [devices, setDevices]       = useState([])
  const [error, setError]           = useState(null)
  const [showRegister, setShowRegister] = useState(false)
  const [editDevice, setEditDevice] = useState(null)

  const load = () => {
    getDevices()
      .then(setDevices)
      .catch(() => setError('Could not load devices'))
  }

  useEffect(() => { load() }, [])

  const handleUpdated = (updated) => {
    setEditDevice(null)
    load()
    if (updated.device_id === selectedId) onSelect(updated)
  }

  return (
    <aside className="panel device-list">
      <div className="panel-title-row">
        <p className="panel-title">Devices</p>
        <button className="add-btn" onClick={() => setShowRegister(true)} title="Register device">+</button>
      </div>

      {error && <p className="muted">{error}</p>}
      {!error && devices.length === 0 && <p className="muted">No devices registered.</p>}

      {devices.map(d => (
        <div
          key={d.device_id}
          className={`device-card${d.device_id === selectedId ? ' active' : ''}`}
          onClick={() => onSelect(d)}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && onSelect(d)}
        >
          <div className="device-card-top">
            <span className="device-name">{d.name}</span>
            <div className="device-card-actions">
              <span className={`source-badge source-badge--${d.source ?? 'simulator'}`}>
                {d.source === 'hardware' ? 'HW' : 'SIM'}
              </span>
              <button
                className="edit-btn"
                title="Edit device"
                onClick={(e) => { e.stopPropagation(); setEditDevice(d) }}
              >
                ✎
              </button>
            </div>
          </div>
          <span className="device-location">{d.location}</span>
          {d.status && d.status !== 'active' && (
            <span className="status-inactive">inactive</span>
          )}
          {d.sensors?.length > 0 && (
            <div className="device-sensors">
              {d.sensors.map(s => <span key={s} className="sensor-tag">{s}</span>)}
            </div>
          )}
        </div>
      ))}

      {showRegister && (
        <RegisterDevice
          onCreated={() => { setShowRegister(false); load() }}
          onClose={() => setShowRegister(false)}
        />
      )}

      {editDevice && (
        <EditDevice
          device={editDevice}
          onUpdated={handleUpdated}
          onClose={() => setEditDevice(null)}
        />
      )}
    </aside>
  )
}
