<div align="center">

![TwinLab](./assets/banner.svg)

**Generator-first IIoT predictive maintenance — built for Pakistan's asset-heavy SMEs**

[![Python](https://img.shields.io/badge/Python-3776AB?logo=python&logoColor=white&style=flat-square)](https://python.org)
[![FastAPI](https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white&style=flat-square)](https://fastapi.tiangolo.com)
[![React](https://img.shields.io/badge/React-61DAFB?logo=react&logoColor=black&style=flat-square)](https://react.dev)
[![InfluxDB](https://img.shields.io/badge/InfluxDB-22ADF6?logo=influxdb&logoColor=white&style=flat-square)](https://influxdata.com)
[![MongoDB](https://img.shields.io/badge/MongoDB-47A248?logo=mongodb&logoColor=white&style=flat-square)](https://mongodb.com)
[![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white&style=flat-square)](https://docker.com)
[![Groq](https://img.shields.io/badge/Groq-F55036?logo=groq&logoColor=white&style=flat-square)](https://groq.com)

[![MVP v2](https://img.shields.io/badge/MVP%20v2-Phase%20B%20Complete-539091?style=flat-square)]()
[![Status](https://img.shields.io/badge/Status-Active%20Build-orange?style=flat-square)]()

</div>

---

## The problem

> A generator goes down at 2 AM. The owner finds out at 9 AM when the shift manager calls.
> By then the fuel has been stolen, the food-storage compressor has been off for seven hours,
> and the repair bill is three times what a sensor would have cost.
>
> Western monitoring platforms (Siemens, GE Predix, AVEVA) cost more per year than most
> Pakistani SMEs earn in a quarter. **The people who need these tools most can't afford them.**
>
> **TwinLab puts a sensor on your highest-cost asset and WhatsApps you before it fails or gets stolen.**

---

## What TwinLab is

<div align="center">

![TwinLab Pro · TwinLab Edu](./assets/TwinLab_Wordmark_Varient.png)

</div>

Two products, one engine.

| | TwinLab Pro | TwinLab Edu |
| :--- | :--- | :--- |
| **Who** | Banks · hospitals · telecom towers · factories | Engineering students |
| **Entry point** | Generator monitoring (fuel, load, temperature, vibration) | Virtual IIoT experiment canvas |
| **Alert channel** | WhatsApp (owner) + live dashboard (ops head) | In-app coaching |
| **AI** | Groq LLaMA — Urdu / Roman Urdu / English | Same |
| **Hardware** | ESP32 + DHT22 + MPU6050 | ESP32-based student kits |
| **Pilot** | HSK Bone Care — generator at orthopedic facility | DUET · NED |

> **Buyer vs user:** the owner is the buyer — he never opens the dashboard.
> He receives a WhatsApp. The dashboard is for his ops head or son.

`📍 Karachi, Pakistan` &nbsp;·&nbsp; `🏢 OmniteX` &nbsp;·&nbsp; `🎯 NIC Hyderabad / NIC Karachi`

---

## Architecture

```
ESP32 (real hardware)          simulator.py (registry-driven)
        │                               │
        └──────────── MQTT ─────────────┘
                          │
                  Mosquitto :1883
                          │
          ┌───────────────┴────────────────┐
          │                                │
    ingestion.py                      main.py
    MQTT → InfluxDB              WS bridge + alert engine
    (time-series)                         │
                                ┌─────────┴──────────┐
                           threshold             fuel-theft
                           rule eval             rule eval
                                │
                         alerts collection
                         (MongoDB) ──► WhatsApp (Twilio, Phase C)
                                │
                         WebSocket push
                                │
                        React Dashboard
                   live charts · alerts panel · Groq chat
```

---

## Stack

![TwinLab stack](./assets/folder-map.svg)

| Layer | Choice |
| :--- | :--- |
| Hardware | ESP32 + DHT22 (temp/humidity) + MPU6050 (accel/vibration) |
| Messaging | MQTT via **Mosquitto** |
| Time-series DB | **InfluxDB 2.7** |
| Document DB | **MongoDB 7.0** (device registry, thresholds, alerts, sim control) |
| Backend | **FastAPI** (Python) |
| AI — chat | **Groq** `llama-3.3-70b-versatile` (Urdu / Roman Urdu / English) |
| Alerts | **Twilio WhatsApp** sandbox — bilingual, rupee-anchored (Phase C) |
| Frontend | **React + Vite** (recharts) |
| Deploy | Docker Compose (dev) |

---

## Repo structure

| Path | |
| :--- | :--- |
| [`backend/`](./backend) | FastAPI app — device registry, readings, alerts, WebSocket, Groq chat |
| [`backend/alerts.py`](./backend/alerts.py) | Alert engine — threshold rules, fuel-theft rule, cooldown |
| [`backend/routers/`](./backend/routers) | `devices` · `readings` · `alerts` · `chat` · `rul` · `ws` |
| [`frontend/`](./frontend) | React dashboard — live charts, alerts panel, Groq chat FAB |
| [`phase/`](./phase) | Phase docs — plan → build log → actually achieved |
| [`phase/MVP_v2_PLAN.md`](./phase/MVP_v2_PLAN.md) | Authoritative v2 spec (read before expanding any phase) |
| [`phase/limitations.md`](./phase/limitations.md) | Known limitations log |
| [`ingestion.py`](./ingestion.py) | MQTT subscriber → InfluxDB writer (flat script, no changes) |
| [`simulator.py`](./simulator.py) | Registry-driven simulator — reads active devices from Mongo |
| [`docker-compose.yml`](./docker-compose.yml) | Mosquitto + InfluxDB + MongoDB |

---

## Get started

**Prerequisites:** Docker Desktop running, `.venv` created with `pip install -r requirements.txt`.

Run each command in a **separate terminal** from the repo root (`D:\TwinLab`):

```powershell
# 1 — Docker services (Mosquitto + InfluxDB + MongoDB)
docker compose up -d

# 2 — MQTT → InfluxDB ingestion
.venv\Scripts\python ingestion.py

# 3 — Simulator (reads device registry — register a device first, see below)
.venv\Scripts\python simulator.py

# 4 — FastAPI backend
cd backend
..\.venv\Scripts\uvicorn main:app --reload --port 8000

# 5 — React dashboard
cd frontend
npm run dev
# → http://localhost:5173
```

| Service | URL | Credentials |
| :--- | :--- | :--- |
| Dashboard | http://localhost:5173 | — |
| API + Swagger | http://localhost:8000/docs | — |
| InfluxDB UI | http://localhost:8086 | admin / twinlab123 |
| MongoDB | localhost:27017 | admin / twinlab123 |
| MQTT broker | localhost:1883 | anonymous |

> **First run:** the simulator publishes nothing until you register a device.
> Open the dashboard → click **+** → set Source = **Simulator**, add sensors
> (e.g. `fuel_level, load_current, temperature`), set thresholds → Register.
> The simulator picks it up within 30 s and starts publishing.

---

## Roadmap

### Original MVP (phases 1–4) — complete

| Phase | Goal | Status |
| :---: | :--- | :---: |
| 1 | MQTT + InfluxDB + MongoDB — sensor data landing | ✅ |
| 2 | FastAPI backend — device CRUD, readings API, WebSocket push | ✅ |
| 3 | React dashboard — live charts, device list, alerts panel | ✅ |
| 4 | Groq Urdu chat, rule-based RUL, load-shedding banner | ✅ |

### MVP v2 rebuild — generator-first, threshold-alerted, WhatsApp-first

| Phase | File | Scope | Status |
| :---: | :--- | :--- | :---: |
| A | [phase-5.md](./phase/phase-5.md) | Registry-driven simulator · device schema (`source`, `thresholds`, `status`) | ✅ |
| B | [phase-6.md](./phase/phase-6.md) | Threshold alert engine · fuel-theft rule · `alerts` collection | ✅ |
| C | [phase-7.md](./phase/phase-7.md) | Twilio WhatsApp — bilingual, rupee-anchored | ⬜ |
| D | [phase-8.md](./phase/phase-8.md) | Simulator control mini-app (`sim-control/`) | ⬜ |
| E | [phase-9.md](./phase/phase-9.md) | Real ESP32 hardware buffer · brand string cleanup | ⬜ |

---

## Team

| | Role |
| :--- | :--- |
| **Muhammad Arham Rajput** | Founder & CEO (Technical) — architecture, MQTT, InfluxDB/MongoDB, ESP32, Groq, this repo |
| **Wahaj** | Head of Product Engineering — React dashboard, FastAPI, Mongo schema |
| **Muskan Hanif** | Head of Design — visual identity, dashboard UI, alert templates |
| **Abaan (Muhammad Abban Khawaja)** | Engineering & Security |
| **Kaif Alam** | Co-founder, Growth & BD — brand, BD, NIC paperwork |

---

<div align="center">

[LinkedIn](https://www.linkedin.com/in/muhammad-arham-rajput) &nbsp;·&nbsp;
[GitHub](https://github.com/Arhamurrahemeen) &nbsp;·&nbsp;
[Email](mailto:arhamurrahemeen@gmail.com)

<br/>

![OmniteX](./assets/OmniteX_Wordmark_black.png)

<sub>Living document — updated at the end of every phase.</sub>

</div>
