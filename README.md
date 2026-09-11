<div align="center">

![TwinLab](./assets/banner.svg)

**Pakistan's predictive-maintenance platform — digital-twin asset health, non-invasive sensors, push alerts on TwinLab's own app, priced in PKR**

<sub>Predictive Maintenance (PdM) · digital-twin per-asset health model · non-invasive install · SME to enterprise (Textile · FMCG · NFL · HSK)</sub>

[![Python](https://img.shields.io/badge/Python-3776AB?logo=python&logoColor=white&style=flat-square)](https://python.org)
[![FastAPI](https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white&style=flat-square)](https://fastapi.tiangolo.com)
[![React](https://img.shields.io/badge/React-61DAFB?logo=react&logoColor=black&style=flat-square)](https://react.dev)
[![InfluxDB](https://img.shields.io/badge/InfluxDB-22ADF6?logo=influxdb&logoColor=white&style=flat-square)](https://influxdata.com)
[![MongoDB](https://img.shields.io/badge/MongoDB-47A248?logo=mongodb&logoColor=white&style=flat-square)](https://mongodb.com)
[![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white&style=flat-square)](https://docker.com)
[![Groq](https://img.shields.io/badge/Groq-F55036?logo=groq&logoColor=white&style=flat-square)](https://groq.com)

[![MVP v2](https://img.shields.io/badge/MVP%20v2-Phase%2014%20%C2%B7%20Android%20app-539091?style=flat-square)]()
[![Status](https://img.shields.io/badge/Status-Active%20Build-orange?style=flat-square)]()

</div>

---

## The problem

> A motor bearing starts failing at 2 AM. The plant finds out at 9 AM when the line stops.
> By then the asset is damaged, a shift of output is lost, and the repair bill is three times
> what a sensor would have cost. SCADA controls that line — it doesn't predict failure on it,
> and it never reached the standby genset, the compressor, or the chiller at all.
>
> Foreign predictive-maintenance and asset-management platforms (Augury, Petasense; Siemens, GE Predix, IBM Maximo)
> are USD-billed and cost more per year than most Pakistani SMEs earn in a quarter.
> **The people who need these tools most can't afford them — and no productized Pakistani PdM alternative exists.**
>
> **TwinLab straps a non-invasive sensor onto your critical machines and pushes you an alert — in Roman Urdu — before an asset fails.**

---

## What TwinLab is

<div align="center">

![TwinLab Pro · TwinLab Edu](./assets/TwinLab_Wordmark_Varient.png)

</div>

**Category:** Predictive Maintenance (PdM) — the term Augury and Petasense use for themselves, and a budget line plants that run reliability programs already have. **Digital twin** is the feature: a live per-asset health model. TwinLab is a PdM layer that sits *ahead of* an EAM/APM system (Siemens MindSphere, IBM Maximo, GE Predix) — it does not compete with EAM, it competes with *not having predictive maintenance at all*. Architecturally it's the **APM 4.0** generation — wireless condition monitoring on a standalone Pakistani cloud, non-invasive install — vs the deep-OT-integrated APM 3.0 incumbents. Same product reaches an SME workshop *and* an NFL Faisalabad plant.

Two products, one engine.

| | TwinLab Pro | TwinLab Edu |
| :--- | :--- | :--- |
| **Who** | Textile + FMCG plants (already run SCADA, have non-invasive coverage gaps) → SME feeder (hospitals, hospitality, anyone with a motor or generator) | Engineering students |
| **Assets** | Motors, compressors, chillers/AC, boilers, gensets, conveyors, tanks — vibration, temperature, current (genset is a supported machine + feeder offering, **not** the wedge) | Virtual IIoT experiment canvas |
| **Alert channel** | Push notification on the TwinLab Android app (owner) + live web dashboard (ops head) | In-app coaching |
| **AI** | Groq LLaMA — Urdu / Roman Urdu / English | Same |
| **Hardware** | ESP32 + DHT22 + MPU6050 (non-invasive strap-on) | ESP32-based student kits |
| **Pilots** | HSK Bone Care (hospital, proven) · hospitality channel (Sutoon) · **NFL POC ask: PKR 25 lac / 10 assets / 3 months** | DUET · NED |

> **Buyer vs user:** the owner is the buyer — he never opens the web dashboard.
> He gets a push notification on the TwinLab app (where he can also glance at the
> asset list and its digital twin). The dashboard is for his ops head or son.

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
                         (MongoDB) ──► FCM push ──► TwinLab Android app
                                │                   (dashboard · detail · 3D twin)
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
| Alerts | **Firebase Cloud Messaging** push to the TwinLab Android app — bilingual (EN + Roman Urdu), rupee-anchored |
| Frontend | **React + Vite** (recharts) — ops-head web dashboard |
| Buyer app | Native **Kotlin + Jetpack Compose** (`android/`) — Ktor, DataStore, Compose-Canvas digital twin, FCM |
| Deploy | Docker Compose (dev) |

---

## Repo structure

| Path | |
| :--- | :--- |
| [`backend/`](./backend) | FastAPI app — device registry, readings, alerts, WebSocket, Groq chat |
| [`backend/alerts.py`](./backend/alerts.py) | Alert engine — threshold rules, fuel-theft rule, cooldown |
| [`backend/routers/`](./backend/routers) | `devices` · `readings` · `alerts` · `chat` · `rul` · `ws` · `push` |
| [`backend/push.py`](./backend/push.py) | FCM push sender — broadcast alerts to registered device tokens |
| [`frontend/`](./frontend) | React dashboard — live charts, alerts panel, Groq chat FAB |
| [`android/`](./android) | Native Kotlin + Compose app — asset dashboard, live detail, digital twin, FCM push |
| [`sim-control/`](./sim-control) | Simulator control mini-app — generator toggle, base-value sliders, fault injectors |
| [`phase/`](./phase) | Phase docs — plan → build log → actually achieved |
| [`phase/MVP_v2_PLAN.md`](./phase/MVP_v2_PLAN.md) | Authoritative v2 spec (read before expanding any phase) |
| [`phase/limitations.md`](./phase/limitations.md) | Known limitations log |
| [`ingestion.py`](./ingestion.py) | MQTT subscriber → InfluxDB writer (flat script, no changes) |
| [`simulator.py`](./simulator.py) | Registry-driven simulator — reads active devices from Mongo |
| [`docker-compose.yml`](./docker-compose.yml) | Mosquitto + InfluxDB + MongoDB |

---

## Get started

**Prerequisites:** Docker Desktop running, `.venv` created with `pip install -r requirements.txt`.

Run each command in a **separate terminal** from the repo root (`D:\TwinLab_v2`):

```powershell
# 1 — Docker services (Mosquitto + InfluxDB + MongoDB)
docker compose up -d

# 2 — MQTT → InfluxDB ingestion
.venv\Scripts\python ingestion.py

# 3 — Simulator (reads device registry — 5 NFL devices are seeded by default)
.venv\Scripts\python simulator.py

# 4 — FastAPI backend  (--host 0.0.0.0 so the Android app on the LAN can reach it)
cd backend
..\.venv\Scripts\uvicorn main:app --reload --host 0.0.0.0 --port 8000

# 5 — React dashboard
cd frontend
npm run dev
# → http://localhost:5173

# 6 — Simulator control mini-app (optional — toggle generator, sliders, fault
# injectors, plus a Demo Control Panel with 3 one-click demo buttons + Demo Reset)
cd sim-control
npm run dev
# → http://localhost:5174
```

> **Demo run-through:** open sim-control's **Demo Controls** panel at the top of the
> page → click **1. Inject Overheat**, **2. Inject Consumable**, **3. Inject Theft**
> (each targets a fixed NFL demo device from `seed_nfl.py`) and watch alerts land on
> the dashboard + as a push notification on the app. Click **Demo Reset** between runs to clear cooldowns,
> injectors, and run-hours so the next run starts clean.

| Service | URL | Credentials |
| :--- | :--- | :--- |
| Dashboard | http://localhost:5173 | — |
| Sim control | http://localhost:5174 | — |
| API + Swagger | http://localhost:8000/docs | — |
| InfluxDB UI | http://localhost:8086 | admin / twinlab123 |
| MongoDB | localhost:27017 | admin / twinlab123 |
| MQTT broker | localhost:1883 | anonymous |

> **First run:** the simulator publishes nothing until you register a device.
> Open the dashboard → click **+** → set Source = **Simulator**, add sensors
> (e.g. `fuel_level, load_current, temperature`), set thresholds → Register.
> The simulator picks it up within 30 s and starts publishing.

---

## Run the Android app (`android/`)

The buyer surface. Needs the backend from step 4 running with `--host 0.0.0.0`,
and the phone on the **same Wi-Fi** as the laptop.

```powershell
# 1 — find the laptop's LAN IP (the Wi-Fi adapter's IPv4, e.g. 192.168.1.7)
ipconfig | Select-String IPv4

# 2 — one-time: let the phone reach port 8000 through Windows Firewall
#     (run in an ADMIN PowerShell — approve the UAC prompt)
New-NetFirewallRule -DisplayName "TwinLab 8000" -Direction Inbound `
  -LocalPort 8000 -Protocol TCP -Action Allow -Profile Private
```

3. Open `D:\TwinLab_v2\android` in **Android Studio** → let it generate the Gradle
   wrapper and sync (pulls Gradle 8.9 / AGP 8.7.2 / SDK 35).
4. Plug in the phone (USB debugging on) or start an emulator → **Run** `app`.
5. First launch shows a **Settings** screen — enter `http://<laptop-LAN-IP>:8000`
   (the IP from step 1, **not** `localhost`). Save.
6. The asset dashboard loads the 5 seeded NFL devices with live values. Injecting
   a fault from sim-control pushes an alert to the app **and** a live FCM push
   notification (Firebase is wired up — see [`android/README.md`](./android/README.md)
   if setting up a new environment).

```powershell
# JVM unit tests (health status, WS parsing, twin mapping) — no device needed
cd android
.\gradlew :app:testDebugUnitTest
```

**App can't reach the backend?** → phone and laptop on the same Wi-Fi · backend
started with `--host 0.0.0.0` · firewall rule added (step 2) · Settings URL uses
the LAN IP with `http://` and `:8000`. Test from the phone's browser:
`http://<laptop-LAN-IP>:8000/health` should return `{"status":"ok"}`.

---

## Roadmap

### Original MVP (phases 1–4) — complete

| Phase | Goal | Status |
| :---: | :--- | :---: |
| 1 | MQTT + InfluxDB + MongoDB — sensor data landing | ✅ |
| 2 | FastAPI backend — device CRUD, readings API, WebSocket push | ✅ |
| 3 | React dashboard — live charts, device list, alerts panel | ✅ |
| 4 | Groq Urdu chat, rule-based RUL, load-shedding banner | ✅ |

### MVP v2 rebuild — PdM engine, threshold-alerted, app-first

| Phase | File | Scope | Status |
| :---: | :--- | :--- | :---: |
| A | [phase-5.md](./phase/phase-5.md) | Registry-driven simulator · device schema (`source`, `thresholds`, `status`) | ✅ |
| B | [phase-6.md](./phase/phase-6.md) | Threshold alert engine · fuel-theft rule · `alerts` collection | ✅ |
| C | [phase-7.md](./phase/phase-7.md) | Twilio WhatsApp — bilingual, rupee-anchored | ✅ |
| D | [phase-8.md](./phase/phase-8.md) | Simulator control mini-app (`sim-control/`) | ✅ |
| E | [phase-9.md](./phase/phase-9.md) | Real ESP32 hardware buffer · brand string cleanup | ⏸ Superseded by Phase 13 |
| F | [phase-10.md](./phase/phase-10.md) | NFL reframe — seed 5 NFL devices · brand kill · SIMULATED badge | ✅ |
| G | [phase-11.md](./phase/phase-11.md) | CRM/inventory features — asset registry · consumable auto-reorder · role-based routing | ✅ |
| H | [phase-12.md](./phase/phase-12.md) | Demo choreography — manual injector buttons + Demo Reset + screen-recording backup | ✅ |
| 13 | [phase-13.md](./phase/phase-13.md) | Hardware node — bench-tested ESP-IDF firmware (MPU6050 + DHT22) merged as `TL-01` | ✅ |
| 14 | [phase-14.md](./phase/phase-14.md) | Twilio → FCM push · native Kotlin + Compose Android app (dashboard · digital twin · alerts) | ✅ |

---

## Team

| | Role |
| :--- | :--- |
| **Muhammad Arham Rajput** | Founder & CEO — product end to end (hardware, firmware, backend, AI alerting), architecture, delivery |
| **Malaika** | Co-founder & Head of Finance, Compliance and Sales — financial model, unit economics, compliance, customer pipeline and conversion |

---

<div align="center">

[LinkedIn](https://www.linkedin.com/in/muhammad-arham-rajput) &nbsp;·&nbsp;
[GitHub](https://github.com/Arhamurrahemeen) &nbsp;·&nbsp;
[Email](mailto:arhamurrahemeen@gmail.com)

<br/>

![OmniteX](./assets/OmniteX_Wordmark_black.png)

<sub>Living document — updated at the end of every phase.</sub>

</div>
