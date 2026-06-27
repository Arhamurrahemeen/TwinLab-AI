# Phase B (6) — Generator sensors + threshold alert engine

## Goal
Replace the Isolation Forest anomaly system with an explicit threshold-based alert engine. Every MQTT reading is evaluated against the device's stored thresholds; breaches write to an `alerts` collection and broadcast to the dashboard in real time. Adds the fuel-theft rule. Kills the "everything is an anomaly" bug.

## Structure & steps

### Files created

| File | Purpose |
|---|---|
| `backend/alerts.py` | Threshold cache, cooldown, `evaluate()`, fuel-theft buffer |
| `backend/routers/alerts.py` | `GET /devices/{id}/alerts` endpoint |

### Files modified

| File | Change |
|---|---|
| `backend/main.py` | Call `alerts.evaluate()` in `_on_mqtt_message`; unmount `anomaly.router`; mount `alerts.router` |
| `frontend/src/api.js` | Add `getAlerts()`; remove `getAnomalies()` |
| `frontend/src/components/AlertsPanel.jsx` | Poll `/alerts`; render `alert_type`, `severity`, `detail`, value + unit, time |

### Files NOT touched

| File | Reason |
|---|---|
| `backend/routers/anomaly.py` | Unmount only — do not delete (spec §0) |
| `simulator.py` | `fuel_level` + `load_current` already added in Phase A |

### Ordered steps

1. **`backend/alerts.py`**
   - In-memory threshold cache: `{device_id: {sensor: {min, max}}}` — loaded from Mongo on first access, TTL-refreshed every 60 s.
   - Cooldown dict: `{(device_id, sensor, alert_type): last_fired_ts}` — suppress re-fires within 600 s.
   - Fuel-theft buffer: per-device deque of `(ts_ms, fuel_level)` readings; evaluated against `_last_known` `load_current`.
   - `evaluate(device_id, sensor, value, unit, ts, last_known)` → alert dict or `None`.
   - Threshold rule: `value < min` → warning; `value > max` → critical for `load_current`/`temperature`, else warning.
   - Fuel-theft rule: `fuel_level` drop > `THEFT_DROP_L` (5 L) within `THEFT_WINDOW_S` (300 s) while `load_current < OFF_AMPS` (2 A) → critical `fuel_theft` alert.
   - Alert dict shape matches `alerts` collection schema from MVP_v2_PLAN.md §1: includes `message_en`, `message_ur`, `whatsapp_sent: False`.

2. **`backend/routers/alerts.py`**
   - `GET /devices/{device_id}/alerts?limit=50&since=<iso_ts>` — queries `alerts` collection, most-recent first.

3. **`backend/main.py`**
   - In `_on_mqtt_message`: after updating `_last_known`, call `alerts.evaluate(...)` (non-blocking; schedule async Mongo write via `run_coroutine_threadsafe`).
   - Unmount `anomaly.router`; mount `alerts.router` at `/devices`.

4. **`frontend/src/api.js`**
   - Add `getAlerts(deviceId, limit)`.
   - Remove `getAnomalies`.

5. **`frontend/src/components/AlertsPanel.jsx`**
   - Poll `getAlerts` every 30 s (same cadence as before).
   - Render per alert: severity colour (`warning` → amber, `critical` → red), `alert_type` tag, sensor name, value + unit, `detail` string, timestamp.

## Start commands

Same stack as Phase A — no new services. Run each in a separate terminal from `D:\TwinLab`:

```powershell
# Terminal 1 — Docker (already running — leave it)
docker compose up -d

# Terminal 2 — Ingestion (already running — leave it)
.venv\Scripts\python ingestion.py

# Terminal 3 — Simulator (already running — leave it)
.venv\Scripts\python simulator.py

# Terminal 4 — Backend (uvicorn --reload picks up all backend changes automatically)
cd backend
..\\.venv\Scripts\uvicorn main:app --reload --port 8000

# Terminal 5 — Dashboard (Vite HMR picks up all frontend changes automatically)
cd frontend
npm run dev
```

> No restarts needed mid-phase — `--reload` and Vite HMR handle all file changes.

## Expected outcome

**Acceptance checks (from MVP_v2_PLAN.md §3):**

- [ ] Set `temperature.max = 40` on a device, push a 45 °C reading → exactly one `threshold` alert written to Mongo and shown in the panel. A second 45 °C within 10 min does **not** create a duplicate (cooldown).
- [ ] With `load_current < 2 A` (generator off), drop `fuel_level` by 8 L within 2 min → one `fuel_theft` critical alert.
- [ ] With `load_current > 2 A` (generator running), normal fuel burn does **not** trigger fuel-theft.
- [ ] `/anomalies` is no longer referenced anywhere in the frontend (`grep -r anomalies frontend/src` → empty).
- [ ] `GET /devices/{id}/alerts` returns alert documents with `alert_type`, `severity`, `detail`, `message_en`, `message_ur`.

---

## ✅ Actually achieved

**Shipped:**
- `backend/alerts.py`: in-memory threshold cache (60 s TTL, `cache_loop()` background task), cooldown dict (600 s), fuel-theft buffer (deque per device, 300 s window, 5 L drop threshold, requires `load_current < 2 A`). `evaluate()` is fully synchronous — safe to call from the MQTT daemon thread. Alert dict matches the `alerts` collection schema from MVP_v2_PLAN.md §1; `message_en` + `message_ur` generated at fire time; `whatsapp_sent: False` (Phase C).
- `backend/routers/alerts.py`: `GET /devices/{id}/alerts?limit=&since=` — Motor query, sorted most-recent first, datetime serialised to ISO string.
- `backend/main.py`: `_persist_alert()` coroutine writes to `alerts` collection and broadcasts alert over WS. `cache_loop()` started as asyncio task in lifespan. `anomaly.router` unmounted; `alerts_router` mounted at `/devices`.
- `frontend/src/api.js`: `getAnomalies` removed, `getAlerts` added.
- `frontend/src/components/AlertsPanel.jsx`: polls `getAlerts` every 30 s. Renders `alert-item--critical` (red) / `alert-item--warning` (amber) variants, `alert-type-tag` badge (`threshold` / `fuel theft`), `detail` line, value + unit, timestamp.
- `frontend/src/App.css`: `.alert-item--critical`, `.alert-item--warning`, `.alert-top-row`, `.alert-type-tag--threshold`, `.alert-type-tag--fuel_theft`, `.alert-detail`.

**Deviations from plan:** none.

**Deferrals:** none — all Phase B scope shipped.

**Gotchas:**
- `anomaly.py` still exists on disk (not deleted) as per spec §0. It is unmounted — it will not appear in Swagger or fire on any route.
- Threshold cache starts empty; first refresh happens at lifespan startup before MQTT connects, so the first readings after startup are evaluated against live thresholds. No startup race.
- `_persist_alert` does `dict(alert)` before insert to avoid Motor mutating the original dict with `_id`.
