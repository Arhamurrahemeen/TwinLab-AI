import { useMemo, useState } from 'react'

const BASE = '/api'

export default function EditDevice({ device, onUpdated, onClose }) {
  const [form, setForm] = useState({
    name:     device.name     ?? '',
    location: device.location ?? '',
    sensors:  (device.sensors ?? []).join(', '),
    source:   device.source   ?? 'simulator',
    status:   device.status   ?? 'active',
  })

  const initThresholds = () => {
    const result = {}
    for (const [sensor, bounds] of Object.entries(device.thresholds ?? {})) {
      result[sensor] = {
        min: bounds.min !== null && bounds.min !== undefined ? String(bounds.min) : '',
        max: bounds.max !== null && bounds.max !== undefined ? String(bounds.max) : '',
      }
    }
    return result
  }

  const [thresholds, setThresholds] = useState(initThresholds)
  const [error,  setError]  = useState('')
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
      const t      = thresholds[sensor] ?? {}
      const minRaw = t.min !== '' && t.min !== undefined ? parseFloat(t.min) : null
      const maxRaw = t.max !== '' && t.max !== undefined ? parseFloat(t.max) : null
      const minVal = (minRaw !== null && !isNaN(minRaw)) ? minRaw : null
      const maxVal = (maxRaw !== null && !isNaN(maxRaw)) ? maxRaw : null
      if (minVal !== null || maxVal !== null) {
        result[sensor] = { min: minVal, max: maxVal }
      }
    }
    return result
  }

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    if (!form.name.trim()) { setError('Name is required.'); return }
    setSaving(true)
    try {
      const res = await fetch(`${BASE}/devices/${device.device_id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name:       form.name.trim(),
          location:   form.location.trim(),
          sensors:    sensorList,
          source:     form.source,
          status:     form.status,
          thresholds: buildThresholds(),
        }),
      })
      if (!res.ok) {
        const data = await res.json()
        setError(data.detail ?? 'Update failed.')
        return
      }
      onUpdated(await res.json())
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
          <span className="modal-title">Edit Device</span>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <form className="modal-form" onSubmit={submit}>
          <label className="field-label">Device ID</label>
          <input className="field-input" value={device.device_id} disabled />

          <label className="field-label">Name *</label>
          <input className="field-input" value={form.name} onChange={set('name')} />

          <label className="field-label">Location</label>
          <input className="field-input" value={form.location} onChange={set('location')} />

          <label className="field-label">Source</label>
          <select className="field-input" value={form.source} onChange={set('source')}>
            <option value="simulator">Simulator</option>
            <option value="hardware">Hardware (ESP32)</option>
          </select>

          <label className="field-label">Status</label>
          <select className="field-input" value={form.status} onChange={set('status')}>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </select>

          <label className="field-label">
            Sensors <span className="field-hint">(comma-separated)</span>
          </label>
          <input
            className="field-input"
            value={form.sensors}
            onChange={set('sensors')}
          />

          {sensorList.length > 0 && (
            <>
              <label className="field-label">
                Thresholds <span className="field-hint">(leave blank to remove)</span>
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
            {saving ? 'Saving…' : 'Save Changes'}
          </button>
        </form>
      </div>
    </div>
  )
}
