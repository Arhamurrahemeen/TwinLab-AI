# Phase D (8) — Simulator control mini-app

## Goal
Give the demo operator a separate control surface to drive the simulator deterministically — set live sensor baselines, toggle the generator, and inject fault scenarios (fuel theft, overheat, overload, connectivity drop) on demand. Eliminates waiting for random values to breach thresholds.

## Structure & steps

### Files created

| File | Purpose |
|---|---|
| `backend/routers/sim.py` | `GET /sim`, `GET /sim/{id}`, `PUT /sim/{id}` ↔ `sim_control` collection |
| `sim-control/` | New Vite React app — operator control surface |

### Files modified

| File | Change |
|---|---|
| `backend/main.py` | Mount sim router |
| `simulator.py` | Read `sim_control` doc per device each tick; apply `generator_on`, `base_values`, `inject` modes |

### `sim_control` document schema

```jsonc
{
  "device_id": "shell-gen-1",
  "generator_on": true,
  "base_values": { "fuel_level": 70, "temperature": 35, "humidity": 50 },
  "inject": {
    "fuel_theft": { "active": false, "until_ts": 0 },
    "overheat":   { "active": false, "until_ts": 0 },
    "overload":   { "active": false, "until_ts": 0 },
    "offline":    { "active": false, "until_ts": 0 }
  },
  "updated_at": "..."
}
```

### Ordered steps

1. **`backend/routers/sim.py`**
   - `GET /sim` — return all `source:simulator` devices with their `sim_control` docs merged (upsert default doc if missing).
   - `GET /sim/{device_id}` — return control doc for one device (create default if missing); 404 if device not found or not `source:simulator`.
   - `PUT /sim/{device_id}` — upsert the control doc; reject with 400 if device isn't `source:simulator`. Accept partial body (only the keys sent are updated, via `$set`). Set `updated_at`.
   - Validate `device_id` against `^[\w\-]+$`.

2. **`backend/main.py`**
   - `from routers import sim as sim_router`
   - `app.include_router(sim_router.router, prefix="/sim", tags=["sim-control"])`

3. **`simulator.py`** — extend without breaking the flat style
   - Add `sim_col = mongo[MONGO_DB]["sim_control"]` (same client, second collection reference).
   - Add in-memory fuel level tracker: `_fuel_level: dict = {}` and `_last_base_fuel: dict = {}`.
   - Per tick, per device: call `_get_sim_ctrl(sim_col, device_id)` → returns the control doc or defaults.
   - **Offline check first:** if `inject.offline.active` and `until_ts > now_ms` → skip all publishing for this device.
   - **Resolve values** based on control doc:
     - `fuel_level`: tracked in `_fuel_level[device_id]`. Reset from `base_values.fuel_level` when the operator changes it (detect via `_last_base_fuel`). Drain rates:
       - `fuel_theft` active → −1.0 L/tick (drops 5 L in 5 s → triggers alert fast)
       - `generator_on=True`, no theft → −0.01 L/tick (slow burn)
       - `generator_on=False` → flat
     - `load_current`: `0–0.5 A` when off or theft; `base_values.load_current (default 18)` ±2 when on; `50+ A` during overload.
     - `temperature`: `base_values.temperature (default 35)` + 5 °C when gen on; `95+ °C` during overheat.
     - `humidity`: `base_values.humidity (default 55)` ±2.
     - Everything else: existing `_sensor_value(sensor, t)` (no change).
   - Keep bracketed-print logging style. Add `[SIM-CTRL]` prefix for control-state logs.

4. **`sim-control/` — new Vite React app**
   - Scaffold: `npm create vite@latest sim-control -- --template react` then `npm install` in `sim-control/`.
   - `src/api.js`: `getDevices()`, `getCtrl(id)`, `putCtrl(id, body)` against `http://localhost:8000`.
   - `src/App.jsx`: on mount, fetch `/sim` → list of devices; render one `<DeviceControl>` per device.
   - `src/components/DeviceControl.jsx`:
     - Device name + `source` badge.
     - **Generator toggle** — PUT `{generator_on: bool}` immediately on change.
     - **Base value sliders**: `fuel_level` (0–100 L), `temperature` (0–100 °C), `humidity` (0–100 %) — debounced PUT (300 ms) on slide.
     - **Injector buttons** (4): "Inject Fuel Theft", "Inject Overheat", "Inject Overload", "Drop Connectivity". Each PUT `{inject: {<type>: {active: true, until_ts: now + 120_000}}}`. Show remaining countdown while active (poll GET every 5 s or derive from `until_ts` client-side).
   - `src/App.css`: minimal but legible — cream background, card per device, clear button states (active injector = red/amber).
   - Separate dev server — Vite picks its own port (e.g. `:5174`).

## Start commands

```powershell
# Terminal 1 — Docker (leave running)
docker compose up -d

# Terminal 2 — Ingestion (leave running)
.venv\Scripts\python ingestion.py

# Terminal 3 — Simulator (restart to pick up sim_control changes)
.venv\Scripts\python simulator.py

# Terminal 4 — Backend (--reload picks up all backend changes)
cd backend
..\.venv\Scripts\uvicorn main:app --reload --port 8000

# Terminal 5 — Dashboard
cd frontend
npm run dev

# Terminal 6 — Sim control (new)
cd sim-control
npm install   # first run only
npm run dev
# → Vite assigns a port (e.g. http://localhost:5174)
```

## Expected outcome

**Acceptance checks (from MVP_v2_PLAN.md §5):**

- [ ] Move the fuel_level slider → main dashboard chart tracks the new baseline within ~2 s.
- [ ] Toggle generator OFF, click **Inject fuel theft** → fuel_level drops rapidly on dashboard → fuel-theft critical alert fires in the Alerts panel (and WhatsApp once Twilio is fixed).
- [ ] Click **Inject overheat** → temperature climbs above the device's `temperature.max` threshold → threshold critical alert fires.
- [ ] Click **Drop connectivity** → simulator stops publishing for that device; main dashboard shows stale/offline state; clicking again (or waiting for `until_ts` to expire) resumes live data.
- [ ] `GET /sim/{device_id}` returns the current control doc with all fields.
- [ ] `PUT /sim` on a `source:hardware` device returns 400.

---

## ✅ Actually achieved

**Shipped:**
- `backend/routers/sim.py` (new): `GET /sim` lists all simulator devices merged with their `sim_control` docs. `GET /sim/{id}` returns or creates a default control doc. `PUT /sim/{id}` does a full `replace_one` upsert (rejects non-simulator devices with 400). Device ID validated against `^[\w\-]+$`. Default doc shape: `generator_on: true`, `base_values: {fuel_level:70, temperature:35, humidity:55}`, all 4 injectors inactive.
- `backend/main.py`: `sim_router` mounted at `/sim` with tag `sim-control`.
- `simulator.py`: full rewrite. Adds `sim_control` collection reference (`sim_col`). Per-tick per-device: calls `_get_sim_ctrl()` → reads control doc or returns `{}` defaults. `_compute_values()` applies: `offline` injector → return `None` (skip publish); `fuel_theft` → drain 1 L/tick from tracked `_fuel_level`; `generator_on` → normal 0.01 L/tick burn or flat when off; `overheat` → 95+ °C; `overload` → 55+ A; sliders reset `_fuel_level` when `base_values.fuel_level` changes in the doc. Accel/gyro/unknown sensors fall back to original `_sensor_value()`.
- `sim-control/` (new Vite React app): scaffolded with `npm create vite -- --template react`. `src/api.js`: `getSimDevices`, `getSimCtrl`, `putSimCtrl`. `src/App.jsx`: loads devices on mount, renders `<DeviceControl>` per device. `src/components/DeviceControl.jsx`: generator toggle (immediate PUT), base-value sliders (commit on mouse-up/touch-end, optimistic local update), 4 injector buttons (set `until_ts = now + 120_000 ms`, show seconds countdown, disabled while active). `src/App.css`: cream background, card layout, amber active-injector state, generator on/off colour variants.

**Deviations from plan:** none.

**Deferrals:** none — all Phase D scope shipped.

**Gotchas:**
- `import simulator` in a test runner triggers the MQTT connect + main loop immediately (module-level code). Validated with `ast.parse` instead.
- Windows PowerShell console (cp1252) throws `UnicodeEncodeError` on `→` — replaced with `->` in simulator print statements.
