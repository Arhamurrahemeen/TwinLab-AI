# CLAUDE.md — TwinLab

> Read automatically by Claude Code at the start of every session in this directory.
> This file holds **durable, always-true** context only. Phase detail lives in `phase/`.
> Keep this file lean. When something here goes stale, fix it — don't append.

---

## 1. What TwinLab is

**TwinLab** (one word, capital T and L — *not* "TwinLab AI") is an **IIoT digital twin + predictive maintenance platform** by **OmniteX** (Pakistan; founder Muhammad Arham Rajput). Currently in **MVP rebuild (v2)** ahead of NIC Hyderabad / NIC Karachi.

**First vertical wedge: generator monitoring** (banks, hospitals, telecom towers, factories, commercial buildings). The platform identity stays broad — generators are the entry point, not the whole product.

- **TwinLab Pro** — asset monitoring for SME / asset-heavy operations. Generator-first.
- **TwinLab Edu** — same engine, university lab layer. Parallel track, not a second vertical.

**One-line buyer pitch:** *"We put a sensor on your highest-cost asset and WhatsApp you before it fails or gets stolen — starting with generators."*

**Who buys vs who uses:** the owner is the **buyer, not the user** — he receives **WhatsApp alerts only**. The dashboard is for the maintenance/ops head or owner's son. Design for both separately.

**The real competitor is the spreadsheet and the ledger** — not Siemens / GE Predix / AVEVA. Never frame TwinLab as a cheap Western-platform clone. We **complement** existing workflows; we never ask the owner to change how he works.

---

## 2. Tech stack (locked — change requires a conversation, not a commit)

| Layer | Choice |
|---|---|
| Hardware (real) | ESP32 + DHT22 (temp/humidity) + MPU6050 (accel/vibration) |
| Messaging | MQTT via **Mosquitto** |
| Time-series DB | **InfluxDB 2.7** (sensor readings) |
| Document DB | **MongoDB 7.0** (device registry, thresholds, alerts, sim control) |
| Backend | **FastAPI** (Python) |
| AI — chat | **Groq** `llama-3.3-70b-versatile` (Urdu / Roman Urdu / English) |
| Alerts | **Twilio WhatsApp** (sandbox for MVP), bilingual, rupee-anchored |
| Frontend | **React + Vite** (recharts) |
| Sim control | Separate **Vite** mini-app, same FastAPI backend |
| Deploy | Docker Compose (dev) |

**AI policy:** Groq is the only LLM. **Gemini is not used** (rate limits). **Isolation Forest is parked** for the MVP — alerting is threshold + fuel-theft rule. RUL stays **rule-based** (no trained LSTM).

---

## 3. Run everything (Windows, from repo root `D:\TwinLab`)

```powershell
docker compose up -d                                   # Mosquitto + InfluxDB + MongoDB
.venv\Scripts\python ingestion.py                      # MQTT -> InfluxDB
.venv\Scripts\python simulator.py                      # registry-driven sim publisher
cd backend && ..\.venv\Scripts\uvicorn main:app --reload --port 8000
cd frontend && npm run dev                             # dashboard  http://localhost:5173
cd sim-control && npm run dev                          # sim control mini-app (Phase D+)
```

| Service | URL | Creds |
|---|---|---|
| Dashboard | http://localhost:5173 | — |
| Sim control | (Vite assigns, e.g. :5174) | — |
| API + Swagger | http://localhost:8000/docs | — |
| InfluxDB UI | http://localhost:8086 | admin / twinlab123 |
| MQTT | localhost:1883 | anonymous |
| MongoDB | localhost:27017 | admin / twinlab123 |

> ESP32 note: `MQTT_HOST` in firmware must be the laptop's **LAN IP**, never `localhost`.

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
- Don't add MQTT broker auth yet (anonymous is intentional through the MVP).
- Don't add Kubernetes / Helm / Terraform.
- Don't write a test suite yet.
- Don't rename the GitHub repo (`TwinLab-AI`) — it breaks remotes. Fix the **product name in code/UI strings** to "TwinLab" instead.
- Don't commit `backend/.env` (dev credentials).
- Don't change the MQTT topic contract.

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
| E | `phase/phase-9.md` | Hardware buffer (ESP32 real sensors) + brand string fixes | ⬜ |

Update the Status column (⬜ → ✅) as each phase's "Actually achieved" is written.

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

*Last updated: start of MVP v2 rebuild (post-Shahruk strategy). Generator-first wedge, threshold + fuel-theft alerting, WhatsApp-first, Groq-only, Isolation Forest parked.*