# Phase A (5) — Registry-driven core + device schema

## Goal
Make a registered device the single source of truth. Registering a `source:simulator` device with a sensor list causes the simulator to start publishing for it automatically — zero code edit required. Kills the hardcoded `dev-001` bug.

## Structure & steps

### Files touched / created

| File | Change |
|---|---|
| `backend/models/device.py` | Add `source`, `thresholds`, `status` fields with defaults |
| `backend/routers/devices.py` | Accept/return new fields; allow PATCH on `thresholds`, `source`, `status` |
| `simulator.py` | Rewrite: remove hardcoded `dev-001`; poll Mongo for active simulator devices |
| `frontend/src/components/RegisterDevice.jsx` | Add Source dropdown + per-sensor threshold min/max rows |
| `frontend/src/components/DeviceList.jsx` | Render `source` badge and `status` on each card |

### Ordered steps

1. Update `backend/models/device.py` — add three optional fields with their defaults.
2. Update `backend/routers/devices.py` — pass new fields through on POST/PATCH/GET; no other logic change.
3. Verify (read-only) that `ingestion.py` and the WS bridge in `main.py` already use `twinlab/#` wildcard — no change needed.
4. Rewrite `simulator.py` — on startup and every `REFRESH_S` (default 30 s) reload device list from Mongo; inner publish loop iterates devices and their `sensors` lists; keep bracketed-print style.
5. Add `RegisterDevice.jsx` to `frontend/src/components/` — Source dropdown (`simulator` / `hardware`) + collapsible threshold rows (sensor name, min, max).
6. Update `DeviceList.jsx` — show `source` as a small badge and `status` alongside existing fields.
7. Wire `RegisterDevice.jsx` into `App.jsx` (button to open it, or inline in the device panel).
8. Update `frontend/src/api.js` — extend `createDevice` / `updateDevice` payloads to include new fields if needed.

## Start commands

Run each in a **separate terminal** from `D:\TwinLab`:

```powershell
# Terminal 1 — Docker services (Mosquitto + InfluxDB + MongoDB)
docker compose up -d

# Terminal 2 — MQTT → InfluxDB ingestion
.venv\Scripts\python ingestion.py

# Terminal 3 — Simulator (registry-driven; register a device first)
.venv\Scripts\python simulator.py

# Terminal 4 — FastAPI backend
cd backend
..\\.venv\Scripts\uvicorn main:app --reload --port 8000

# Terminal 5 — React dashboard
cd frontend
npm run dev
# Opens at http://localhost:5173
```

| Service | URL |
|---|---|
| Dashboard | http://localhost:5173 |
| API + Swagger | http://localhost:8000/docs |
| InfluxDB UI | http://localhost:8086 (admin / twinlab123) |

> **Note:** Start the simulator *after* registering at least one `source:simulator` device via the dashboard. The simulator polls Mongo every 30 s — a newly registered device starts publishing within one refresh cycle.

## Expected outcome

**Verifiable acceptance checks (from MVP_v2_PLAN.md §2):**

- [ ] Register a new `source:simulator` device with a sensor list via the UI → within one simulator refresh cycle (~30 s), live charts populate for that device. No edit to `simulator.py`.
- [ ] Register a `source:hardware` device → it appears in the device list; charts stay empty until a real publisher sends to its topic.
- [ ] `GET /devices/{id}` JSON response includes `source`, `thresholds`, `status`.
- [ ] Old devices in Mongo without those fields still work (backend returns defaults, simulator skips them if `source` is absent or not `"simulator"`).

---

## ✅ Actually achieved

**Shipped:**
- `backend/models/device.py`: `DeviceCreate`, `DeviceUpdate`, `DeviceResponse` all extended with `source` (default `"simulator"`), `thresholds` (default `{}`), `status` (default `"active"`). No changes to `routers/devices.py` required — existing `model_dump()` + `if v is not None` PATCH filter handles all three fields correctly.
- `simulator.py`: full rewrite. Hardcoded `dev-001` / `DEVICE_ID` removed. On startup and every 30 s, queries MongoDB for `{source: "simulator", status: "active"}` via **pymongo** and publishes all listed sensors. Unknown sensors fall back to a generic oscillating value. Keeps bracketed-print log style.
- `frontend/src/components/RegisterDevice.jsx`: Source `<select>` (simulator / hardware), reactive threshold rows (one min/max input pair per parsed sensor), `buildThresholds()` emits only non-empty bounds.
- `frontend/src/components/DeviceList.jsx`: SIM / HW source badge per card, `inactive` text for non-active devices.
- `frontend/src/App.css`: `.source-badge--simulator`, `.source-badge--hardware`, `.status-inactive`, `.device-card-top`, `.threshold-row`, `.threshold-sensor`, `.threshold-input`, `.threshold-header-row`, `.threshold-col-label`; modal made scrollable with `max-height: calc(90vh - 52px)`.

**Deviations from plan:** none.

**Deferrals:** none — all Phase A scope shipped.

**Gotchas:**
- Old Mongo devices without `source` are **not** picked up by the simulator (intentional — the query is strict `source == "simulator"`). They still serve correctly through the API via Pydantic defaults. Users must PATCH or re-register old devices to get simulation.
- `anomaly.py` untouched — unmount deferred to Phase B as specified.
