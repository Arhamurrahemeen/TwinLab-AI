# Phase 15 — Device onboarding: MAC-derived hardware IDs, sensor picker, delete UI

## Goal

Cut the manual friction in adding/removing devices. Hardware boards no longer need a
per-device `DEVICE_ID` edit + rebuild before flashing (ID is derived from the chip's
MAC at boot); the dashboard gets a sensor checkbox picker instead of free-text, a
discover-unregistered-devices helper, and a delete action (previously API-only).

## Structure & steps

**Firmware** (`firmware/twinlab_node_v1/main/main.c`, `main/secrets.h.example`):
- Drop `DEVICE_ID` from `secrets.h` — one shared file now works for every board at a site.
- Compute `g_device_id` (`"TL-%02X%02X%02X"` from the last 3 bytes of `esp_efuse_mac_get_default()`) once at boot.
- `pub_reading()` topic and the MQTT CONNECT client-id switch from compile-time `DEVICE_ID` string concat to runtime `snprintf`/`g_device_id`.
- `mqtt_selftest()`'s buffer-size assert switches to a fixed `DEVICE_ID_MAXLEN` (9 — `"TL-"` + 6 hex chars) instead of the old compile-time macro length.

**Backend** (`backend/main.py`):
- `GET /devices/discover` — device IDs present in the existing `_last_known` in-memory cache (already populated by the MQTT bridge) with no matching `db.devices` doc. No new state.

**Frontend**:
- `frontend/src/api.js` — add `deleteDevice(id)`, `discoverDevices()`.
- `frontend/src/components/RegisterDevice.jsx` — sensor checkboxes instead of comma text, scoped by `source` (hardware: temperature/humidity/accel_x/y/z/vibration; simulator: adds fuel_level/load_current); when source=hardware, a dropdown of `discoverDevices()` results autofills `device_id`.
- `frontend/src/components/EditDevice.jsx` — same sensor checkbox treatment, scoped by the selected `source`.
- `frontend/src/components/DeviceList.jsx` — 🗑 delete icon next to the existing ✎ edit icon, `window.confirm` then `DELETE /devices/{id}`.

## Start commands

```powershell
docker compose up -d
.venv\Scripts\python ingestion.py
cd backend; ..\.venv\Scripts\uvicorn main:app --reload --host 0.0.0.0 --port 8000
cd frontend; npm run dev

# Firmware (ESP-IDF shell, from firmware\twinlab_node_v1\)
#   secrets.h no longer needs DEVICE_ID — WIFI_SSID/PASSWORD/MQTT_HOST/MQTT_PORT only
idf.py build
idf.py -p COM<N> flash
mosquitto_sub -h localhost -t "twinlab/#" -v   # confirm MAC-derived TL-xxxxxx topic
```

## Expected outcome

- [ ] Same firmware binary flashed to any board publishes under a unique `TL-<MAC suffix>` ID with no `secrets.h` edit.
- [ ] `mqtt_selftest()` still passes at boot (buffer-size assert holds with the fixed max ID length).
- [ ] `GET /devices/discover` returns live-but-unregistered device IDs.
- [ ] Dashboard "Register Device" shows a discovered-ID dropdown for hardware source, and sensor checkboxes (scoped by source) instead of a text field.
- [ ] Edit modal's sensor field is also a checkbox picker.
- [ ] Each device card has a working delete (🗑) action with a confirm prompt.

---
## ✅ Actually achieved

All items shipped and verified end to end:

- Firmware rebuilt and flashed to the bench ESP32 (previously `TL-02`). Boot log confirms
  `device id: TL-B49244` (from `esp_efuse_mac_get_default()`, MAC `1c:c3:ab:b4:92:44`),
  `mqtt_selftest()`/`dht_selftest()` pass (no asserts fired), and it connects to the broker
  and publishes on `twinlab/device/TL-B49244/sensor/*` with real, plausible readings —
  confirmed live via `mosquitto_sub`.
- `GET /devices/discover` returns `["TL-B49244"]` — correctly excludes registered IDs.
  Had to place this route in `main.py` *before* `app.include_router(devices.router, ...)`,
  since `/devices/discover` has the same path shape as `devices.router`'s
  `GET /devices/{device_id}` and Starlette matches routes in registration order — a plain
  append at the bottom of the file would have silently 404'd (well, matched the wrong
  handler and returned "device not found").
- Frontend (`RegisterDevice.jsx`, `EditDevice.jsx`, `DeviceList.jsx`, new `sensor-options.js`,
  `api.js` additions) builds clean (`vite build`). Sensor picker is a checkbox group scoped
  by `source` (hardware: 6 real sensors; simulator: +`fuel_level`/`load_current`); switching
  source drops now-invalid sensors from the selection automatically. Delete is a 🗑 icon next
  to ✎ with `window.confirm`. Not visually verified in a browser this session — the Claude in
  Chrome extension wasn't connected — verified via `vite build` + reading the rendered JSX only.

**Deferred / not done:**
- Option C (full SoftAP/BLE provisioning) explicitly out of scope, per the brainstorming
  discussion — Option A was chosen as sufficient for now.

**Follow-up verification, 2026-09-13 (browser, via Claude in Chrome):**
- `TL-02` was already gone from the registry by this point — nothing to clean up; only
  `NFL-SITE-GEN-01`, `NFL-FSD-CHILL-03`, `TL-B49244` exist, and `TL-B49244` is registered.
- Sensor picker confirmed live: switching Source to Hardware in Register Device drops
  `fuel_level`/`load_current` and leaves the 6 real sensors, exactly as scoped.
- Discover-dropdown confirmed live end to end: published a throwaway MQTT reading for a
  fake `TL-TEST99` device, `GET /devices/discover` picked it up, the modal's "OR PICK A
  LIVE UNREGISTERED DEVICE" dropdown appeared (it's conditionally rendered only when
  `discovered.length > 0` — correct, not a bug) and selecting it autofilled Device ID.
  Closed the modal without submitting; `TL-TEST99` was never persisted (it only ever
  lived in the backend's in-memory `_last_known` cache).
- Delete button's presence confirmed via the accessibility tree (`"Delete device"`
  button on each card); did not click it to avoid triggering the `window.confirm`
  dialog Claude-in-Chrome can't dismiss.
- Dashboard live data + alert feed also incidentally re-confirmed working (LIVE badge,
  charts updating, alert list populated) while doing the above.
