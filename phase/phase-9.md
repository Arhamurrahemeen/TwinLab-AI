# Phase 9 — Hardware buffer (ESP32 real sensors) + brand string fix

## Goal

Prove the existing pipeline ingests **real ESP32 data**, not just simulated data, without touching anything else. Register one hardware device (`TL-01`) publishing `temperature`, `humidity`, `accel_x`, `accel_y`, `accel_z` on the locked MQTT contract, and confirm it renders and alerts identically to a simulator device. Also clear the stale "TwinLab AI" brand string. This is a **buffer phase** — do not let it block the pitch-critical demo; if hardware misbehaves, the simulator remains the fallback demo path.

Explicitly out of scope for this phase (see CLAUDE.md §6): run/stop detection, software hour meter, vibration-RMS alert rule, fuel sensor, CT clamp. Those need their own phase doc and a scope decision from Arham first.

## Structure & steps

**Firmware (new directory, first firmware in the repo — none exists today):**

1. Scaffold `firmware/twinlab_node_v1/`:
   - `twinlab_node_v1.ino` — setup/loop entry point
   - `Config.h.example` — `WIFI_SSID`, `WIFI_PASSWORD`, `MQTT_HOST` (laptop LAN IP), `DEVICE_ID = "TL-01"`. Real `Config.h` gitignored.
   - `Sensors.h/.cpp` — DHT22 + MPU6050 read wrappers
   - `Mqtt.h/.cpp` — WiFi connect, MQTT connect/reconnect (exponential backoff), publish helper
2. Wiring (bench): DHT22 DATA → GPIO4 with 10kΩ pull-up to 3V3. MPU6050 SDA → GPIO21, SCL → GPIO22 (default `Wire.begin()` pins, no remap). Both sensors share ESP32 3V3/GND.
3. Publish loop, matching the locked topic contract exactly:
   - `twinlab/device/TL-01/sensor/temperature` → `{value, unit:"C", ts}` every 5s
   - `twinlab/device/TL-01/sensor/humidity` → `{value, unit:"%", ts}` every 5s
   - `twinlab/device/TL-01/sensor/accel_x|accel_y|accel_z` → `{value, unit:"g", ts}` every 1s
   - `ts` in milliseconds, per contract (CLAUDE.md §4).
4. Bench-verify with `mosquitto_sub -t "twinlab/#" -v` before touching the backend at all.

**Backend (no code changes expected — registry-driven core from Phase A already handles new devices):**

5. Register `TL-01` via existing `POST /devices`:
   ```json
   {
     "device_id": "TL-01",
     "name": "TwinLab Bench Node 1",
     "location": "Arham desk",
     "sensors": ["temperature", "humidity", "accel_x", "accel_y", "accel_z"],
     "source": "hardware",
     "thresholds": { "temperature": { "min": null, "max": 55 } },
     "status": "active"
   }
   ```
   Threshold value (55°C) is a starting guess for enclosure over-temp, not a validated field number — expect to retune once TL-01 has a week of real ambient data.
6. If `POST /devices` rejects `source: "hardware"` for any reason, that's a real bug to fix — Phase A's schema is supposed to be source-agnostic. Do not add a special-cased branch; fix the schema/validation instead.

**Brand fix:**

7. `grep -ril "twinlab ai" .` across the repo (excluding `.git`, `node_modules`). Expect at minimum `backend/routers/chat.py`'s system prompt. Fix every hit to "TwinLab".
8. Confirm no `Gemini` references remain in any active code path (CLAUDE.md §2 — Gemini is not used).

## Start commands

```powershell
# Terminal 1 — infra
docker compose up -d

# Terminal 2 — ingestion
.venv\Scripts\python ingestion.py

# Terminal 3 — backend
cd backend
..\.venv\Scripts\uvicorn main:app --reload --port 8000

# Terminal 4 — dashboard
cd frontend
npm run dev

# Terminal 5 — firmware bench check (before flashing, confirm broker reachable)
mosquitto_sub -h localhost -t "twinlab/#" -v

# Flash + monitor (see CLAUDE.md §3 for full firmware commands)
arduino-cli compile --fqbn esp32:esp32:esp32 firmware\twinlab_node_v1
arduino-cli upload -p COM<N> --fqbn esp32:esp32:esp32 firmware\twinlab_node_v1
arduino-cli monitor -p COM<N> -c baudrate=115200

# Register TL-01 (PowerShell, adjust body as needed)
Invoke-RestMethod -Uri http://localhost:8000/devices -Method Post -ContentType "application/json" -Body '{"device_id":"TL-01","name":"TwinLab Bench Node 1","location":"Arham desk","sensors":["temperature","humidity","accel_x","accel_y","accel_z"],"source":"hardware","thresholds":{"temperature":{"min":null,"max":55}},"status":"active"}'
```

## Expected outcome

Acceptance checks (from `MVP_v2_PLAN.md` §6, unchanged):

- [ ] `mosquitto_sub -t "twinlab/#"` shows live TL-01 messages on the locked topic contract before the backend is even involved.
- [ ] `TL-01` appears on the React dashboard as a device, live temperature/humidity/vibration charts update in real time — same rendering path as a simulator device, no special-casing.
- [ ] Forcing a real over-temperature (hairdryer near the DHT22, or lowering the threshold temporarily) raises a genuine threshold alert in the Alerts panel — proving the alert engine works on hardware-sourced readings, not just simulated ones.
- [ ] `grep -ri "twinlab ai"` returns zero hits in active source/UI strings.
- [ ] No Gemini references in any active code path.
- [ ] Simulator devices are unaffected — `TL-01` coexisting with simulator devices doesn't change simulator behavior (confirms `source`-agnostic ingestion held).

---
## ✅ Actually achieved
<!-- Fill in after the phase is done — don't mark this phase ✅ in CLAUDE.md §8 until this section exists. -->
