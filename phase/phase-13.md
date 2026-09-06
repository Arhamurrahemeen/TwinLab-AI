# Phase 13 — Hardware node: ESP-IDF firmware merge (Phase E revived)

## Goal

Merge the tested standalone ESP-IDF sensor firmware (`D:\ESP_IDF\MPU6050_DHT22` — MPU6050 + DHT22, proven on the bench) into the TwinLab pipeline as a real device `TL-01`. Strip the firmware's standalone SoftAP + 3D-cube web demo, add WiFi-station + MQTT publish on the locked topic contract, and confirm the existing source-agnostic pipeline ingests, charts, and alerts on hardware readings with **zero backend/frontend code changes**. This completes the MVP hardware story for the BanoQabil / Alibaba Cloud hackathon submission.

Supersedes the deferred Phase E (`phase-9.md`), which scaffolded an untested Arduino firmware. That scaffold is deleted; ESP-IDF is now the firmware framework (decided with Arham, CLAUDE.md §2 "change requires a conversation").

Out of scope (unchanged from phase-9 / CLAUDE.md §6): run/stop detection, software hour meter, vibration-RMS **alert rule**, fuel sensor, CT clamp. `vibration` is published as a raw passthrough **value** only (dashboard already renders it) — no alert rule keys off it.

## Structure & steps

**1. Preserve the demo, then move the project.**
- `git tag esp-idf-softap-demo` on the current tree first is not possible (the ESP-IDF project isn't in this repo). Instead: keep `D:\ESP_IDF\MPU6050_DHT22` intact on disk as the demo copy; the repo gets the stripped version.
- Delete the 6 Arduino files under `firmware/twinlab_node_v1/`.
- Copy into `firmware/twinlab_node_v1/`: `CMakeLists.txt`, `main/CMakeLists.txt`, `main/main.c`. **Exclude** `build/`, `sdkconfig`, `sdkconfig.old`, `.vscode/`, `main/index.html`, `main/three.min.js`.

**2. Strip `main/main.c` (demo scaffolding — not the sensor code):**
- SoftAP + `esp_http_server`: `net_start()` httpd half, `page_get`, `three_js_get`, `telemetry_get`, `ws_get`, `ws_push`, `ws_fd`, `telemetry_buf`, the `EMBED_FILES` externs.
- Mahony AHRS block: `q0..q3`, `ib_*`, `ahrs_reset/seed/update`, `quat_mul`, `quat_to_euler`, `ahrs_selftest`, `gx0/gy0/gz0` seed use, `qref_inv` logic, all `AHRS_*` / `ACC_LSB`-orientation `#define`s not needed for raw accel.
- Firmware-side health: `health_status`, `sev`, `VIB_WARN/ALERT`, `TEMP_WARN/ALERT`, `HUM_WARN/ALERT`. Thresholds are the backend alert engine's job.
- `AP_SSID` / `AP_PASS`.

**3. Keep verbatim (the tested asset):**
- `line_pulled_up` bus check, I2C master bus + device setup, MPU register config (`0x6B/0x1A/0x19/0x1B/0x1C`), `calibrate()` (gyro bias + boot diagnostics), the 14-byte burst read + scaling to g, `mpu_w`/`mpu_r`.
- DHT22: `dht_decode`, `dht_wait`, `dht_read`, `dht_task`, `dht_selftest`, `dht_mux`.
- `g_vib` EMA: `g_vib += (1/SAMPLE_HZ) * (fabsf(amag - 1.0f) - g_vib);`

**4. Add to `main/main.c` (~80 lines):**
- `#include "secrets.h"` (gitignored).
- WiFi STA: `esp_netif_create_default_wifi_sta()`, event handler (got-IP + disconnect→retry), block until connected.
- SNTP: `esp_sntp` / `esp_netif_sntp`, wait for year > 2020, so `ts` is real unix epoch ms (contract §4). Mirrors the old Arduino scaffold's NTP step.
- ~~`esp-mqtt`~~ **(revised — see Actually achieved):** esp-mqtt isn't populated in this IDF install. Replaced with a hand-rolled publish-only MQTT-over-TCP client (lwip sockets).
- `pub_reading(const char *sensor, float value, const char *unit)`:
  - topic: `twinlab/device/" DEVICE_ID "/sensor/<sensor>`
  - payload: `{"value":<.2f>,"unit":"<unit>","ts":<epoch_ms>}`
  - `esp_mqtt_client_publish(..., qos=0, retain=0)`
- `payload_selftest()` — format a known reading, `assert` the exact JSON string. Replaces the removed `ahrs_selftest()` call in `app_main`. `dht_selftest()` stays.

**5. Publish loop (replaces the `ws_push` cadence):**
- `temperature` (unit `C`) + `humidity` (unit `%`) every 5000 ms — from `g_temp` / `g_hum` (skip publish while NaN, i.e. before first good DHT read).
- `accel_x` / `accel_y` / `accel_z` (unit `g`) + `vibration` (unit `g`, the `g_vib` value) every 1000 ms.
- `DEVICE_ID` = `TL-01`.

**6. `main/CMakeLists.txt`:**
- Drop `EMBED_FILES "index.html" "three.min.js"`, drop `esp_http_server`.
- `REQUIRES`: `esp_driver_i2c esp_driver_gpio esp_wifi esp_netif nvs_flash esp_timer esp_event mqtt esp-tls` (+ `esp_sntp` if the SNTP API used needs it explicitly — resolve at build).

**7. Config files:**
- `firmware/twinlab_node_v1/main/secrets.h.example`:
  ```c
  #pragma once
  #define WIFI_SSID     "your-wifi"
  #define WIFI_PASSWORD "your-pass"
  #define MQTT_HOST     "192.168.1.100"   // laptop LAN IP, never localhost
  #define MQTT_PORT     1883
  #define DEVICE_ID     "TL-01"
  ```
- `.gitignore`: replace the `firmware/twinlab_node_v1/Config.h` line with:
  ```
  firmware/twinlab_node_v1/build/
  firmware/twinlab_node_v1/sdkconfig
  firmware/twinlab_node_v1/sdkconfig.old
  firmware/twinlab_node_v1/main/secrets.h
  ```

**8. Register `TL-01` (no backend code — schema is source-agnostic, confirmed in `models/device.py` + `alerts.py`):**
```powershell
Invoke-RestMethod -Uri http://localhost:8000/devices -Method Post -ContentType "application/json" -Body '{"device_id":"TL-01","name":"TwinLab Bench Node 1","location":"Bench","sensors":["temperature","humidity","accel_x","accel_y","accel_z","vibration"],"source":"hardware","thresholds":{"temperature":{"min":null,"max":55}},"status":"active"}'
```

**9. CLAUDE.md edits:**
- §2 firmware row → ESP-IDF 6 (`idf.py`), path `firmware/twinlab_node_v1/`, drop the Arduino/PubSubClient/Adafruit library list.
- §3 firmware section → `idf.py set-target esp32` / `idf.py build` / `idf.py -p COM<N> flash monitor`; `Config.h` → `secrets.h`.
- §6 vibration note → `vibration` allowed as a raw passthrough **value** (no alert rule); everything else in that bullet stands.
- §8 table: Phase E status ⏸ → ✅ (revived by Phase 13); add Phase 13 row.
- Footer "Last updated" line.

## Start commands

```powershell
# Terminal 1 — infra
docker compose up -d

# Terminal 2 — ingestion
.venv\Scripts\python ingestion.py

# Terminal 3 — backend
cd backend; ..\.venv\Scripts\uvicorn main:app --reload --port 8000

# Terminal 4 — dashboard
cd frontend; npm run dev

# Terminal 5 — confirm broker reachable from the LAN before flashing
mosquitto_sub -h localhost -t "twinlab/#" -v

# Firmware (ESP-IDF shell, from firmware\twinlab_node_v1\)
#   copy main\secrets.h.example -> main\secrets.h, fill in WiFi + laptop LAN IP
idf.py set-target esp32
idf.py build
idf.py -p COM<N> flash monitor

# Register the device (PowerShell) — see step 8 body above
```

## Expected outcome

Acceptance checks:

- [x] `idf.py build` succeeds from a clean tree with only `main/secrets.h` added. *(verified this session — ESP-IDF 6.0.2, zero warnings, 819 KB bin)*
- [ ] `mosquitto_sub -t "twinlab/#" -v` shows live `TL-01` messages on the locked contract (`twinlab/device/TL-01/sensor/{temperature,humidity,accel_x,accel_y,accel_z,vibration}`), `ts` a plausible epoch-ms, **before** the backend is involved.
- [ ] `TL-01` appears on the React dashboard with live temperature / humidity / vibration charts updating — same render path as a simulator device, no special-casing.
- [ ] Warming the DHT22 (hairdryer) past 55 °C, or temporarily lowering the threshold, fires a genuine threshold alert in the Alerts panel — proving the alert engine runs on hardware-sourced readings.
- [ ] Simulator/NFL devices unaffected while `TL-01` publishes (confirms source-agnostic ingestion held).
- [ ] `dht_selftest()` + `mqtt_selftest()` pass at boot (serial log).
- [ ] `git status` shows no `build/`, `sdkconfig`, or `secrets.h` staged.

---
## ✅ Actually achieved

**Build verified.** `idf.py set-target esp32 && idf.py build` on the actual toolchain (`C:\esp\v6.0.2\esp-idf`, ESP-IDF 6.0.2) → exit 0, `main.c` compiles with **zero warnings**, `twinlab_node_v1.bin` = 0xc7f30 (819 KB), 22 % free in the app partition. Not yet flashed / no hardware run.

**Shipped:**
- Deleted the 6 untested Arduino scaffold files under `firmware/twinlab_node_v1/`.
- `firmware/twinlab_node_v1/CMakeLists.txt` + `main/CMakeLists.txt` — ESP-IDF 6 project, `MINIMAL_BUILD ON`, `REQUIRES esp_driver_i2c esp_driver_gpio esp_wifi esp_netif esp_event nvs_flash esp_timer lwip` (no `EMBED_FILES`, no `esp_http_server`, **no `mqtt`** — see the pivot below).
- `firmware/twinlab_node_v1/main/main.c` — one flat file:
  - **Carried over verbatim** from the bench-tested standalone firmware: `line_pulled_up` bus check, I2C master bus + device setup, MPU register config (`0x6B/0x1A/0x19/0x1B/0x1C`), the 14-byte burst read, `mpu_w`/`mpu_r`, the full DHT22 bit-bang (`dht_decode`/`dht_wait`/`dht_read`/`dht_task`/`dht_mux`), `dht_selftest`, and the `g_vib` EMA (`fabsf(amag-1)` low-passed).
  - **Removed** (standalone-demo scaffolding): SoftAP + `esp_http_server` + all `httpd`/`ws_*`/`telemetry_*`, `EMBED_FILES` externs, the entire Mahony AHRS block (`q0..q3`, `ahrs_*`, `quat_*`, `quat_to_euler`, `ahrs_selftest`, `qref_inv`), `calibrate()` (only fed the AHRS + gyro, neither of which survives), firmware-side `health_status`/`sev` + all threshold `#define`s.
  - **Added**: WiFi-STA (`wifi_start` — event handler, auto-reconnect, blocks on `WIFI_CONNECTED_BIT`), SNTP via `esp_netif_sntp` (`sntp_sync`, 15 s wait, warns but continues on timeout), a hand-rolled publish-only MQTT-3.1.1-over-TCP client (`mqtt_encode_len`/`mqtt_connect_broker`/`mqtt_ensure`/`mqtt_publish` over lwip sockets — CONNECT + QoS-0 PUBLISH, keepalive 0, reconnect on any send failure), `pub_reading(sensor, value, unit)` → topic `twinlab/device/TL-01/sensor/<sensor>` + payload `{"value":<.2f>,"unit":"<unit>","ts":<epoch_ms>}`, `epoch_ms()` via `gettimeofday`, and `mqtt_selftest()` (asserts the length-varint encoder + the exact JSON wire string + publish-buffer headroom at boot — replaces the removed `ahrs_selftest()` call).
  - Publish loop: `mqtt_ensure()` at the top; `accel_x/y/z` + `vibration` (unit `g`) every 1000 ms; `temperature` (`C`) + `humidity` (`%`) every 5000 ms, skipped while the DHT reading is still NaN. Intervals timed off `esp_timer_get_time()` (monotonic), not wall clock.
- `firmware/twinlab_node_v1/main/secrets.h.example` — `WIFI_SSID`, `WIFI_PASSWORD`, `MQTT_HOST`, `MQTT_PORT`, `DEVICE_ID`.
- `.gitignore` — dropped `firmware/twinlab_node_v1/Config.h`, added `main/secrets.h`, `build/`, `sdkconfig`, `sdkconfig.old`.
- `CLAUDE.md` — §2 firmware row (ESP-IDF 6, `idf.py`, TL-01 sensor list, hand-rolled MQTT note), §3 firmware section (`idf.py` commands, `secrets.h`, `MQTT_HOST`), §6 (`vibration` = raw passthrough value, no alert rule; "Phase E's sensor set" → "phase-13.md"), §8 table (Phase E → revived; new Phase 13 row ✅), footer "Last updated".
- `three.min.js` / `index.html` from the demo firmware were **not** copied into the repo.

**Deviations from plan:**
- **esp-mqtt dropped.** The plan assumed `esp-mqtt` was in-tree. On this ESP-IDF 6.0.2 install `components/mqtt/` contains only `test_apps` (no source — the submodule/managed component isn't populated), and `idf.py add-dependency espressif/esp_mqtt` returns "not found" (no such name on the registry / no network). Rather than have Arham repair the IDF install the night before submission, MQTT is now a ~70-line publish-only client over a raw lwip TCP socket. Zero external/managed deps; only needs `lwip` (always present). Trade-off: hand-rolled protocol bytes instead of a library — mitigated by `mqtt_selftest()` pinning the packet encoder at boot.
- **Reconnect runs inline in the sensor loop** (`ponytail:` comment in the code). A dead broker stalls MPU reads for up to the 5 s socket timeout. Fine at 1 Hz publish; promote to its own task if loop timing ever matters.
- Plan step 1's `git tag` on the demo — not done (no commits made; the standalone firmware stays intact at `D:\ESP_IDF\MPU6050_DHT22` as the preserved demo copy). Tag it manually if wanted.
- `calibrate()` dropped entirely — it only fed the (removed) AHRS seed + gyro-bias subtraction. Published accel is raw. Saves a 1.2 s boot delay.
- Loop still issues the full 14-byte MPU burst read (tested path) even though only the 6 accel bytes are used.

**Backend / frontend:** zero changes, as predicted. `models/device.py` already accepts `source:"hardware"` and any sensor list; `alerts.py` `evaluate()` no-ops on a sensor with no threshold entry (`vibration`), so it can't spuriously fire; `ingestion.py` + WS bridge are `twinlab/#` wildcard. Confirmed by reading all three.

**NOT verified — no hardware run from this session:**
- Not flashed. No `mosquitto_sub` capture, no dashboard/alert walkthrough.
- Runtime unknowns: MPU6050 wiring on this board (tested in the demo firmware, same GPIOs), whether Mosquitto accepts `keepalive: 0` without a `max_keepalive` override (default config does), NTP reachability on the demo WiFi.

**Next steps for Arham (≈10 min):**
1. Edit `firmware/twinlab_node_v1/main/secrets.h` (already created with placeholders) — real WiFi SSID/pass + laptop LAN IP in `MQTT_HOST`.
2. ESP-IDF shell in `firmware/twinlab_node_v1/`: `idf.py -p COM<N> flash monitor` (build is already current). Watch for `mqtt_selftest`/`dht_selftest` passing, `got IP`, `mqtt connected to <ip>:1883`.
3. `mosquitto_sub -h localhost -t "twinlab/#" -v` — confirm the 6 topics with plausible epoch-ms `ts`.
4. `POST /devices` for `TL-01` (body in step 8 above), then check the dashboard + a hairdryer-triggered temperature alert.
5. Tick the acceptance checkboxes above.
