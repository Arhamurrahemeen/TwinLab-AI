# Phase 1 — Ingestion Pipeline

## Goal
Stand up the core data ingestion stack: sensor data flows from a publisher (real or simulated) through an MQTT broker into InfluxDB, with MongoDB also running for later use. Verify the pipeline end-to-end before writing any application code.

---

## What Phase 1 was NOT
- No FastAPI, no REST endpoints
- No frontend
- No authentication
- No AI/ML
- No real ESP32 hardware required (simulator covers it)

---

## Project Structure

```
TwinLab/
├── docker-compose.yml              # Spins up Mosquitto, InfluxDB, MongoDB
├── mosquitto/
│   ├── config/
│   │   └── mosquitto.conf          # Broker config: port 1883, anonymous, persistence on
│   ├── data/                       # Mosquitto persistence files (runtime)
│   └── log/                        # Mosquitto logs (runtime)
├── ingestion.py                    # MQTT subscriber → writes to InfluxDB
├── simulator.py                    # Fake DHT22 + MPU6050 publisher (no hardware needed)
├── test_mqtt.py                    # Bare MQTT listener for broker sanity check
├── requirements.txt                # paho-mqtt, influxdb-client
└── README.md                       # Step-by-step run instructions
```

---

## MQTT Topic Contract

```
twinlab/device/{device_id}/sensor/{sensor_name}
```

Payload (JSON):
```json
{ "value": 24.6, "unit": "C", "ts": 1734000000000 }
```

- `ts` is milliseconds since epoch
- `ingestion.py` converts to nanoseconds (× 1,000,000) before writing to InfluxDB

---

## Services & Ports

| Service    | Container        | Port  |
|------------|------------------|-------|
| MQTT       | twinlab-mqtt     | 1883  |
| InfluxDB   | twinlab-influx   | 8086  |
| MongoDB    | twinlab-mongo    | 27017 |

---

## Verification Steps (the 5-step sequence)

1. `docker compose up -d` — all three containers healthy
2. `pip install -r requirements.txt`
3. Two terminals: `python test_mqtt.py` (listener) + `python simulator.py` (publisher) — confirm messages print in the listener
4. Stop the test listener. Run `python ingestion.py`. Keep simulator running.
5. Open `http://localhost:8086` (admin / twinlab123) → Data Explorer → bucket `twinlab` → measurement `sensor_reading` — confirm rows landing with `device_id`, `sensor`, `unit` tags

---

## Issues Encountered & Fixes

**Issue 1: Docker daemon not running**
- Error: `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`
- Fix: Open Docker Desktop and wait for the engine to start before running `docker compose up -d`

**Issue 2: mosquitto.conf was a directory, not a file**
- Error: `mount src=.../mosquitto.conf ... not a directory: Are you trying to mount a directory onto a file (or vice-versa)?`
- Root cause: Docker had auto-created `mosquitto/config/mosquitto.conf` as a directory (empty bind mount target) before the file existed
- Fix: Delete the directory, create the actual `mosquitto.conf` file with correct broker config, re-run `docker compose up -d`

**Note on InfluxDB Data Explorer**
- The Data Explorer is a query tool, not a live dashboard — it does not auto-push updates
- For verification, use the Auto Refresh dropdown (top-right, set to 5s) or check manually
- Real live graphs come in Phase 3 with the React frontend + WebSockets

---

## Outcome
Phase 1 verified. Simulated sensor data flows from `simulator.py` → Mosquitto (port 1883) → `ingestion.py` → InfluxDB bucket `twinlab`, measurement `sensor_reading`. MongoDB is running and ready for Phase 2 device registry.
