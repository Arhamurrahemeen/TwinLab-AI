# TwinLab — MVP v2 Rebuild Plan (authoritative spec)

> Read this before expanding any phase. `CLAUDE.md` holds durable context; **this file holds the "what and why" for the v2 rebuild.** Each phase below maps to a `phase/phase-N.md` working doc that Claude Code creates per the phase protocol in `CLAUDE.md` §7.
>
> **Why v2:** the original MVP (`phase-1..4`) was built on the pre-Shahruk strategy — generic any-asset monitoring, single hardcoded device, no thresholds, in-app alerts only. v2 makes it **generator-first, registry-driven, source-agnostic, threshold-alerted, and WhatsApp-first.**
>
> **Timebox:** 2 days. Bias to demoable over complete.

---

## 0. Resolved decisions (do not relitigate)

- **Alert engine lives in `backend/main.py`'s MQTT message handler** — it already consumes every message and has async Mongo (Motor). `ingestion.py` stays a pure flat MQTT→InfluxDB writer.
- **Isolation Forest parked.** Leave `backend/routers/anomaly.py` in the repo but **unmount its router** from `main.py`, and remove the frontend `/anomalies` poll + `getAnomalies` usage. Do not delete the file.
- **Sim control = separate Vite frontend (`sim-control/`), shared FastAPI backend** (new `/sim` router). The **simulator reads Mongo via pymongo** (sync flat script).
- **Groq only.** No Gemini anywhere in the alert/chat path.
- **Hardware vs simulator sensor split:**
  - **Hardware (ESP32, real):** `temperature`, `humidity`, `accel_x/y/z` (vibration).
  - **Simulator-only:** `fuel_level`, `load_current`, and the **fuel-theft** anomaly (no physical fuel/CT sensor owned yet).

---

## 1. Data model (the spine everything hangs off)

### `devices` collection (extend existing)

```jsonc
{
  "device_id": "shell-mpx-gen-1",        // ^[\w\-]+$
  "name": "Shell Pump Mirpurkhas — Generator 1",
  "location": "Mirpurkhas",
  "sensors": ["fuel_level", "load_current", "temperature", "humidity", "accel_x"],
  "source": "simulator",                  // NEW: "simulator" | "hardware"
  "thresholds": {                          // NEW: per-sensor bounds; null = unbounded
    "fuel_level":   { "min": 10,  "max": null },   // low-fuel warning
    "load_current": { "min": null, "max": 40 },    // overload
    "temperature":  { "min": null, "max": 85 }
  },
  "status": "active",                      // NEW: "active" | "inactive"
  "description": null,
  "created_at": "...",
  "updated_at": "..."
}
```

- `source` defaults to `"simulator"` if omitted (keeps old devices working).
- `thresholds` defaults to `{}` (no thresholds = no threshold alerts).
- `status` defaults to `"active"`.

### `alerts` collection (new)

```jsonc
{
  "device_id": "shell-mpx-gen-1",
  "sensor": "fuel_level",
  "alert_type": "threshold",              // "threshold" | "fuel_theft"
  "severity": "warning",                   // "warning" | "critical"
  "value": 8.4,
  "unit": "L",
  "detail": "below min 10",                // human-readable breach descriptor
  "message_en": "...",                     // rendered alert text (EN)
  "message_ur": "...",                     // rendered alert text (Roman Urdu)
  "ts": 1734000000000,                     // ms
  "whatsapp_sent": true,
  "created_at": "..."
}
```

### `sim_control` collection (new, Phase D)

One doc per `source:simulator` device. The simulator polls it each loop.

```jsonc
{
  "device_id": "shell-mpx-gen-1",
  "generator_on": true,                    // drives load_current + fuel burn
  "base_values": {                          // operator-set baselines (sliders)
    "fuel_level": 70, "temperature": 35, "humidity": 50
  },
  "inject": {                               // active anomaly injectors (expiring)
    "fuel_theft": { "active": false, "until_ts": 0 },
    "overheat":   { "active": false, "until_ts": 0 },
    "overload":   { "active": false, "until_ts": 0 },
    "offline":    { "active": false, "until_ts": 0 }   // stop publishing -> connectivity drop
  },
  "updated_at": "..."
}
```

---

## 2. Phase A → `phase/phase-5.md` — Registry-driven core + schema

**Goal:** A registered device is the single source of truth. Registering a `source:simulator` device makes it start publishing with **zero code change**. This kills the "add device does nothing" bug (simulator was hardcoded to `dev-001`).

**Backend**
- `models/device.py`: add `source` (default `"simulator"`), `thresholds` (default `{}`), `status` (default `"active"`) to `DeviceCreate`, `DeviceUpdate`, `DeviceResponse`.
- `routers/devices.py`: accept/return the new fields. PATCH must allow updating `thresholds`, `source`, `status`. (Keep existing validation + `^[\w\-]+$` discipline.)
- Confirm `ingestion.py` (`twinlab/#`) and `main.py` WS bridge (`twinlab/#`) already accept any `device_id` — **verify only, no change.**

**Simulator (`simulator.py`) — rewrite, stay flat**
- Remove hardcoded `DEVICE_ID = "dev-001"`.
- On startup and every N seconds, read `devices` where `source == "simulator"` and `status == "active"` via **pymongo**.
- For each such device, publish values **only for the sensors listed on that device** to `twinlab/device/{device_id}/sensor/{sensor}`.
- Keep the bracketed-print logging style.
- (Anomaly injection + per-device control comes in Phase D; for now, sensible defaults so charts move.)

**Frontend**
- `RegisterDevice.jsx`: add a **Source** dropdown (`simulator` / `hardware`) and optional **threshold** inputs (min/max per sensor — a simple repeatable row is fine).
- Show `source` and `status` on the device card in `DeviceList.jsx`.

**Acceptance checks**
- Register a new `source:simulator` device with sensors → within one sim refresh cycle, live charts populate for it. No simulator code edit.
- Register a `source:hardware` device → it appears, charts stay empty until a real publisher sends to its topic.
- `GET /devices/{id}` returns `source`, `thresholds`, `status`.

---

## 3. Phase B → `phase/phase-6.md` — Generator sensors + threshold alert engine

**Goal:** Replace "every reading becomes an alert" with **explicit per-sensor thresholds** evaluated at ingestion, plus a deterministic **fuel-theft** rule. Add the two generator-defining sensors.

**Simulator — generator profile**
- Add `fuel_level` (unit `L`) and `load_current` (unit `A`).
- Running model: when generator on → `load_current` ~15–25 A, `fuel_level` slowly decreases, `temperature` runs warmer; when off → `load_current` ~0, fuel flat.
- (Injectors wired in Phase D; Phase B just needs the sensors emitting believable values.)

**Alert engine — in `backend/main.py` `_on_mqtt_message`**
- New module `backend/alerts.py` (simple functions, no OOP): `evaluate(device_id, sensor, value, unit)` → returns an alert dict or `None`.
- **Threshold rule:** breach if `value < thresholds[sensor].min` or `value > thresholds[sensor].max`. Severity: `critical` for hard bounds you designate (e.g. overload, overheat), else `warning`.
- **Fuel-theft rule:** maintain a small rolling buffer of `fuel_level` per device (in-memory). Fire `fuel_theft` (critical) when `fuel_level` drops more than **`THEFT_DROP_L`** (default 5 L) within **`THEFT_WINDOW_S`** (default 300 s) **while `load_current` < `OFF_AMPS`** (default 2 A) — i.e. fuel disappearing while the generator is off. Read latest `load_current` from the existing `_last_known` cache.
- **Cooldown:** in-memory `{(device_id, sensor, alert_type): last_fired_ts}`, default **600 s**. Suppress duplicates within cooldown.
- Threshold config: cache device thresholds in memory; refresh on a short interval and/or on device PATCH. Don't hit Mongo on every message.
- On fire: write to `alerts` collection (async Motor), broadcast to WS so the dashboard updates live, and (Phase C) trigger WhatsApp.

**API + frontend**
- New `routers/alerts.py`: `GET /devices/{id}/alerts?limit=&since=` (most-recent first from Mongo).
- `main.py`: mount alerts router; **unmount `anomaly.py`**.
- `AlertsPanel.jsx`: stop polling `/anomalies`; poll `/alerts` (or consume WS). Render `alert_type`, `severity`, value+unit, time. Remove `getAnomalies` from `api.js`.

**Acceptance checks**
- Set `temperature.max = 40`, push a 45 °C reading → exactly one `threshold` alert; a second 45 °C within 10 min does **not** create a duplicate.
- With generator off, drop `fuel_level` 8 L in 2 min → one `fuel_theft` critical alert.
- With generator on, normal fuel burn does **not** trigger fuel-theft.
- `/anomalies` no longer referenced anywhere in the frontend.

---

## 4. Phase C → `phase/phase-7.md` — WhatsApp (Twilio sandbox)

**Goal:** The alert is the product for the buyer. Every fired alert (respecting cooldown) sends a **bilingual, rupee-anchored WhatsApp** message.

**Backend**
- Config (`config.py` + `.env`): `twilio_account_sid`, `twilio_auth_token`, `twilio_whatsapp_from` (`whatsapp:+14155238886` sandbox), `alert_whatsapp_to` (your demo phone, `whatsapp:+92...`).
- `backend/whatsapp.py` (flat functions): `send_alert(alert: dict)` using the Twilio REST API. Log-and-continue on failure; set `whatsapp_sent` accordingly. Never crash the MQTT handler if Twilio is down.
- Alert engine calls `send_alert` when an alert fires (same cooldown gate — no separate spam).

**Message template (bilingual, rupee-anchored)**
- Fuel theft, e.g.:
  `⚠️ {device_name} — fuel dropped {drop}L in {mins} min while generator OFF. Suspected theft. Est. loss ~PKR {rupees}.`
  `{device_name} — generator BAND honay ke bawajood {mins} min mein {drop}L fuel kam hua. Chori ka shak. Taqreeban PKR {rupees} nuqsan.`
- Threshold breach, e.g.:
  `🔴 {device_name} — {sensor} {value}{unit} (limit {bound}{unit}).`
- Rupee estimate for fuel theft: `rupees = drop_litres * DIESEL_PRICE_PKR` (config default, easy to update).

**Limitations to record in `phase-7.md`**
- **Sandbox only.** Recipient phone must first send the Twilio join code; free-form messages work inside the 24-h window. **Not production** — production needs Meta Business verification + template approval (days–weeks).
- Single recipient (`alert_whatsapp_to`) for the demo; per-device owner routing is post-MVP.

**Acceptance checks**
- Trigger a fuel-theft alert → WhatsApp arrives on the demo phone in Roman Urdu + English with a rupee figure.
- Trigger the same within cooldown → no second WhatsApp.
- Twilio creds blank/invalid → alert still records in Mongo + dashboard; backend logs the failure and stays up.

---

## 5. Phase D → `phase/phase-8.md` — Simulator control mini-app

**Goal:** A separate operator console to drive the demo deterministically — set live values, flip the generator, and inject faults on cue. Controls **only `source:simulator`** devices.

**Backend**
- `routers/sim.py`: `GET /sim/{device_id}` and `PUT /sim/{device_id}` ↔ `sim_control` collection (upsert; reject if device isn't `source:simulator`). `GET /sim` lists controllable devices.
- Mount in `main.py`.

**Simulator**
- Each loop, for each simulator device, read its `sim_control` doc: apply `base_values`, honor `generator_on`, and apply active `inject` modes until their `until_ts`:
  - `fuel_theft` → rapidly decrease `fuel_level` while forcing `load_current` low.
  - `overheat` → push `temperature` above its max.
  - `overload` → push `load_current` above its max.
  - `offline` → **stop publishing** for that device (drives the dashboard's load-shedding banner + reconnect).

**Frontend — new Vite app `sim-control/`**
- Talks to the same backend (`/sim`, `/devices`). Separate dev server.
- Per simulator device: sensor sliders (base values), generator on/off toggle, and injector buttons: **Inject fuel theft**, **Inject overheat**, **Inject overload**, **Drop connectivity** (each sets the injector active for a short, configurable window).
- Minimal styling — this is an internal control surface, not brand-critical, but keep it legible (cream bg ok).

**Acceptance checks**
- Move a slider → main dashboard chart for that sensor tracks the new baseline within a couple seconds.
- Click **Inject fuel theft** with generator off → fuel-theft alert + WhatsApp fire on the main dashboard path.
- Click **Drop connectivity** → main dashboard shows the offline banner; releasing it resumes live data.

---

## 6. Phase E → `phase/phase-9.md` — Hardware buffer + brand

**Goal:** Prove the same pipeline ingests **real ESP32 data**, and clear stale "TwinLab AI" branding. Buffer phase — do not let it block the pitch-critical demo.

**Hardware**
- ESP32 firmware (Arduino) publishes `temperature`, `humidity`, `accel_x/y/z` to `twinlab/device/{its_device_id}/sensor/{sensor}`, payload `{value, unit, ts}` (ms). `MQTT_HOST` = laptop LAN IP.
- Register the ESP32 as a `source:hardware` device with those sensors. Confirm live charts + threshold alerts work identically to simulator devices.
- **Out of scope:** fuel_level / load_current on hardware (no sensor owned). Fuel-theft stays simulator-only.

**Brand**
- Replace "TwinLab AI" → "TwinLab" in code/UI strings (e.g. `chat.py` system prompt, any title strings). Do **not** rename the GitHub repo.
- Confirm no Gemini references remain in active paths.

**Acceptance checks**
- Real ESP32 readings appear on the dashboard and breach a set threshold to raise a real alert.
- `grep -ri "twinlab ai"` returns nothing in active source/UI strings.

---

## 7. Demo narrative this rebuild enables (for the NIC stage)

1. Open dashboard → select **Shell Pump Mirpurkhas — Generator 1** (or **HSK Bone Care — Generator**).
2. Live generator sensors stream: fuel, load current, temperature, vibration.
3. From the **sim control app**, flip generator OFF, then **Inject fuel theft**.
4. Dashboard raises a **critical fuel-theft alert**; a **WhatsApp** lands on the phone in Roman Urdu + English with a rupee loss figure — *the owner never opened the app.*
5. Ask the chat (Groq) **"kya masla hai?"** → plain-language explanation with the sensor context.
6. **Drop connectivity** → load-shedding banner + last-known readings; restore → live resumes.
7. (If hardware ready) point to the **real ESP32** device on the same board — same pipeline, real silicon.

---

## 8. Global limitations (state honestly in the relevant phase docs)

- WhatsApp is **Twilio sandbox** — join-code required, not production-grade.
- Hardware demos **temp/humidity/vibration only**; fuel/load/theft are simulator-only.
- **No auth, single-tenant.** Out of scope.
- **No digital-twin visual layer** (React Flow twin) in this MVP — "twin" is facility-level / Edu framing.
- RUL stays rule-based; ML models untrained; Isolation Forest parked.
- ESP32 bring-up (WiFi, LAN IP, flashing) is the biggest schedule risk — treat as buffer, not critical path.
