import { useCallback, useEffect, useState } from 'react'
import { getAnomalies } from '../api'

export default function AlertsPanel({ device }) {
  const [anomalies, setAnomalies] = useState([])
  const [scanning, setScanning] = useState(false)

  const scan = useCallback(async () => {
    if (!device) return
    setScanning(true)
    try {
      const results = await Promise.all(
        (device.sensors ?? []).map(s => getAnomalies(device.device_id, s).catch(() => []))
      )
      const flagged = results
        .flat()
        .filter(r => r.is_anomaly)
        .sort((a, b) => new Date(b.ts) - new Date(a.ts))
      setAnomalies(flagged)
    } finally {
      setScanning(false)
    }
  }, [device])

  useEffect(() => {
    setAnomalies([])
    scan()
    const id = setInterval(scan, 30_000)
    return () => clearInterval(id)
  }, [scan])

  return (
    <aside className="panel alerts-panel">
      <div className="panel-title-row">
        <p className="panel-title">Alerts</p>
        {anomalies.length > 0 && (
          <span className="alert-badge">{anomalies.length}</span>
        )}
      </div>

      {!device && <p className="muted">Select a device.</p>}

      {device && scanning && anomalies.length === 0 && (
        <p className="muted">Scanning…</p>
      )}

      {device && !scanning && anomalies.length === 0 && (
        <p className="muted ok-text">No anomalies detected.</p>
      )}

      {anomalies.map((a, i) => (
        <div key={i} className="alert-item">
          <span className="alert-sensor">{a.sensor.replace('_', ' ')}</span>
          <span className="alert-value">{a.value.toFixed(4)} <span className="alert-unit">{a.unit}</span></span>
          <span className="alert-ts">{new Date(a.ts).toLocaleTimeString()}</span>
        </div>
      ))}
    </aside>
  )
}
