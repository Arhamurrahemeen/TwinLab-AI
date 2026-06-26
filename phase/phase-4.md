# Phase 4 — Gemini Chat, RUL, Load-Shedding, Device Registration

## Goal
Complete the MVP for the NIC Karachi demo. Four additions: Gemini-powered Urdu/English chat with device context, rule-based RUL display, load-shedding mode (disconnect banner + last-known cache), and a Register Device form in the dashboard.

---

## What Phase 4 Does NOT Include
- Real LSTM training (rule-based RUL only)
- Authentication
- Production deployment
- Kubernetes / Helm

---

## Project Structure (additions only)

```
backend/
└── routers/
    ├── chat.py          # NEW: POST /chat — Gemini with device context
    └── rul.py           # NEW: GET /devices/{id}/rul — rule-based RUL per sensor

frontend/src/
├── components/
│   ├── ChatPanel.jsx    # NEW: floating chat button → slide-up chat window
│   ├── RulCard.jsx      # NEW: RUL estimate strip below charts
│   └── RegisterDevice.jsx  # NEW: modal form — POST /devices
└── hooks/
    └── useDeviceSocket.js  # UPDATE: expose connection status for load-shedding banner
```

---

## Key Technical Decisions

- **Gemini** — `gemini-1.5-flash` via `google-generativeai` SDK; backend builds context (device name, location, latest readings per sensor, active anomalies) before calling Gemini; response streamed back as plain text
- **RUL** — linear regression over last 50 readings per sensor; extrapolate to a per-sensor threshold; return `{sensor, hours_remaining, trend_direction}`; if trend is flat/improving, return "stable"
- **Load-shedding** — backend stores last known reading per device+sensor in a dict (updated on every MQTT message); `GET /devices/{id}/last-known` serves it when live data is unavailable; frontend WebSocket hook exposes `connected` bool; disconnected → yellow banner + last-known fallback
- **Register Device** — modal in DeviceList panel; fields: device_id, name, location, sensors (comma-separated); POST to existing `/devices`; list auto-refreshes on success

---

## Build Steps

### Backend

#### Step 1 — Add Gemini key to `.env` + `config.py`
```
GEMINI_API_KEY=your-key-here
```

#### Step 2 — Install `google-generativeai`
```
google-generativeai
```

#### Step 3 — `backend/routers/chat.py`
- `POST /chat` body: `{device_id, message}`
- Fetches last reading per sensor + active anomalies from InfluxDB/anomaly logic
- Builds system prompt with device context
- Calls Gemini flash, returns `{reply}`

#### Step 4 — `backend/routers/rul.py`
- `GET /devices/{device_id}/rul`
- Fetches last 50 readings per sensor from InfluxDB
- Linear regression (numpy polyfit) on values over time
- Extrapolates to threshold; returns hours remaining or "stable"

#### Step 5 — Last-known cache in `main.py`
- Dict `_last_known: dict[device_id][sensor] = {value, unit, ts}`
- Updated in `_on_mqtt_message`
- New endpoint `GET /devices/{device_id}/last-known` returns the dict

#### Step 6 — Register all new routers in `main.py`

---

### Frontend

#### Step 7 — Update `useDeviceSocket` to expose `connected` bool
#### Step 8 — Load-shedding banner in `App.jsx` — yellow strip when `connected === false`
#### Step 9 — `RulCard.jsx` — fetches `/rul` on device select, shows per-sensor strip
#### Step 10 — `ChatPanel.jsx` — floating teal button bottom-right; slide-up panel; POST `/chat`; renders Gemini reply; supports Urdu input natively (browser handles RTL)
#### Step 11 — `RegisterDevice.jsx` — modal triggered by `+` button in device panel header

---

## Demo Script (NIC pitch flow)

1. Open dashboard → select Boiler Unit A → 8 live charts
2. Wait for fault spike (every 60 s) → red alert appears
3. Click chat button → type "kya masla hai?" → Gemini explains anomaly in Urdu
4. Kill backend → yellow "Connectivity lost" banner appears, last known readings shown
5. Restart backend → banner clears, live data resumes
6. Click `+` in device panel → register a second device live

---

## Verification Checklist

- [ ] `POST /chat` with `{device_id, message}` returns Gemini reply with device context
- [ ] `GET /devices/{id}/rul` returns per-sensor RUL or "stable"
- [ ] `GET /devices/{id}/last-known` returns cached readings
- [ ] Chat panel opens, sends message, renders reply
- [ ] Urdu text input works in chat panel
- [ ] Disconnecting backend shows yellow banner within 5 s
- [ ] Reconnecting backend clears banner and resumes live data
- [ ] Register Device modal creates device, list refreshes
- [ ] Full NIC demo flow runs without errors

---

## Outcome
Phase 4 built. Full MVP delivered.

**Backend additions:**
- `google-generativeai` installed; `GEMINI_API_KEY` wired into `config.py`
- `routers/chat.py` — `POST /chat` fetches latest readings from InfluxDB, builds device context, calls `gemini-1.5-flash`; replies in same language as input (Urdu / English)
- `routers/rul.py` — `GET /devices/{id}/rul` runs linear regression (numpy polyfit) per sensor over last 2h; extrapolates to per-sensor threshold; returns `{sensor, status, hours_remaining, trend}`
- `main.py` — in-memory `_last_known` dict updated on every MQTT message; `GET /devices/{id}/last-known` serves cached readings for load-shedding fallback

**Frontend additions:**
- `useDeviceSocket` — now returns `{ messages, connected }`; auto-reconnects every 3 s on drop
- `App.jsx` — yellow load-shedding banner when `connected === false`; navbar dot goes red
- `RulCard.jsx` — horizontal strip below charts header; shows per-sensor hours-remaining; warning sensors highlighted amber; hidden when all stable
- `ChatPanel.jsx` — floating teal FAB bottom-right; slide-up 340px panel; user/AI bubbles; loading dots animation; Urdu input works natively
- `RegisterDevice.jsx` — modal triggered by `+` in device panel header; posts to `/devices`; list auto-refreshes on success
- `DeviceList.jsx` — `+` button added to panel title row
