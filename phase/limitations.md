# TwinLab — Known Limitations

> Running log of known issues, gaps, and deferred fixes across all phases. Add an entry whenever a limitation is discovered; mark resolved when the fix ships.

---

## Phase A — Registry-driven core

### Alerts panel shows Isolation Forest anomalies, not threshold alerts

**Status:** ✅ Resolved in Phase B

**Symptom:** After setting thresholds on a device (e.g. temperature min: 5, max: 40), readings well within range (e.g. 27°C) are still flagged as alerts in the Alerts panel.

**Root cause:** The Alerts panel (`AlertsPanel.jsx`) is still wired to the old Phase 3 Isolation Forest endpoint (`GET /devices/{id}/anomalies`). Isolation Forest trains a statistical model on the last 50 readings and flags outliers — it has no knowledge of the threshold fields stored on the device document. The threshold alert engine that evaluates `value < min` / `value > max` is Phase B scope and has not been built yet.

**Affected components:**
- `frontend/src/components/AlertsPanel.jsx` — polls `/anomalies`
- `backend/routers/anomaly.py` — still mounted in `main.py`
- `frontend/src/api.js` — `getAnomalies()` still present

**Fix (Phase B):**
- Build `backend/alerts.py` — threshold + fuel-theft evaluation
- Build `backend/routers/alerts.py` — `GET /devices/{id}/alerts`
- Unmount `anomaly.py` router from `main.py`
- Rewrite `AlertsPanel.jsx` to consume `/alerts`
- Remove `getAnomalies` from `api.js`

---

## Phase C — Twilio WhatsApp

### Error 63007 — "Could not find a Channel with the specified From address"

**Status:** ⬜ Open — investigating tomorrow (daily message limit reached 2026-06-27)

**Symptom:** `send_alert` logs `[WHATSAPP ERROR]` with Twilio error 63007. Alert records in Mongo correctly; `whatsapp_sent` stays `False`.

**Root cause (suspected):** `TWILIO_WHATSAPP_FROM` in `backend/.env` may be missing the `whatsapp:` prefix, or the sandbox number is wrong. Correct value: `whatsapp:+14155238886`.

**Fix:** Verify `backend/.env` has:
```
TWILIO_WHATSAPP_FROM=whatsapp:+14155238886
ALERT_WHATSAPP_TO=whatsapp:+92xxxxxxxxx
```
Restart uvicorn after any `.env` change. Re-test once the daily 5-message limit resets.

---
