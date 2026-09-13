# CLAUDE.md — TwinLab

> Read automatically by Claude Code at the start of every session in this directory.
> This file holds **durable, always-true** context only. Phase detail lives in `phase/`.
> Keep this file lean. When something here goes stale, fix it — don't append.

---

## 1. What TwinLab is

**TwinLab** (one word, capital T and L — *not* "TwinLab AI") is a **Predictive Maintenance (PdM) platform** — non-invasive condition monitoring + a live digital-twin view of critical machines, bilingual push alerts on its own Android app, priced in PKR. Category corrected from "SCAPM" 2026-09-05 (see vault `TwinLab_Identity.md`) — PdM is the term the real comparables (Augury, Petasense) use; "digital twin" is a feature, not the category. Architecturally **APM 4.0** (wireless condition monitoring + standalone cloud, non-invasive install) vs the incumbents' APM 3.0 (Siemens MindSphere / GE Predix / IBM Maximo / PTC ThingWorx — deep OT integration required). Built by **OmniteX** (Pakistan; founder Muhammad Arham Rajput). Currently past Phase 15 of the MVP v2 rebuild, pitching NIC Karachi (Final Round) and SEIC Karachi (Cohort 2).

**Lead vertical: Textile + FMCG enterprise plants** (locked 2026-08-10) — SCADA-present plants with non-invasive coverage gaps (gensets, compressors, chillers, HVAC, older lines), running in parallel with an SME feeder motion (hospitals, guest houses, general SME). **Genset is a supported machine and feeder/service offering, no longer the wedge.**

- **TwinLab Pro** — asset monitoring for SME / asset-heavy operations and enterprise Textile+FMCG plants.
- **TwinLab Edu** — parked; relaunched as its own venture, **OmniTwin** (see vault `OmniTwin/Overview.md`). Not a TwinLab track anymore.

**One-line buyer pitch:** *"We give Pakistani industries and SMEs a digital-twin view of their critical machines — non-invasive sensors that flag failure before it happens, pushed to your phone in Roman Urdu. PKR-billed, not USD-billed."*

**Who buys vs who uses:** the owner is the **buyer, not the user** — he receives **push notifications on the TwinLab Android app** (`android/`), which is also where he sees the asset list and per-asset digital twin. The web dashboard is for the maintenance/ops head or owner's son. Design for both separately. (Twilio/WhatsApp was the alert channel through Phase C–G; retired in Phase 14.)

**The real competitor is the spreadsheet and the ledger** — not Siemens / GE Predix / AVEVA. Never frame TwinLab as a cheap Western-platform clone. We **complement** existing workflows; we never ask the owner to change how he works.

---

## 2. Tech stack (locked — change requires a conversation, not a commit)

| Layer | Choice |
|---|---|
| Hardware (real) | ESP32 + DHT22 (temp/humidity) + MPU6050 (accel/vibration). Firmware is **ESP-IDF 6.x** (`idf.py`), lives in `firmware/twinlab_node_v1/`. Publishes `temperature`, `humidity`, `accel_x/y/z`, `vibration` on the MQTT contract via a hand-rolled publish-only MQTT-over-TCP client (no esp-mqtt dependency). **Device ID is MAC-derived at boot** (Phase 15) — no per-board `secrets.h` edit; the bench unit currently publishes as `TL-B49244`, flashed and verified live. **No fuel sensor, no CT clamp owned yet** — fuel-theft and load-current stay simulator-only until those parts are bought. |
| Messaging | MQTT via **Mosquitto** |
| Buyer app | Native **Kotlin + Jetpack Compose** (Material 3), `android/`, single Gradle module, package `com.omnitex.twinlab`. Ktor client (REST + WebSocket), kotlinx.serialization, DataStore. Asset dashboard + per-asset live detail + Compose-Canvas digital twin. Talks to the backend over the LAN (base URL set in a Settings screen). JVM unit tests only. |
| Time-series DB | **InfluxDB 2.7** (sensor readings) |
| Document DB | **MongoDB 7.0** (device registry, thresholds, alerts, sim control) |
| Backend | **FastAPI** (Python) |
| AI — chat | **Groq** `llama-3.3-70b-versatile` (Urdu / Roman Urdu / English) |
| Alerts | **Firebase Cloud Messaging** push to the TwinLab Android app, bilingual (EN + Roman Urdu) + rupee-anchored. Broadcast to every registered device token (`push_tokens` collection); `backend/push.py` + `backend/routers/push.py`. Graceful no-op when `fcm_credentials_file` is unset. Twilio/WhatsApp retired in Phase 14. |
| Frontend | **React + Vite** (recharts) |
| Sim control | Separate **Vite** mini-app, same FastAPI backend |
| Deploy | Docker Compose (dev) |

**AI policy:** Groq is the only LLM. **Gemini is not used** (rate limits). **Isolation Forest is parked** for the MVP — alerting is threshold + fuel-theft rule. RUL stays **rule-based** (no trained LSTM).

**Alert transport:** FCM push only (Phase 14). `firebase-admin` in `requirements.txt`; `FCM_CREDENTIALS_FILE` in `backend/.env` points at the service-account JSON (`backend/fcm-service-account.json`, gitignored). No Twilio — do not reintroduce it.

---

## 3. Run everything (Windows, from repo root `D:\TwinLab_v2`)

```powershell
docker compose up -d                                   # Mosquitto + InfluxDB + MongoDB
.venv\Scripts\python ingestion.py                      # MQTT -> InfluxDB
.venv\Scripts\python simulator.py                      # registry-driven sim publisher
cd backend && ..\.venv\Scripts\uvicorn main:app --reload --host 0.0.0.0 --port 8000
cd frontend && npm run dev                              # dashboard  http://localhost:5173
cd sim-control && npm run dev                           # sim control mini-app (Phase D+)
```

> `--host 0.0.0.0` matters once the Android app is in the loop — the phone
> reaches the backend on the laptop's LAN IP, and the app's Settings screen
> must be given `http://<LAN-IP>:8000`, never `localhost`.

| Service | URL | Creds |
|---|---|---|
| Dashboard | http://localhost:5173 | — |
| Sim control | (Vite assigns, e.g. :5174) | — |
| API + Swagger | http://localhost:8000/docs | — |
| InfluxDB UI | http://localhost:8086 | admin / twinlab123 |
| MQTT | localhost:1883 | anonymous |
| MongoDB | localhost:27017 | admin / twinlab123 |

> ESP32 note: `MQTT_HOST` in firmware must be the laptop's **LAN IP**, never `localhost`.

### Firmware — build & flash (`firmware/twinlab_node_v1/`, ESP-IDF 6.x)

```powershell
# from an ESP-IDF shell, cwd firmware\twinlab_node_v1\
idf.py set-target esp32
idf.py build
idf.py -p COM<N> flash monitor
```

Replace `COM<N>` with the port Device Manager assigns on connect. No external components or managed dependencies — I2C/GPIO drivers, WiFi, and lwip sockets are all in-tree; MQTT is a small hand-rolled publish-only client in `main.c` (esp-mqtt isn't populated in every IDF 6.0 install).

`main/secrets.h` is gitignored — copy `main/secrets.h.example` and fill in `WIFI_SSID`, `WIFI_PASSWORD`, `MQTT_HOST` (laptop LAN IP), `MQTT_PORT`, `DEVICE_ID`. `build/`, `sdkconfig`, `sdkconfig.old` are gitignored (machine-generated).

### Android app (`android/`, Kotlin + Compose)

Open `D:\TwinLab_v2\android` in Android Studio → let it generate the Gradle
wrapper + sync (it pulls Gradle 8.9 / AGP 8.7.2 / SDK 35). Run on a device on the
**same LAN as the backend**; first launch shows a Settings screen — enter
`http://<laptop-LAN-IP>:8000`. `gradlew :app:testDebugUnitTest` runs the JVM unit
tests (health status, WS parsing, twin mapping). Push needs a Firebase project:
`android/README.md` has the steps; `android/app/google-services.json` is
gitignored. Everything except live push works without Firebase.

---

## 4. MQTT topic contract (DO NOT CHANGE)

```
twinlab/device/{device_id}/sensor/{sensor_name}
```

Payload (JSON): `{ "value": 24.6, "unit": "C", "ts": 1734000000000 }`
`ts` is **milliseconds**. InfluxDB writes **nanoseconds** — `ingestion.py` multiplies by 1,000,000.

This contract is the seam that makes the system **source-agnostic**: simulator and real ESP32 are just two publishers. Ingestion (`twinlab/#`) and the backend WS bridge (`twinlab/#`) already accept any `device_id`. Never special-case a device by source in ingestion or the WS path.

---

## 5. Coding conventions (carry these forward)

- **Flat scripts stay flat.** `ingestion.py`, `simulator.py`, `test_mqtt.py` are intentionally readable, no classes, no DI. Do not "refactor" them into OOP.
- **Firmware stays flat too.** `.ino` + a handful of `.h`/`.cpp` helper pairs (`Sensors`, `Mqtt`). No C++ class hierarchies, no Arduino "sketch frameworks."
- **Backend = package structure, but simple.** No DI containers, no abstract base classes, no premature patterns.
- **Config via `pydantic-settings`** — typed `Settings` singleton in `backend/config.py`, `.env` in `backend/`. Never hard-code credentials. Never commit `.env`.
- **Logging split:** backend uses the `logging` module with timestamps; flat scripts use bracketed prints (`[MQTT]`, `[OK]`, `[ERROR]`, `[SIM]`).
- **Log-and-continue.** Ingestion and backend must never crash on a bad payload.
- **Async Motor, not PyMongo, in the backend.** The MQTT→WebSocket bridge is a daemon thread using `asyncio.run_coroutine_threadsafe()` — no InfluxDB polling for live data. (The **simulator** is the one exception: it reads Mongo via **pymongo** because it's a sync flat script.)
- **Validate IDs** against `^[\w\-]+$` before any Flux string interpolation.

---

## 6. Things Claude Code should NOT do

- Don't refactor flat Phase-1 scripts into classes / add DI.
- Don't swap locked tech (InfluxDB, Mosquitto, MongoDB, FastAPI, Docker Compose).
- Don't reintroduce **Gemini** or wire **Isolation Forest** into the alert path — both are out for the MVP.
- Don't reintroduce **Twilio / WhatsApp sending** — the alert transport is FCM push (`backend/push.py`) as of Phase 14. `whatsapp.py` is deleted.
- Don't add **role-based alert routing** to the app — v1 broadcasts every alert to every registered token; owner/maintenance/vendor routing is an explicit v2 feature.
- Don't pull the Android app into a multi-module build, add Hilt, or add instrumented/Compose-UI tests — single module, manual `AppContainer` DI, JVM unit tests only.
- Don't add MQTT broker auth yet (anonymous is intentional through the MVP).
- Don't add Kubernetes / Helm / Terraform.
- Don't write a test suite yet.
- Don't rename the GitHub repo (`TwinLab-AI`) — it breaks remotes. Fix the **product name in code/UI strings** to "TwinLab" instead.
- Don't commit `backend/.env` or firmware `main/secrets.h` (both gitignored, both hold credentials).
- Don't change the MQTT topic contract.
- **Don't expand the hardware node's sensor set beyond what `phase-13.md` scopes** (temperature, humidity, accel_x/y/z, and `vibration` — all raw passthrough values). Vibration now has a threshold alert rule and drives run/stop detection for the run-hours meter (Phase 16) — **`VIB_RUNNING_G` and the vibration alert threshold are both uncalibrated placeholders** (no hardware node has ever been mounted on a spinning machine) and need recalibration once one is.
- Don't claim fuel-theft or overload detection works on real hardware. No fuel sensor or CT clamp is owned yet — those rules stay simulator-only until the parts exist.

---

## 7. Phase workflow protocol (FOLLOW EVERY PHASE)

The authoritative rebuild spec is **`phase/MVP_v2_PLAN.md`**. Read it before expanding any phase.

For each phase, in order:

1. **Before writing any code**, create `phase/phase-N.md` with these sections, minimal text:
   - **Goal** — 2–3 lines: what this phase makes and why.
   - **Structure & steps** — files touched/created; ordered, concrete steps.
   - **Start commands** — exact PowerShell commands to run the full stack for this phase (copy-paste ready, in order).
   - **Expected outcome** — the demoable/verifiable end state + acceptance checks.
2. Do the work.
3. **Only after the goal is met**, append a final section to the same file:
   - **✅ Actually achieved** — what shipped, what deviated from plan, what was deferred, gotchas hit.

Rules: do **not** start coding before `phase/phase-N.md` exists. Do **not** mark a phase done before "Actually achieved" is written. One file per phase. Keep prose minimal — this doubles as the build log.

Template:

```markdown
# Phase N — <title>

## Goal
<2–3 lines.>

## Structure & steps
<files + ordered steps>

## Start commands
<exact PowerShell commands, in order, to bring the full stack up for this phase>

## Expected outcome
<verifiable end state + acceptance checks>

---
## ✅ Actually achieved   <!-- after the phase is done -->
<what shipped / deviations / deferrals / gotchas>
```

---

## 8. MVP v2 roadmap (detail in `phase/MVP_v2_PLAN.md`)

History: `phase-1..4` = original build (done). v2 rebuild continues as **phase-5 onward**.

| Phase | File | Scope | Status |
|---|---|---|---|
| A | `phase/phase-5.md` | Registry-driven core + device schema (`source`, `thresholds`, `status`); registry-driven simulator | ✅ |
| B | `phase/phase-6.md` | Generator sensors (`fuel_level`, `load_current`) + threshold alert engine + fuel-theft rule | ✅ |
| C | `phase/phase-7.md` | Twilio WhatsApp on the alert path (sandbox), bilingual + rupee-anchored | ✅ |
| D | `phase/phase-8.md` | Simulator control mini-app + `sim_control` collection | ✅ |
| E | `phase/phase-9.md` | Hardware buffer (ESP32 real sensors, raw passthrough only) + brand string fixes | ⏸ **Deferred for ELXR'26**, then revived — see Phase 13 |
| F | `phase/phase-10.md` | NFL/SCAPM reframe: seed 4 NFL devices, brand-string kill, SIMULATED badge | ✅ |
| G | `phase/phase-11.md` | Three CRM/inventory features: asset registry (warranty/vendor), consumable auto-reorder (`run_hours`), role-based WhatsApp routing | ✅ |
| H | `phase/phase-12.md` | Demo choreography: manual injector buttons + `Demo Reset` + screen-recording backup | ✅ |
| 13 | `phase/phase-13.md` | Hardware node: tested ESP-IDF firmware (MPU6050 + DHT22) merged into the pipeline as `TL-01`, WiFi-STA + MQTT on the locked contract. Supersedes Phase E. | ✅ |
| 14 | `phase/phase-14.md` | Twilio/WhatsApp retired → FCM push. Native Kotlin + Compose Android app (`android/`): asset dashboard, per-asset live detail + digital twin, push alerts. Backend engine unchanged, transport swapped. | ✅ |
| 15 | `phase/phase-15.md` | Device onboarding: MAC-derived hardware device IDs (no more per-board `secrets.h` edit), sensor checkbox picker + discover-unregistered-devices + delete UI. First real hardware flash-and-verify (`TL-B49244`, confirmed live via `mosquitto_sub`). | ✅ |
| 16 | `phase/phase-16.md` | Vibration threshold alert rule (critical severity) + vibration-driven run/stop detection, giving hardware nodes a working run-hours meter for the first time (no CT clamp needed). `VIB_RUNNING_G` and the vibration alert threshold are uncalibrated placeholders pending a real spinning-machine mount. | ✅ |
| 17 | `phase/phase-17.md` | Digital twin visual redesign: flat rounded-rect + oversized fan → isometric 3-face shading, neon status rim, properly-inset 5-blade fan. Ported from a Claude Design mockup into `TwinView.kt`, same `TwinState` contract. Dropped a duplicate "SIGNAL LOST" text (`AssetDetailScreen` already shows it). | ✅ |
| 18 | `phase/phase-18.md` | App-wide theme refresh: shared `TwinLabTopBar` (shadow + tinted icons) across all 4 screens, card-based asset list + alert rows, glow-ring status dots, warmer background. Launcher icon artwork scaled to 80% for breathing room. | ✅ |

Update the Status column (⬜ → ✅) as each phase's "Actually achieved" is written. Use ⏸ for phases explicitly deferred (scope moved elsewhere or postponed to a later cycle).

---

## 9. Team (no equity discussion in repo files)

- **Muhammad Arham Rajput** — Founder & CEO (Technical). Architecture, MQTT, InfluxDB/MongoDB, ESP32, Groq, this repo.
- **Wahaj** — Head of Product Engineering. React dashboard, FastAPI, Mongo schema.
- **Kaif Alam** — Co-founder, Growth & BD. Brand, BD, NIC paperwork, customer discovery.
- **Muskan Hanif** — Head of Design. Visual identity, dashboard UI, alert templates.
- **Abaan (Muhammad Abban Khawaja)** — Engineering & Security. Scoped under Arham/Wahaj.

Equity/vesting is deferred until after the NIC pitch — no equity promises in any repo file.

---

## 10. GitHub

Remote: `https://github.com/Arhamurrahemeen/TwinLab-AI.git`

---

*Last updated: Phase 15 (2026-09-13) — MAC-derived device IDs, sensor picker/discover/delete UI, and the firmware's first real flash-and-verify (`TL-B49244`, confirmed live via `mosquitto_sub` — no longer simulator-only). Firebase project is live (`google-services.json` + `backend/fcm-service-account.json` both in place, gitignored) and the Android debug APK is compiled. **End-to-end push re-verified 2026-09-13** — full pipeline (simulator injector → threshold alert → `push.py` → FCM) fired against the one real registered token, backend logged `sent 1/1`, the persisted alert carries `push_sent: true`, and Arham confirmed the notification rendered on the physical phone (see `phase/rd_benchmarks.md`). Fully confirmed, not just API-accepted. Category corrected **SCAPM → Predictive Maintenance (PdM)** and the **generator-first wedge killed** in favor of Textile+FMCG enterprise (2026-09-05/2026-08-10, per vault `TwinLab_Identity.md`) — genset is now a supported machine/feeder offering, not the entry point. Groq-only, Isolation Forest still parked; non-invasive install narrative preserved.*
