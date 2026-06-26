import { useState } from 'react'

const BASE = '/api'

export default function RegisterDevice({ onCreated, onClose }) {
  const [form, setForm] = useState({
    device_id: '',
    name: '',
    location: '',
    sensors: '',
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const set = (field) => (e) => setForm(f => ({ ...f, [field]: e.target.value }))

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
          device_id: form.device_id.trim(),
          name: form.name.trim(),
          location: form.location.trim(),
          sensors: form.sensors.split(',').map(s => s.trim()).filter(Boolean),
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
          <input className="field-input" placeholder="e.g. dev-002" value={form.device_id} onChange={set('device_id')} />

          <label className="field-label">Name *</label>
          <input className="field-input" placeholder="e.g. Compressor Unit B" value={form.name} onChange={set('name')} />

          <label className="field-label">Location</label>
          <input className="field-input" placeholder="e.g. Factory Floor 2" value={form.location} onChange={set('location')} />

          <label className="field-label">Sensors <span className="field-hint">(comma-separated)</span></label>
          <input className="field-input" placeholder="temperature, humidity, accel_x" value={form.sensors} onChange={set('sensors')} />

          {error && <p className="field-error">{error}</p>}

          <button className="btn-primary" type="submit" disabled={saving}>
            {saving ? 'Registering…' : 'Register'}
          </button>
        </form>
      </div>
    </div>
  )
}
