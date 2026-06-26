# Phase 3 — React Dashboard + Anomaly Detection

## Goal
Build the frontend dashboard and wire Isolation Forest anomaly detection into the backend. By the end of Phase 3, a user can open a browser, see live sensor readings for a registered device, and see flagged anomalies — all driven by the Phase 2 API.

---

## What Phase 3 Does NOT Include
- No authentication (Phase 4)
- No LSTM / RUL prediction (Phase 4)
- No Gemini Urdu chat (Phase 4)
- No real ESP32 hardware (still simulated)
- No load-shedding mode (Phase 4)

---

## Project Structure (target)

```
TwinLab/
├── backend/                        # Phase 2 — unchanged except new anomaly endpoint
│   └── routers/
│       └── anomaly.py              # NEW: Isolation Forest endpoint
├── frontend/                       # NEW: Vite + React app
│   ├── src/
│   │   ├── components/
│   │   │   ├── DeviceList.jsx      # Left panel — registered devices
│   │   │   ├── SensorChart.jsx     # Live recharts line chart per sensor
│   │   │   └── AlertsPanel.jsx     # Anomaly flags list
│   │   ├── hooks/
│   │   │   └── useDeviceSocket.js  # WebSocket hook — live readings
│   │   ├── api.js                  # Axios/fetch wrappers for REST calls
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
└── ...
```

---

## Key Technical Decisions

- **Vite + React** — faster dev server than CRA, no ejecting needed
- **recharts** — React-native charting, simpler API than Chart.js
- **WebSocket hook** — custom `useDeviceSocket` keeps live data in component state; falls back gracefully if socket drops
- **Isolation Forest** — `scikit-learn` IsolationForest, trained on the last N readings per device per sensor; exposed as `GET /devices/{device_id}/anomalies?sensor=&limit=`
- **No Redux / Zustand** — plain React state + props for MVP scope

---

## Build Steps

### Backend — Anomaly Detection

#### Step 1 — Add scikit-learn to requirements
```
scikit-learn
numpy
```

#### Step 2 — `backend/routers/anomaly.py`
- Fetches last N readings from InfluxDB for a given device + sensor
- Fits `IsolationForest(contamination=0.1)` on the values
- Returns readings with an `is_anomaly: bool` flag

#### Step 3 — Register router in `main.py`
Mount at `/devices/{device_id}/anomalies`.

---

### Frontend

#### Step 4 — Scaffold Vite app
```bash
# from D:\TwinLab
npm create vite@latest frontend -- --template react
cd frontend && npm install
npm install recharts axios
```

#### Step 5 — `api.js`
Thin wrappers over `fetch` / axios:
- `getDevices()`
- `getReadings(deviceId, sensor, limit, rangeHours)`
- `getAnomalies(deviceId, sensor, limit)`

#### Step 6 — `useDeviceSocket` hook
Opens `ws://localhost:8000/ws/{deviceId}`, appends incoming readings to local state array, cleans up on unmount.

#### Step 7 — `DeviceList` component
Fetches `GET /devices` on mount, renders clickable list. Selected device drives the rest of the dashboard.

#### Step 8 — `SensorChart` component
- On mount: fetches last 50 readings via REST (historical seed)
- On socket message: appends new point, trims to 100 points
- Renders `recharts` `<LineChart>` — one chart per sensor (temperature, humidity, vibration, current)

#### Step 9 — `AlertsPanel` component
Polls `GET /devices/{id}/anomalies` every 30 seconds. Renders flagged readings as a timestamped list. Anomaly rows highlighted in red.

#### Step 10 — `App.jsx` layout
Three-panel layout: DeviceList (left) | SensorCharts (center) | AlertsPanel (right).

---

## Demo Script

```bash
# 1. Start Docker
docker compose up -d

# 2. Start ingestion
.venv\Scripts\python ingestion.py

# 3. Start simulator
.venv\Scripts\python simulator.py

# 4. Start backend
cd backend && ..\\.venv\Scripts\uvicorn main:app --reload --port 8000

# 5. Start frontend
cd frontend && npm run dev
# Opens at http://localhost:5173
```

---

## Verification Checklist

- [ ] `GET /devices/{id}/anomalies?sensor=temperature` returns readings with `is_anomaly` flag
- [ ] Frontend loads at `http://localhost:5173`
- [ ] Device list shows registered devices from MongoDB
- [ ] Selecting a device loads historical chart data
- [ ] Live readings appear on the chart as simulator publishes
- [ ] Alerts panel refreshes and shows any flagged anomalies

---

## Outcome
Phase 3 built and verified. Full frontend + anomaly backend delivered:

**Backend additions:**
- `scikit-learn` + `numpy` added to `requirements.txt` and installed
- `backend/routers/anomaly.py` — `GET /devices/{device_id}/anomalies` runs Isolation Forest (`contamination=0.1`) on last N InfluxDB readings; returns each record with `is_anomaly: bool`
- Anomaly router registered in `main.py` at `/devices` prefix

**Frontend (Vite + React at `frontend/`):**
- `vite.config.js` — Vite dev proxy: `/api → http://localhost:8000`, `/ws → ws://localhost:8000`
- `src/api.js` — `getDevices`, `getReadings`, `getAnomalies` fetch wrappers
- `src/hooks/useDeviceSocket.js` — opens WebSocket per device, buffers up to 200 messages
- `src/components/DeviceList.jsx` — fetches MongoDB device list, teal left-border active state
- `src/components/SensorChart.jsx` — seeds from REST history, appends live WebSocket data; per-sensor colour coding; recharts `LineChart`
- `src/components/AlertsPanel.jsx` — polls anomaly endpoint every 30 s across all device sensors; red-bordered alert cards
- `src/App.jsx` — three-panel layout: DeviceList | charts grid | AlertsPanel
- Brand palette: `#1E2128` bg, `#252B35` cards, `#4A9B8E` teal, `#56C596` green, `#E05252` alert red

Charts are driven by `device.sensors` from MongoDB — no hardcoded sensor list.
