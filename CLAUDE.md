# CLAUDE.md — TwinLab AI

> This file is read automatically by Claude Code at the start of every session in this directory. It is the single source of truth for project context. Keep it tight; link out to deeper docs when they exist.

---

## 1. What TwinLab AI is

TwinLab AI is an **Industrial IoT (IIoT) digital twin SaaS platform** being built by **OmniteX** (Pakistan-based startup, founder: Muhammad Arham Rajput). The product is currently in **MVP build phase** and is the anchor of OmniteX's NIC Karachi incubation application.

The thesis: SMEs and engineering institutions in Pakistan cannot afford Western digital-twin platforms (Siemens, GE Predix, AVEVA). TwinLab is a localised, lean, Urdu-aware alternative built on open-source primitives, with a load-shedding-tolerant architecture and a price point that fits the local market.

### Two product verticals

**TwinLab Pro** — SME factory monitoring
- Real-time sensor monitoring (temperature, humidity, vibration, current)
- Anomaly detection via **Isolation Forest**
- Remaining Useful Life (RUL) prediction via **LSTM**
- Urdu-first conversational UI (Gemini-powered)
- Load-shedding mode (graceful degradation when power/connectivity drops)
- Target client: small/mid manufacturers, including HSK Bone Care (warm pilot — Arham's family hospital, orthopedic equipment monitoring)

**TwinLab Edu** — Engineering student kits
- React Flow canvas for building virtual experiments
- Experiment templates (DSP, control systems, IIoT labs)
- Pairs with low-cost ESP32-based hardware kits
- Target: DUET, NED, and other Pakistani engineering universities

### Tech stack (locked)

| Layer            | Choice                                                   |
| ---------------- | -------------------------------------------------------- |
| Hardware         | ESP32 + DHT22 (temp/humidity) + MPU6050 (accel/gyro)     |
| Messaging        | MQTT via **Mosquitto** broker                            |
| Time-series DB   | **InfluxDB 2.7** (sensor readings)                       |
| Document DB      | **MongoDB 7.0** (device registry, users, configs)        |
| Backend          | **FastAPI** (Python)                                     |
| AI               | Gemini API (chat/coaching), Isolation Forest, LSTM       |
| Frontend         | **React** + React Flow (for Edu canvas)                  |
| Deploy           | Docker Compose (dev), TBD for prod                       |

---

## 2. Current status — Phase 3 complete, Phase 4 is next

| Phase | Goal | Status |
| ----- | ---- | ------ |
| **1** | MQTT + InfluxDB + MongoDB up, sensor data landing in time-series DB | ✅ Done |
| **2** | FastAPI backend: device registry (Mongo), live readings endpoint (Influx), WebSocket push | ✅ Done |
| **3** | React dashboard: live charts, device list, alerts panel; Isolation Forest anomaly detection | ✅ Done |
| **4** | Gemini Urdu chat, rule-based RUL, load-shedding mode, device registration UI | ✅ Done |

The MVP target is a **demoable end-to-end loop**: a simulated ESP32 publishes readings, the dashboard shows them live, an anomaly is flagged, the user asks "kya masla hai?" in Urdu, Gemini explains it. That's the NIC pitch.

---

## 3. Repo structure (current)

```
TwinLab/
├── backend/                        # FastAPI app (Phase 2+3)
│   ├── .env                        # Service config (not committed)
│   ├── config.py                   # pydantic-settings loads .env
│   ├── main.py                     # App entry point + MQTT-to-WebSocket bridge
│   ├── db/
│   │   ├── influx.py               # Lazy InfluxDB client
│   │   └── mongo.py                # Async Motor client
│   ├── models/
│   │   └── device.py               # DeviceCreate, DeviceUpdate, DeviceResponse
│   └── routers/
│       ├── devices.py              # CRUD against MongoDB
│       ├── readings.py             # Flux query against InfluxDB
│       ├── anomaly.py              # Isolation Forest — GET /devices/{id}/anomalies
│       └── ws.py                   # WebSocket + ConnectionManager
├── frontend/                       # Vite + React dashboard (Phase 3)
│   ├── src/
│   │   ├── api.js                  # getDevices / getReadings / getAnomalies
│   │   ├── hooks/
│   │   │   └── useDeviceSocket.js  # WebSocket hook — live MQTT readings
│   │   ├── components/
│   │   │   ├── DeviceList.jsx      # Left panel — registered devices
│   │   │   ├── SensorChart.jsx     # recharts line chart (history + live)
│   │   │   └── AlertsPanel.jsx     # Right panel — anomaly flags, polls every 30 s
│   │   ├── App.jsx                 # Three-panel layout
│   │   ├── App.css                 # Brand palette + component styles
│   │   └── index.css               # Global reset
│   └── vite.config.js              # Dev proxy: /api → :8000, /ws → ws://:8000
├── assets/                         # Brand assets + logos
├── phase/
│   ├── phase-1.md
│   ├── phase-2.md
│   └── phase-3.md
├── mosquitto/config/mosquitto.conf
├── docker-compose.yml
├── ingestion.py
├── simulator.py
├── test_mqtt.py
├── requirements.txt                # All deps (Phase 1–3)
└── README.md
```

---

## 4. How to start everything

```powershell
# 1. Start Docker services (run from D:\TwinLab)
docker compose up -d

# 2. Start ingestion service
.venv\Scripts\python ingestion.py

# 3. Start simulator (separate terminal)
.venv\Scripts\python simulator.py

# 4. Start FastAPI backend (separate terminal, from backend/)
cd backend
..\\.venv\Scripts\uvicorn main:app --reload --port 8000

# 5. Start React dashboard (separate terminal, from frontend/)
cd frontend
npm run dev
# Opens at http://localhost:5173
```

| Service | URL | Credentials |
| ------- | --- | ----------- |
| **Dashboard** | http://localhost:5173 | — |
| InfluxDB UI | http://localhost:8086 | admin / twinlab123 |
| API + Swagger | http://localhost:8000/docs | — |
| MQTT broker | localhost:1883 | anonymous |
| MongoDB | localhost:27017 | admin / twinlab123 |

---

## 5. Phase 2 — what was built

### API endpoints (all running on `localhost:8000`)

| Method | Path | What it does |
| ------ | ---- | ------------ |
| GET | `/health` | Sanity check |
| POST | `/devices` | Register device in MongoDB |
| GET | `/devices` | List all devices |
| GET | `/devices/{device_id}` | Get single device |
| PATCH | `/devices/{device_id}` | Update name/location/sensors |
| DELETE | `/devices/{device_id}` | Remove device |
| GET | `/devices/{device_id}/readings` | Query InfluxDB (`?sensor=temperature&limit=20&range_hours=24`) |
| WS | `/ws/{device_id}` | Live push — MQTT messages forwarded to connected clients |

### Key design decisions made in Phase 2
- **Motor** (async MongoDB driver) — not PyMongo, keeps FastAPI non-blocking
- **pydantic-settings** — `.env` file loaded into a typed `Settings` singleton in `config.py`
- **MQTT → WebSocket bridge** — background daemon thread runs paho-mqtt, uses `asyncio.run_coroutine_threadsafe()` to push to WebSocket clients on the main loop. No InfluxDB polling for live data.
- **Flux query input validation** — `device_id` and `sensor` validated against `^[\w\-]+$` before string interpolation
- **CORS** — `allow_origins=["*"]` for dev, tightens in Phase 4

---

## 6. MQTT topic contract (do not change)

```
twinlab/device/{device_id}/sensor/{sensor_name}
```

Payload (JSON):
```json
{ "value": 24.6, "unit": "C", "ts": 1734000000000 }
```

`ts` is milliseconds since epoch. InfluxDB writes use nanoseconds — `ingestion.py` multiplies by 1,000,000.

---

## 7. Phase 3 — what's next (React dashboard)

Phase 3 scope:
- React app with live sensor charts (consuming `/devices/{id}/readings` + WebSocket)
- Device list panel
- Alerts panel
- Isolation Forest anomaly detection wired into the backend (new endpoint or flag on readings)

Real ESP32 hardware is still NOT needed — `simulator.py` covers it. Hardware comes in Phase 3+ once the dashboard is worth showing.

---

## 8. Constraints and conventions

- **Python style**: flat scripts for Phase 1 files. Backend uses package structure but stays simple — no DI containers, no abstract base classes.
- **Config**: `.env` in `backend/` loaded via pydantic-settings. Never hard-code credentials.
- **Logging**: Phase 2+ uses Python `logging` module with timestamps. Phase 1 files keep bracketed print style (`[MQTT]`, `[OK]`, `[ERROR]`).
- **Error handling**: log and continue. Ingestion and backend must never crash on a bad payload.
- **No silent MQTT topic changes** — firmware (Phase 3+) and backend both depend on the contract above.

---

## 9. Things Claude Code should NOT do

- Do not refactor Phase 1 flat scripts into classes or add DI. They're intentionally readable for the pitch demo.
- Do not swap any locked tech (InfluxDB, Mosquitto, MongoDB, Docker Compose) — change requires a conversation, not a commit.
- Do not add MQTT broker authentication yet. Anonymous is intentional through Phase 3.
- Do not introduce Kubernetes, Helm, or Terraform. Docker Compose is the MVP deployment target.
- Do not write a test suite yet. That lands in Phase 2 hardening or Phase 3.
- Do not commit `backend/.env` — it contains dev credentials.

---

## 10. GitHub

Remote: `https://github.com/Arhamurrahemeen/TwinLab-AI.git`

---

## 11. Who's working on this

- **Arham** (CTO / Founder) — backend, GenAI, MERN, this repo
- **Wahaj** (Head of Product Engineering) — DUET batch topper, joining Phase 2+
- **Kaif** (Co-founder, non-technical) — branding, BD, pitch

---

## 12. Useful background docs (outside this repo)

- OmniteX brand guidelines (cream/ink/teal palette, Calibri)
- NIC Karachi pitch deck (TwinLab as anchor product)
- Peer-reviewed validation paper: ESP8266/MPU6050/MQTT/Python study, 96.2% prediction accuracy, <$200 hardware cost
- Pitch Your Vision 2026 feedback from Syed Hazkeel (Head of NIC Karachi)

---

*Last updated: End of Phase 4. MVP complete. All four phases shipped.*
