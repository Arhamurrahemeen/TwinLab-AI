# Phase C (7) — Twilio WhatsApp alerts

## Goal
Every fired alert (threshold or fuel-theft) sends a bilingual WhatsApp to the owner's phone — English + Roman Urdu, with a rupee-anchored loss figure for fuel-theft events. The alert is always recorded in Mongo first; Twilio failure logs and continues, never crashing the MQTT handler.

## Structure & steps

### Files created

| File | Purpose |
|---|---|
| `backend/whatsapp.py` | Flat module: `send_alert(alert, device_name) -> bool` — formats bilingual body, calls Twilio REST, logs and returns True/False |

### Files modified

| File | Change |
|---|---|
| `backend/config.py` | Add `twilio_account_sid`, `twilio_auth_token`, `twilio_whatsapp_from`, `alert_whatsapp_to`, `diesel_price_pkr` |
| `backend/alerts.py` | Add `drop_litres` field to fuel-theft alert dict; update `message_en`/`message_ur` to include PKR estimate; import settings for `diesel_price_pkr` |
| `backend/main.py` | In `_persist_alert`: look up device name from Mongo, call `whatsapp.send_alert` in a thread executor (non-blocking), update `whatsapp_sent: True` in Mongo on success |

### Files NOT touched

| File | Reason |
|---|---|
| `simulator.py` | No change — already publishes all needed sensors |
| `frontend/` | No frontend change — alert panel already shows `whatsapp_sent` field if we render it (optional Phase D polish) |
| `backend/routers/` | No route changes needed |

### Ordered steps

1. **`backend/config.py`**
   - Add 5 fields with empty-string / float defaults so the app starts fine without `.env` values:
     `twilio_account_sid`, `twilio_auth_token`, `twilio_whatsapp_from`, `alert_whatsapp_to`, `diesel_price_pkr: float = 280.0`

2. **`backend/whatsapp.py`** (new flat module)
   - Guard at top: if `settings.twilio_account_sid` is blank, `send_alert` logs "Twilio not configured" and returns `False` immediately.
   - Fuel-theft body:
     ```
     EN: ⚠️ {device_name} — fuel dropped {drop}L in {mins} min while generator OFF. Suspected theft. Est. loss ~PKR {rupees}.
     UR: {device_name} — generator BAND honay ke bawajood {mins} min mein {drop}L fuel kam hua. Chori ka shak. Taqreeban PKR {rupees} nuqsan.
     ```
   - Threshold body:
     ```
     EN: 🔴 {device_name} — {sensor} {value}{unit} exceeded limit ({bound_label}: {bound}{unit}). Severity: {severity}.
     UR: {device_name} — {sensor} {value}{unit} ({bound_label}: {bound}{unit}). Severity: {severity}.
     ```
   - Send via `twilio.rest.Client` to `settings.alert_whatsapp_to` from `settings.twilio_whatsapp_from`.
   - Wrap entire send in `try/except`; log `[WHATSAPP OK]` or `[WHATSAPP ERROR]`; return bool.
   - `rupees = round(drop_litres * settings.diesel_price_pkr)`; `mins = round(window_s / 60, 1)`.

3. **`backend/alerts.py`**
   - Fuel-theft alert dict: add `drop_litres: float` and `window_s: float` (already computed in the rule) so `whatsapp.py` can use them without re-parsing the `detail` string.
   - Update `message_en` / `message_ur` for fuel-theft to include `~PKR {rupees}` figure using `settings.diesel_price_pkr`.
   - Import `from config import settings` at top.

4. **`backend/main.py`** — update `_persist_alert`
   - Look up device name: `device = await db.devices.find_one({"device_id": alert["device_id"]}, {"name": 1})` — use `device["name"]` or fall back to `device_id`.
   - Insert alert to Mongo (as before, `whatsapp_sent: False`).
   - Broadcast WS (as before).
   - Send WhatsApp in thread executor (non-blocking):
     ```python
     loop = asyncio.get_event_loop()
     sent = await loop.run_in_executor(None, whatsapp.send_alert, alert, device_name)
     ```
   - If `sent`, update the stored doc: `await db.alerts.update_one({"_id": doc_id}, {"$set": {"whatsapp_sent": True}})`.

## Start commands

Same stack — no new services. Run each in a separate terminal from `D:\TwinLab`:

```powershell
# Terminal 1 — Docker (leave running)
docker compose up -d

# Terminal 2 — Ingestion (leave running)
.venv\Scripts\python ingestion.py

# Terminal 3 — Simulator (leave running)
.venv\Scripts\python simulator.py

# Terminal 4 — Backend (--reload picks up all changes)
cd backend
..\.venv\Scripts\uvicorn main:app --reload --port 8000

# Terminal 5 — Dashboard
cd frontend
npm run dev
```

> `pip install twilio` required in the venv before starting. Add to `requirements.txt`.

## Expected outcome

**Acceptance checks (from MVP_v2_PLAN.md §4):**

- [ ] Trigger a fuel-theft alert → WhatsApp lands on demo phone with EN + Roman Urdu body and a PKR loss figure within ~5 s.
- [ ] Trigger the same alert within the 600 s cooldown → no second WhatsApp.
- [ ] Set `TWILIO_ACCOUNT_SID=` blank in `.env` (or remove it) → alert still writes to Mongo + appears in dashboard; backend logs `[WHATSAPP] not configured — skipping` and stays up.
- [ ] `GET /devices/{id}/alerts` → `whatsapp_sent: true` on the sent alert, `false` on any that failed or were suppressed.
- [ ] Trigger a threshold breach → WhatsApp arrives with sensor name, value, unit, and limit.

**Known limitations (per MVP_v2_PLAN.md §4):**
- Sandbox only — recipient must have joined with the Twilio join code; messages only work within the 24-h session window.
- Single recipient (`alert_whatsapp_to`); per-device owner routing is post-MVP.

---

## ✅ Actually achieved

**Shipped:**
- `backend/config.py`: 5 new settings — `twilio_account_sid`, `twilio_auth_token`, `twilio_whatsapp_from`, `alert_whatsapp_to`, `diesel_price_pkr` (default 280.0). App starts cleanly with blank values.
- `backend/whatsapp.py` (new flat module): `send_alert(alert, device_name) -> bool`. Early-return guard if `twilio_account_sid` is blank (logs "not configured — skipping"). Twilio SDK imported lazily inside the try block. Bilingual body for `fuel_theft` (EN + Roman Urdu, `~PKR {rupees:,}` figure, drop litres, minutes). Bilingual body for `threshold` (EN + Roman Urdu, sensor/value/unit/detail). Log `[WHATSAPP OK]` or `[WHATSAPP ERROR]`. Never raises.
- `backend/alerts.py`: `from config import settings` added. `_make_alert` signature extended with `drop_litres: float = 0.0, window_s: float = 0.0`. Fuel-theft `message_en`/`message_ur` now include PKR estimate (`rupees = round(drop_litres * settings.diesel_price_pkr)`). `drop_litres` and `window_s` stored in the alert dict (used by `whatsapp.py`). Fuel-theft `_make_alert` call updated to pass both kwargs.
- `backend/main.py`: `import whatsapp` added. `_persist_alert` extended: looks up device name from Mongo (falls back to `device_id`), inserts alert, broadcasts WS, calls `whatsapp.send_alert` in `loop.run_in_executor` (non-blocking), updates `whatsapp_sent: True` in Mongo if sent.
- `requirements.txt`: `twilio` added; installed in `.venv`.

**Deviations from plan:** none.

**Deferrals:** none — all Phase C scope shipped.

**Gotchas:**
- Windows PowerShell console throws `UnicodeEncodeError` on emoji characters when printing — this is a terminal encoding issue only, not a Python/Twilio issue. Messages send correctly. Set `PYTHONIOENCODING=utf-8` or use `chcp 65001` in the terminal if you need to print them locally.
- Twilio SDK is imported inside the `try` block in `send_alert` (lazy import) so the module loads fine even if `twilio` isn't installed — it just fails at send time with a logged error.
