import { useEffect, useMemo, useState } from 'react'
import { getDevices, getAlerts } from '../api'
import RegisterDevice from './RegisterDevice'
import EditDevice from './EditDevice'

// Fixed demo plant order (NFL Recon §2.3 cross-plant narrative); unlisted
// locations (e.g. legacy non-NFL devices) sort after, alphabetically.
const PLANT_ORDER = ['SITE Karachi Plant', 'Faisalabad Plant', 'Sharjah Plant', 'Kunri (sourced by Faisalabad)']

function groupByPlant(devices) {
  const groups = new Map()
  for (const d of devices) {
    const key = d.location || 'Unknown'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(d)
  }
  return [...groups.entries()].sort(([a], [b]) => {
    const ia = PLANT_ORDER.indexOf(a)
    const ib = PLANT_ORDER.indexOf(b)
    if (ia === -1 && ib === -1) return a.localeCompare(b)
    if (ia === -1) return 1
    if (ib === -1) return -1
    return ia - ib
  })
}

export default function DeviceList({ selectedId, onSelect }) {
  const [devices, setDevices]       = useState([])
  const [error, setError]           = useState(null)
  const [showRegister, setShowRegister] = useState(false)
  const [editDevice, setEditDevice] = useState(null)
  const [alertsToday, setAlertsToday] = useState(0)

  const load = () => {
    getDevices()
      .then(setDevices)
      .catch(() => setError('Could not load devices'))
  }

  useEffect(() => { load() }, [])

  // Cross-plant summary strip data — reuses the existing per-device alerts
  // endpoint (no new backend endpoint this phase).
  useEffect(() => {
    if (devices.length === 0) { setAlertsToday(0); return }
    const cutoffMs = new Date().setHours(0, 0, 0, 0)
    Promise.all(devices.map(d => getAlerts(d.device_id, 100).catch(() => [])))
      .then(results => setAlertsToday(results.flat().filter(a => a.ts >= cutoffMs).length))
  }, [devices])

  const plantGroups = useMemo(() => groupByPlant(devices), [devices])

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

      {devices.length > 0 && (
        <p className="plant-summary-strip">
          {plantGroups.length} plants · {devices.length} assets · {alertsToday} active alerts today
        </p>
      )}

      {error && <p className="muted">{error}</p>}
      {!error && devices.length === 0 && <p className="muted">No devices registered.</p>}

      {plantGroups.map(([plant, plantDevices]) => (
        <div key={plant} className="plant-group">
          <p className="plant-group-header">{plant} · {plantDevices.length} device{plantDevices.length === 1 ? '' : 's'}</p>

          {plantDevices.map(d => (
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
                    {d.source === 'hardware' ? 'HW' : 'SIMULATED'}
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
