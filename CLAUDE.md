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

## 2. Where we are right now — Phase 1

The MVP is being built in **4 phases**. Phase 1 is **complete on paper** and the files are downloaded locally. The immediate next action is to **verify the Phase 1 stack runs end-to-end on the developer machine** before moving to Phase 2.

### Phase 1 scope (this directory)

The ingestion pipeline: sensor → MQTT broker → ingestion service → InfluxDB.

```
twinlab/
├── docker-compose.yml        # Mosquitto + InfluxDB + MongoDB
├── mosquitto/config/mosquitto.conf
├── ingestion.py              # MQTT subscriber → InfluxDB writer
├── simulator.py              # Fake DHT22 + MPU6050 publisher (no hardware needed)
├── test_mqtt.py              # Bare MQTT listener for broker sanity check
├── requirements.txt
└── README.md                 # Step-by-step run instructions
```

### Verification sequence (do not skip — this is what we're doing now)

1. `docker compose up -d` → Mosquitto, InfluxDB, MongoDB all healthy
2. `pip install -r requirements.txt`
3. Two terminals: `python test_mqtt.py` (listener) + `python simulator.py` (publisher). Confirm messages flow.
4. Stop the test listener. Run `python ingestion.py`. Keep simulator running.
5. Open `http://localhost:8086` (admin / twinlab123) → Data Explorer → bucket `twinlab` → measurement `sensor_reading`. Confirm rows are landing with `device_id`, `sensor`, `unit` tags.

If all five steps pass, Phase 1 is verified and we move to Phase 2.

### MQTT topic contract (do not change without updating ingestion.py)

```
twinlab/device/{device_id}/sensor/{sensor_name}
```

Payload (JSON):
```json
{ "value": 24.6, "unit": "C", "ts": 1734000000000 }
```

`ts` is **milliseconds since epoch**. InfluxDB writes use nanoseconds — `ingestion.py` multiplies by 1,000,000 on the way in.

### Service ports

- MQTT broker → `localhost:1883`
- InfluxDB UI → `localhost:8086`
- MongoDB → `localhost:27017`

### Default credentials (dev only — rotate before any deployment)

- InfluxDB: `admin / twinlab123`, token `twinlab-super-secret-token`, org `twinlab`, bucket `twinlab`
- MongoDB: `admin / twinlab123`

---

## 3. The four-phase roadmap

| Phase | Goal                                                                                    | Status          |
| ----- | --------------------------------------------------------------------------------------- | --------------- |
| **1** | MQTT + InfluxDB + MongoDB up, sensor data landing in time-series DB                     | **Verifying**   |
| **2** | FastAPI backend: device registry (Mongo), live readings endpoint (Influx), WebSocket push to frontend | Not started     |
| **3** | React dashboard: live charts, device list, alerts panel; Isolation Forest anomaly detection wired in  | Not started     |
| **4** | LSTM RUL model, Gemini Urdu chat interface, load-shedding mode, polished demo for NIC pitch           | Not started     |

The MVP target is a **demoable end-to-end loop**: a simulated ESP32 publishes readings, the dashboard shows them live, an anomaly is flagged, the user asks "kya masla hai?" in Urdu, Gemini explains it. That's the NIC pitch.

---

## 4. Constraints and conventions

- **Python style**: standard library + the two deps in `requirements.txt` for Phase 1. No premature dependencies.
- **Config**: hard-coded constants at the top of each script for Phase 1. Move to `.env` in Phase 2 when FastAPI lands.
- **Logging**: bracketed-tag print statements (`[MQTT]`, `[OK]`, `[ERROR]`) — keep this style so logs are scannable in the demo terminal. Switch to proper logging in Phase 2.
- **Error handling**: log and continue. The ingestion service must never crash on a single bad payload — sensors in the field will send garbage occasionally.
- **No silent changes to the MQTT topic format** — the ESP32 firmware (Phase 3+) and the FastAPI backend (Phase 2) both depend on it.

---

## 5. Things Claude Code should NOT do

- Do not refactor Phase 1 files into a "production" structure (classes, abstract base loggers, dependency injection). Phase 1 is intentionally flat and readable for the pitch demo.
- Do not swap InfluxDB for TimescaleDB, Mosquitto for EMQX, or MongoDB for Postgres. These choices are locked for MVP — change requires a conversation, not a commit.
- Do not add authentication to the MQTT broker yet. Anonymous is intentional for Phase 1 dev. Auth lands in Phase 4 hardening.
- Do not introduce Kubernetes, Helm, Terraform, or any "real" orchestration. Docker Compose is the deployment target for the entire MVP.
- Do not write tests yet. Phase 1 is verified by the README's 5-step sequence. Test suite lands in Phase 2 with FastAPI.

---

## 6. Who's working on this

- **Arham** (CTO / Founder) — backend, GenAI, MERN, this repo
- **Wahaj** (Head of Product Engineering) — DUET batch topper, joining for Phase 2+
- **Kaif** (Co-founder, non-technical) — branding, BD, pitch

---

## 7. Useful background docs (outside this repo)

- OmniteX brand guidelines (cream/ink/teal palette, Calibri)
- NIC Karachi pitch deck (TwinLab as anchor product)
- Peer-reviewed validation paper: ESP8266/MPU6050/MQTT/Python study, 96.2% prediction accuracy, <$200 hardware cost — used as market thesis support
- Pitch Your Vision 2026 feedback from Syed Hazkeel (Head of NIC Karachi)

---

*Last updated: Phase 1 verification stage. Update this file at the end of every phase.*
