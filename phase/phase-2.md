# Phase 2 — FastAPI Backend

## Goal
Build the application backend that sits between the ingestion layer (InfluxDB + MongoDB) and the frontend. Phase 2 delivers a working API that the React dashboard (Phase 3) can talk to, including real-time data push over WebSockets.

---

## What Phase 2 Does NOT Include
- No frontend (Phase 3)
- No Isolation Forest wiring (Phase 3)
- No authentication (Phase 4 hardening)
- No LSTM, no Gemini (Phase 4)

---

## Project Structure (actual)

```
TwinLab/
└── backend/
    ├── __init__.py
    ├── .env                    # All config — replaces hard-coded constants from Phase 1
    ├── config.py               # pydantic-settings loads .env into a Settings object
    ├── main.py                 # FastAPI app, lifespan, MQTT-to-WebSocket bridge
    ├── db/
    │   ├── __init__.py
    │   ├── influx.py           # Lazy InfluxDB client + query_api
    │   └── mongo.py            # Async Motor client, connect/close/get_db
    ├── models/
    │   ├── __init__.py
    │   └── device.py           # DeviceCreate, DeviceUpdate, DeviceResponse (Pydantic)
    └── routers/
        ├── __init__.py
        ├── devices.py          # CRUD against MongoDB
        ├── readings.py         # Flux query against InfluxDB
        └── ws.py               # WebSocket endpoint + ConnectionManager
```

---

## Key Technical Decisions

- **Motor** (async MongoDB driver) — matches FastAPI's async model; PyMongo would block the event loop
- **pydantic-settings** — loads `.env` into a typed `Settings` object; no scattered `os.getenv()` calls
- **Logging** — switches from Phase 1's bracketed `print()` style to Python's `logging` module with timestamps
- **WebSocket + MQTT bridge** — backend runs a `paho-mqtt` subscriber in a background daemon thread; incoming messages are pushed to connected WebSocket clients via `asyncio.run_coroutine_threadsafe()` on the main event loop — no InfluxDB polling for live data
- **Input validation on Flux queries** — `device_id` and `sensor` validated against `^[\w\-]+$` before string interpolation to prevent Flux injection

---

## Build Steps

### Step 1 — Install new dependencies
Updated `requirements.txt` at the project root and installed into `.venv`:

```
fastapi
uvicorn[standard]
motor
pydantic-settings
```

```bash
# from D:\TwinLab
.venv\Scripts\pip install -r requirements.txt
```

Versions installed: `fastapi 0.138.0`, `uvicorn 0.49.0`, `motor 3.7.1`, `pydantic-settings 2.14.2`, `pymongo 4.17.0`.

---

### Step 2 — Create folder structure
```powershell
New-Item -ItemType Directory -Force -Path backend\routers, backend\db, backend\models
```

---

### Step 3 — Config (`backend/.env` + `backend/config.py`)
`.env` holds all service coordinates:
```
MQTT_HOST=localhost
MQTT_PORT=1883
INFLUX_URL=http://localhost:8086
INFLUX_TOKEN=twinlab-super-secret-token
INFLUX_ORG=twinlab
INFLUX_BUCKET=twinlab
MONGO_URI=mongodb://admin:twinlab123@localhost:27017
MONGO_DB=twinlab
```
`config.py` exposes a single `settings` singleton imported everywhere else.

---

### Step 4 — DB clients (`db/mongo.py`, `db/influx.py`)
- `mongo.py` — `connect_mongo()` / `close_mongo()` hooked to app lifespan; `get_db()` returns the active Motor database.
- `influx.py` — `get_query_api()` initialises lazily on first call; `close_influx()` called on shutdown.

---

### Step 5 — Pydantic models (`models/device.py`)
| Model | Purpose |
|-------|---------|
| `DeviceCreate` | Validated request body for POST |
| `DeviceUpdate` | All fields optional — used for PATCH |
| `DeviceResponse` | Response shape — excludes MongoDB `_id` |

---

### Step 6 — Device registry (`routers/devices.py`)
Five endpoints mounted at `/devices`:

| Method | Path | Action |
|--------|------|--------|
| POST | `/devices` | Register device in MongoDB |
| GET | `/devices` | List all devices |
| GET | `/devices/{device_id}` | Get single device |
| PATCH | `/devices/{device_id}` | Update name / location / sensors |
| DELETE | `/devices/{device_id}` | Remove device |

---

### Step 7 — Readings (`routers/readings.py`)

| Method | Path | Query params |
|--------|------|--------------|
| GET | `/devices/{device_id}/readings` | `sensor` (required), `limit` 1–500 (default 20), `range_hours` 1–168 (default 24) |

Runs a Flux query against InfluxDB. Returns array of `{ ts, value, unit, sensor, device_id }`.

---

### Step 8 — WebSocket (`routers/ws.py`)
`ConnectionManager` holds `dict[device_id → list[WebSocket]]`. Endpoint: `ws://localhost:8000/ws/{device_id}`. Keeps connection alive by waiting on `receive_text()`; client messages are ignored for now.

---

### Step 9 — Main app + MQTT bridge (`main.py`)
- Lifespan: connects Mongo → starts MQTT daemon thread → yields → closes both.
- MQTT `on_message`: parses topic, builds payload dict, calls `asyncio.run_coroutine_threadsafe(manager.broadcast(...), _loop)`.
- CORS: `allow_origins=["*"]` for dev.
- `GET /health` returns `{"status": "ok"}`.

---

### Step 10 — Start the server
```bash
# from D:\TwinLab\backend
..\\.venv\Scripts\uvicorn main:app --reload --port 8000
```

Swagger UI: `http://localhost:8000/docs`
OpenAPI schema: `http://localhost:8000/openapi.json`

---

## Demo Script

Make sure Docker is up (`docker compose up -d` from `D:\TwinLab`) and the backend is running before executing these.

### Health check
```powershell
Invoke-RestMethod -Uri "http://localhost:8000/health"
# Expected: { "status": "ok" }
```

---

### Device Registry

**Register a device**
```powershell
Invoke-RestMethod -Method POST -Uri "http://localhost:8000/devices" `
  -ContentType "application/json" `
  -Body '{"device_id":"device_001","name":"Boiler Unit A","location":"Factory Floor 1","sensors":["temperature","humidity","vibration"],"description":"HSK Bone Care pilot unit"}'
```

**List all devices**
```powershell
Invoke-RestMethod -Uri "http://localhost:8000/devices"
```

**Get a single device**
```powershell
Invoke-RestMethod -Uri "http://localhost:8000/devices/device_001"
```

**Update a field**
```powershell
Invoke-RestMethod -Method PATCH -Uri "http://localhost:8000/devices/device_001" `
  -ContentType "application/json" `
  -Body '{"location":"Factory Floor 2"}'
```

**Delete a device**
```powershell
Invoke-RestMethod -Method DELETE -Uri "http://localhost:8000/devices/device_001"
# Returns 204 No Content
```

---

### Readings

> Pre-requisite: run `simulator.py` for ~30 seconds first so data exists in InfluxDB.

**Last 20 temperature readings (default)**
```powershell
Invoke-RestMethod -Uri "http://localhost:8000/devices/device_001/readings?sensor=temperature"
```

**Last 5 humidity readings**
```powershell
Invoke-RestMethod -Uri "http://localhost:8000/devices/device_001/readings?sensor=humidity&limit=5"
```

**Vibration — last hour, up to 50 rows**
```powershell
Invoke-RestMethod -Uri "http://localhost:8000/devices/device_001/readings?sensor=vibration&range_hours=1&limit=50"
```

Each row in the response:
```json
{
  "ts": "2026-06-21T08:15:30+00:00",
  "value": 24.6,
  "unit": "C",
  "sensor": "temperature",
  "device_id": "device_001"
}
```

---

### WebSocket Live Stream

Run in a PowerShell window while `simulator.py` is publishing. You should see a new JSON line printed every time the simulator fires.

```powershell
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$uri = [System.Uri]"ws://localhost:8000/ws/device_001"
$ct = [System.Threading.CancellationToken]::None
$ws.ConnectAsync($uri, $ct).Wait()

$buffer = [System.Byte[]]::new(1024)
while ($ws.State -eq "Open") {
    $result = $ws.ReceiveAsync($buffer, $ct).Result
    $msg = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
    Write-Host $msg
}
```

---

## Verification Checklist

- [ ] `GET /health` returns `{"status":"ok"}`
- [ ] `POST /devices` creates device in MongoDB, returns document with timestamps
- [ ] `GET /devices` returns array including the registered device
- [ ] `PATCH /devices/device_001` returns updated field
- [ ] `DELETE /devices/device_001` returns 204
- [ ] `GET /devices/device_001/readings?sensor=temperature` returns array of `{ts, value, unit}`
- [ ] WebSocket `ws://localhost:8000/ws/device_001` streams live JSON while `simulator.py` runs
- [ ] Swagger UI at `http://localhost:8000/docs` shows all endpoints

---

## Outcome
*To be filled in when Phase 2 verification is complete.*
