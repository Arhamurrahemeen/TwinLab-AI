import { useMemo, useState } from 'react'

const BASE = '/api'

export default function RegisterDevice({ onCreated, onClose }) {
  const [form, setForm] = useState({
    device_id: '',
    name: '',
    location: '',
    sensors: '',
    source: 'simulator',
  })
  const [thresholds, setThresholds] = useState({})
  const [error, setError]   = useState('')
  const [saving, setSaving] = useState(false)

  const set = (field) => (e) => setForm(f => ({ ...f, [field]: e.target.value }))

  const sensorList = useMemo(
    () => form.sensors.split(',').map(s => s.trim()).filter(Boolean),
    [form.sensors],
  )

  const setThreshold = (sensor, bound, raw) => {
    setThresholds(prev => ({
      ...prev,
      [sensor]: { ...prev[sensor], [bound]: raw },
    }))
  }

  const buildThresholds = () => {
    const result = {}
    for (const sensor of sensorList) {
      const t   = thresholds[sensor] ?? {}
      const min = t.min !== '' && t.min !== undefined ? parseFloat(t.min) : null
      const max = t.max !== '' && t.max !== undefined ? parseFloat(t.max) : null
      const minVal = (min !== null && !isNaN(min)) ? min : null
      const maxVal = (max !== null && !isNaN(max)) ? max : null
      if (minVal !== null || maxVal !== null) {
        result[sensor] = { min: minVal, max: maxVal }
      }
    }
    return result
  }

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    if (!form.device_id.trim() || !form.name.trim()) {
      setError('Device ID and Name are required.')
      return
    }
    setSaving(true)
    try {
      const res = await fetch(`${BASE}/devices`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          device_id:  form.device_id.trim(),
          name:       form.name.trim(),
          location:   form.location.trim(),
          sensors:    sensorList,
          source:     form.source,
          thresholds: buildThresholds(),
        }),
      })
      if (!res.ok) {
        const data = await res.json()
        setError(data.detail ?? 'Registration failed.')
        return
      }
      onCreated()
    } catch {
      setError('Network error.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <span className="modal-title">Register Device</span>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <form className="modal-form" onSubmit={submit}>
          <label className="field-label">Device ID *</label>
          <input
            className="field-input"
            placeholder="e.g. shell-mpx-gen-1"
            value={form.device_id}
            onChange={set('device_id')}
          />

          <label className="field-label">Name *</label>
          <input
            className="field-input"
            placeholder="e.g. Shell Mirpurkhas — Generator 1"
            value={form.name}
            onChange={set('name')}
          />

          <label className="field-label">Location</label>
          <input
            className="field-input"
            placeholder="e.g. Mirpurkhas"
            value={form.location}
            onChange={set('location')}
          />

          <label className="field-label">Source</label>
          <select className="field-input" value={form.source} onChange={set('source')}>
            <option value="simulator">Simulator</option>
            <option value="hardware">Hardware (ESP32)</option>
          </select>

          <label className="field-label">
            Sensors <span className="field-hint">(comma-separated)</span>
          </label>
          <input
            className="field-input"
            placeholder="temperature, humidity, fuel_level"
            value={form.sensors}
            onChange={set('sensors')}
          />

          {sensorList.length > 0 && (
            <>
              <label className="field-label">
                Thresholds <span className="field-hint">(optional — leave blank to skip)</span>
              </label>
              <div className="threshold-header-row">
                <span />
                <span className="threshold-col-label">min</span>
                <span className="threshold-col-label">max</span>
              </div>
              {sensorList.map(sensor => (
                <div key={sensor} className="threshold-row">
                  <span className="threshold-sensor">{sensor}</span>
                  <input
                    className="field-input threshold-input"
                    type="number"
                    placeholder="—"
                    value={thresholds[sensor]?.min ?? ''}
                    onChange={e => setThreshold(sensor, 'min', e.target.value)}
                  />
                  <input
                    className="field-input threshold-input"
                    type="number"
                    placeholder="—"
                    value={thresholds[sensor]?.max ?? ''}
                    onChange={e => setThreshold(sensor, 'max', e.target.value)}
                  />
                </div>
              ))}
            </>
          )}

          {error && <p className="field-error">{error}</p>}

          <button className="btn-primary" type="submit" disabled={saving}>
            {saving ? 'Registering…' : 'Register'}
          </button>
        </form>
      </div>
    </div>
  )
}
