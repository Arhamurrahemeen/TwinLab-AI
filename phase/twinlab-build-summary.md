# TwinLab AI — Full Build Summary

> Everything built across all four phases. Single reference document for the full MVP.

---

## What TwinLab AI Is

An **Industrial IoT digital twin SaaS platform** built by OmniteX (founder: Muhammad Arham Rajput) as the anchor product for the NIC Karachi incubation application.

**The pitch:** SMEs and engineering institutions in Pakistan cannot afford Western digital-twin platforms (Siemens, GE Predix, AVEVA). TwinLab is a localised, lean, Urdu-aware alternative built on open-source primitives — priced for the local market.

**Warm pilot client:** HSK Bone Care (Arham's family hospital) — orthopedic equipment monitoring.

---

## Tech Stack

| Layer | Choice |
|-------|--------|
| Hardware (future) | ESP32 + DHT22 + MPU6050 |
| Messaging | MQTT via Mosquitto broker |
| Time-series DB | InfluxDB 2.7 |
| Document DB | MongoDB 7.0 |
| Backend | FastAPI (Python) |
| AI — Chat | Groq API (`llama-3.3-70b-versatile`) |
| AI — Anomaly | Isolation Forest (scikit-learn) |
| AI — RUL | Rule-based linear regression (numpy) |
| Frontend | Vite + React, recharts |
| Deploy | Docker Compose |

---

## Repo Structure

```
TwinLab/
├── backend/
│   ├── .env                    # All credentials (not committed)
│   ├── config.py               # pydantic-settings — loads .env into Settings
│   ├── main.py                 # FastAPI app, MQTT→WebSocket bridge, last-known cache
│   ├── db/
│   │   ├── influx.py           # Lazy InfluxDB client
│   │   └── mongo.py            # Async Motor client
│   ├── models/
│   │   └── device.py           # DeviceCreate, DeviceUpdate, DeviceResponse
│   └── routers/
│       ├── devices.py          # Device CRUD — MongoDB
│       ├── readings.py         # Historical readings — InfluxDB
│       ├── anomaly.py          # Isolation Forest anomaly detection
│       ├── rul.py              # Rule-based RUL forecast
│       ├── chat.py             # Groq AI chat with device context
│       └── ws.py               # WebSocket + ConnectionManager
├── frontend/
│   ├── src/
│   │   ├── api.js              # REST wrappers (getDevices, getReadings, getAnomalies)
│   │   ├── hooks/
│   │   │   └── useDeviceSocket.js  # WebSocket hook — live data + connected bool + auto-reconnect
│   │   ├── components/
│   │   │   ├── DeviceList.jsx      # Device panel with + register button
│   │   │   ├── SensorChart.jsx     # recharts line chart — history + live WebSocket data
│   │   │   ├── AlertsPanel.jsx     # Anomaly flags, polls every 30s
│   │   │   ├── RulCard.jsx         # RUL forecast strip
│   │   │   ├── ChatPanel.jsx       # Floating Groq chat — Urdu/English
│   │   │   └── RegisterDevice.jsx  # Modal form — POST /devices
│   │   ├── App.jsx             # Three-panel layout + load-shedding banner
│   │   ├── App.css             # Full brand-palette styles
│   │   └── index.css           # Global reset
│   └── vite.config.js          # Dev proxy: /api → :8000, /ws → ws://:8000
├── mosquitto/config/mosquitto.conf
├── docker-compose.yml          # Mosquitto + InfluxDB + MongoDB
├── ingestion.py                # MQTT subscriber → InfluxDB writer
├── simulator.py                # Fake sensor publisher with fault injection
├── test_mqtt.py                # Bare MQTT listener
└── requirements.txt
```

---

## How to Run Everything

```powershell
# Terminal 1 — Docker services
docker compose up -d

# Terminal 2 — Ingestion
.venv\Scripts\python ingestion.py

# Terminal 3 — Simulator
.venv\Scripts\python simulator.py

# Terminal 4 — Backend
cd backend
..\\.venv\Scripts\uvicorn main:app --reload --port 8000

# Terminal 5 — Frontend
cd frontend
npm run dev
```

| Service | URL | Credentials |
|---------|-----|-------------|
| **Dashboard** | http://localhost:5173 | — |
| API + Swagger | http://localhost:8000/docs | — |
| InfluxDB UI | http://localhost:8086 | admin / twinlab123 |
| MQTT broker | localhost:1883 | anonymous |
| MongoDB | localhost:27017 | admin / twinlab123 |

---

## All API Endpoints

| Method | Path | What it does |
|--------|------|--------------|
| GET | `/health` | Sanity check |
| POST | `/devices` | Register device in MongoDB |
| GET | `/devices` | List all devices |
| GET | `/devices/{id}` | Get single device |
| PATCH | `/devices/{id}` | Update name / location / sensors |
| DELETE | `/devices/{id}` | Remove device |
| GET | `/devices/{id}/readings` | Historical readings from InfluxDB (`?sensor=&limit=&range_hours=`) |
| GET | `/devices/{id}/anomalies` | Isolation Forest results (`?sensor=&limit=&range_hours=`) |
| GET | `/devices/{id}/rul` | Rule-based RUL forecast per sensor |
| GET | `/devices/{id}/last-known` | Last cached reading per sensor (load-shedding fallback) |
| POST | `/chat` | Groq AI response with device context (`{device_id, message}`) |
| WS | `/ws/{id}` | Live MQTT push to connected clients |

---

## MQTT Topic Contract

```
twinlab/device/{device_id}/sensor/{sensor_name}
```

Payload:
```json
{ "value": 24.6, "unit": "C", "ts": 1734000000000 }
```

`ts` is milliseconds. InfluxDB writes in nanoseconds — `ingestion.py` multiplies by 1,000,000.

---

## Phase 1 — Ingestion Pipeline

**Goal:** Sensor data flows from publisher → MQTT → InfluxDB. No application code.

**What was built:**
- `docker-compose.yml` — Mosquitto, InfluxDB, MongoDB
- `mosquitto/config/mosquitto.conf` — port 1883, anonymous, persistence on
- `ingestion.py` — MQTT subscriber, writes to InfluxDB bucket `twinlab`, measurement `sensor_reading`
- `simulator.py` — fake DHT22 + MPU6050 publisher; no hardware needed
- `test_mqtt.py` — bare MQTT listener for broker sanity check

**Verified:** simulated sensor data lands in InfluxDB `twinlab` bucket, visible in Data Explorer.

**Issues encountered:**
- Docker daemon not running on first attempt — fix: open Docker Desktop first
- `mosquitto.conf` was auto-created as a directory by Docker — fix: delete, recreate as file

---

## Phase 2 — FastAPI Backend

**Goal:** Application layer between ingestion and frontend. REST API + WebSocket push.

**What was built:**

*Config layer:*
- `backend/.env` — all service coordinates
- `backend/config.py` — pydantic-settings singleton, typed, no scattered `os.getenv()` calls

*DB clients:*
- `db/mongo.py` — async Motor client, hooked to FastAPI lifespan
- `db/influx.py` — lazy InfluxDB client, closed on shutdown

*Models:*
- `DeviceCreate` — POST validation
- `DeviceUpdate` — all fields optional, used for PATCH
- `DeviceResponse` — response shape, excludes MongoDB `_id`

*Routers:*
- `routers/devices.py` — full CRUD against MongoDB
- `routers/readings.py` — Flux query against InfluxDB, input validated against `^[\w\-]+$`
- `routers/ws.py` — `ConnectionManager` dict, WebSocket endpoint

*Main:*
- `main.py` — lifespan, MQTT daemon thread, `asyncio.run_coroutine_threadsafe()` bridge, CORS `*`

**Key decisions:**
- Motor (async) not PyMongo — keeps FastAPI non-blocking
- MQTT → WebSocket bridge via background daemon thread, not InfluxDB polling
- Flux query input validated before string interpolation (injection prevention)

---

## Phase 3 — React Dashboard + Anomaly Detection

**Goal:** Frontend dashboard consuming Phase 2 API. Isolation Forest anomaly detection.

**What was built:**

*Backend addition:*
- `routers/anomaly.py` — fetches last N readings, fits `IsolationForest`, uses score-based threshold (mean − 2σ) instead of fixed contamination to avoid constant false positives; minimum 20 points required

*Frontend (Vite + React):*
- `vite.config.js` — Vite dev proxy, `/api → :8000`, `/ws → ws://:8000`
- `api.js` — `getDevices`, `getReadings`, `getAnomalies`
- `useDeviceSocket.js` — opens WebSocket per device, buffers 200 messages
- `DeviceList.jsx` — device cards with teal left-border active state
- `SensorChart.jsx` — seeds from REST (50 historical), appends live WebSocket; per-sensor colour; recharts `LineChart`; no animation for performance
- `AlertsPanel.jsx` — polls all device sensors every 30s, red-bordered alert cards
- `App.jsx` — three-panel layout: DeviceList | charts grid | AlertsPanel

*Simulator update:*
- Fault spike injected every 60 seconds (temp +8–12°C, humidity +15–20%, accel burst) — gives anomaly detection real signal to flag

*UI palette (OmniteX brand):*

| Role | Colour |
|------|--------|
| App background | `#1E2128` |
| Card surface | `#252B35` |
| Card border | `#2E3542` |
| Teal accent | `#4A9B8E` |
| Green accent | `#56C596` |
| Primary text | `#E8EDF2` |
| Muted text | `#8A96A8` |
| Alert red | `#E05252` |

Charts are driven by `device.sensors` from MongoDB — no hardcoded sensor list.

---

## Phase 4 — Groq Chat, RUL, Load-Shedding, Device Registration

**Goal:** Complete the MVP for the NIC Karachi demo.

**What was built:**

*Backend:*
- `routers/chat.py` — `POST /chat`; fetches latest readings from InfluxDB; builds device context string; calls Groq `llama-3.3-70b-versatile`; replies in same language as input (Urdu / English / Roman Urdu)
- `routers/rul.py` — `GET /devices/{id}/rul`; linear regression (numpy polyfit) per sensor over last 2h; extrapolates to per-sensor threshold; returns `{sensor, status, hours_remaining, trend}`; >720h treated as stable; per-sensor thresholds defined for all simulator sensors
- `main.py` additions — in-memory `_last_known` dict updated on every MQTT message; `GET /devices/{id}/last-known` serves cached readings; Groq key read from `config.py`

*Frontend:*
- `useDeviceSocket.js` — now returns `{ messages, connected }`; auto-reconnects every 3s on drop
- `App.jsx` — yellow load-shedding banner when `connected === false`; navbar status dot goes red + "Offline" label
- `RulCard.jsx` — horizontal strip below charts header; shows sensors with hours-remaining forecast; warning (<24h) highlighted amber; hidden when all stable
- `ChatPanel.jsx` — floating teal FAB bottom-right; slide-up 340px panel; user/AI chat bubbles; loading dots animation; Urdu input works natively (browser handles RTL)
- `RegisterDevice.jsx` — modal with device_id, name, location, sensors (comma-separated) fields; POSTs to `/devices`; list auto-refreshes on success
- `DeviceList.jsx` — `+` button in panel title row triggers register modal

*Groq model:* `llama-3.3-70b-versatile` — free tier, fast, multilingual, handles Roman Urdu well.

---

## NIC Demo Flow

1. Open dashboard → `http://localhost:5173`
2. Select **Boiler Unit A** from the device list
3. 8 live sensor charts populate — temperature, humidity, accel x/y/z, gyro x/y/z
4. Wait ~60 seconds → simulator injects fault spike → red alert appears in Alerts panel
5. Click `💬` (bottom-right) → type **"kya masla hai?"** → Groq replies in Roman Urdu with sensor context
6. Kill the backend (`Ctrl+C`) → yellow banner: *"Connectivity lost — showing last known readings"*
7. Restart backend → banner clears, live data resumes automatically
8. Click `+` in Devices panel → register a second device live on stage

---

## What Is NOT Built (intentionally deferred)

| Feature | Reason deferred |
|---------|----------------|
| Authentication | Post-MVP, Phase 5 |
| Real LSTM training | Requires real degradation data from hardware |
| Real ESP32 hardware | Simulator covers demo; hardware in Phase 5 |
| TwinLab Edu canvas | Separate product vertical, separate codebase |
| Production deployment | Docker Compose for now; cloud infra post-NIC |
| Test suite | Phase 5 hardening |
| MQTT authentication | Intentionally anonymous through demo phase |

---

*Document written at end of Phase 4. All four phases shipped. MVP ready for NIC Karachi demo.*
