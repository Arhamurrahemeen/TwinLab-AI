<div align="center">

![TwinLab AI](./assets/banner.svg)

**Industrial IoT Digital Twin Platform for Pakistan's SMEs and Engineering Institutions**

[![Python](https://img.shields.io/badge/Python-3776AB?logo=python&logoColor=white&style=flat-square)](https://python.org)
[![FastAPI](https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white&style=flat-square)](https://fastapi.tiangolo.com)
[![InfluxDB](https://img.shields.io/badge/InfluxDB-22ADF6?logo=influxdb&logoColor=white&style=flat-square)](https://influxdata.com)
[![MongoDB](https://img.shields.io/badge/MongoDB-47A248?logo=mongodb&logoColor=white&style=flat-square)](https://mongodb.com)
[![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white&style=flat-square)](https://docker.com)
[![React](https://img.shields.io/badge/React-61DAFB?logo=react&logoColor=black&style=flat-square)](https://react.dev)
[![Gemini](https://img.shields.io/badge/Gemini-8E75B2?logo=googlegemini&logoColor=white&style=flat-square)](https://ai.google.dev)

[![Phase](https://img.shields.io/badge/Phase-2%20Complete-539091?style=flat-square)]()
[![Status](https://img.shields.io/badge/Status-MVP%20Build-orange?style=flat-square)]()

</div>

---

## The problem

> Siemens, GE Predix, AVEVA — the platforms that actually solve industrial monitoring
> cost more per year than most Pakistani SME factories earn in a quarter.
>
> The engineers who need these tools the most can't afford them.
> The students who will build Pakistan's next industrial wave have never touched them.
>
> **TwinLab is the alternative.**

---

## What TwinLab is

<div align="center">

![TwinLab Pro · TwinLab Edu](./assets/TwinLab_Wordmark_Varient.png)

</div>

Two products, one platform.

| | TwinLab Pro | TwinLab Edu |
| :--- | :--- | :--- |
| **Who** | Small/mid manufacturers | Engineering students |
| **What** | Real-time sensor monitoring, anomaly detection, RUL prediction | Virtual experiment canvas, IIoT lab kits |
| **AI** | Isolation Forest · LSTM · Gemini Urdu chat | Coaching assistant |
| **Hardware** | ESP32 + DHT22 + MPU6050 | ESP32-based student kits |
| **Pilot** | HSK Bone Care (orthopedic equipment) | DUET · NED |

`📍 Karachi, Pakistan` &nbsp;·&nbsp; `🏢 OmniteX` &nbsp;·&nbsp; `🎯 NIC Karachi Incubation`

---

## Stack

![TwinLab stack](./assets/folder-map.svg)

## Architecture

```
ESP32 Sensors
     │  MQTT
     ▼
Mosquitto Broker (port 1883)
     │
     ▼
ingestion.py ──► InfluxDB 2.7  (time-series readings)
                 MongoDB 7.0   (device registry, users, configs)
                      │
                      ▼
               FastAPI Backend
               ├── REST API  (device registry, readings)
               ├── WebSocket (live push to frontend)
               └── AI layer  (Isolation Forest · LSTM · Gemini)
                      │
                      ▼
               React Dashboard
               └── Live charts · Alerts · Urdu chat
```

---

## Repo structure

| Path | |
| :--- | :--- |
| [`backend/`](./backend) | FastAPI app — device registry, readings API, WebSocket push |
| [`phase/`](./phase) | Phase docs — what was built, steps taken, demo scripts |
| [`ingestion.py`](./ingestion.py) | MQTT subscriber → InfluxDB writer |
| [`simulator.py`](./simulator.py) | Fake ESP32 publisher (no hardware needed) |
| [`test_mqtt.py`](./test_mqtt.py) | Bare MQTT listener for broker sanity check |
| [`docker-compose.yml`](./docker-compose.yml) | Mosquitto + InfluxDB + MongoDB |
| [`mosquitto/`](./mosquitto) | Broker config, data, logs |

---

## Get started

**Pre-requisite:** Docker Desktop running.

```bash
# 1. Start all services
docker compose up -d

# 2. Install dependencies
pip install -r requirements.txt

# 3. Start the ingestion service
python ingestion.py

# 4. Run the simulator (separate terminal)
python simulator.py

# 5. Start the backend
cd backend
uvicorn main:app --reload --port 8000
```

| Service | URL | Credentials |
| :--- | :--- | :--- |
| InfluxDB UI | http://localhost:8086 | admin / twinlab123 |
| API + Swagger | http://localhost:8000/docs | — |
| MQTT broker | localhost:1883 | anonymous |
| MongoDB | localhost:27017 | admin / twinlab123 |

---

## Roadmap

| Phase | Goal | Status |
| :---: | :--- | :---: |
| **1** | MQTT + InfluxDB + MongoDB — sensor data landing in time-series DB | ✅ Done |
| **2** | FastAPI backend — device registry, live readings, WebSocket push | ✅ Done |
| **3** | React dashboard — live charts, alerts, Isolation Forest anomaly detection | 🔄 Next |
| **4** | LSTM RUL model · Gemini Urdu chat · load-shedding mode · NIC demo | ⏳ Planned |

---

## Phase docs

| | |
| :--- | :--- |
| [Phase 1 — Ingestion Pipeline →](./phase/phase-1.md) | What was built, issues hit, verification steps |
| [Phase 2 — FastAPI Backend →](./phase/phase-2.md) | Build steps, endpoints, demo script |

---

## Team

| | Role |
| :--- | :--- |
| **Muhammad Arham Rajput** | CTO · Founder — backend, GenAI, this repo |
| **Wahaj** | Head of Product Engineering — joining Phase 2+ |
| **Kaif** | Co-founder — branding, BD, pitch |

---

<div align="center">

[LinkedIn](https://www.linkedin.com/in/muhammad-arham-rajput) &nbsp;·&nbsp;
[GitHub](https://github.com/Arhamurrahemeen) &nbsp;·&nbsp;
[Email](mailto:arhamurrahemeen@gmail.com)

<br/>

![OmniteX](./assets/OmniteX_Wordmark_black.png)

<sub>Living document — updated at the end of every phase.</sub>

</div>
