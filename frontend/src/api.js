const BASE = '/api'

async function _get(path) {
  const res = await fetch(`${BASE}${path}`)
  if (!res.ok) throw new Error(`GET ${path} → ${res.status}`)
  return res.json()
}

export const getDevices = () => _get('/devices')

export const getReadings = (deviceId, sensor, limit = 50, rangeHours = 24) =>
  _get(`/devices/${deviceId}/readings?sensor=${sensor}&limit=${limit}&range_hours=${rangeHours}`)

export const getAlerts = (deviceId, limit = 50) =>
  _get(`/devices/${deviceId}/alerts?limit=${limit}`)
