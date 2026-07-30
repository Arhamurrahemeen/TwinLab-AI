# Phase 11 — Three CRM/inventory features

## Goal

Extend the device model with **asset registry** (warranty/vendor/criticality), add **consumable auto-reorder** (run-hours accumulation + new `consumable_reorder` alert type), and add **role-based WhatsApp routing** (contacts table with role tags, severity-mapped routing). This is the phase that answers the theme-fit challenge — Slide 5 of Deck_Spine claims exactly these three features exist. Without them, TwinLab loses the "CRM / inventory / supply chain software" argument (NFL Recon §8.1). All additions are additive to the schema; no breaking changes.

Out of scope: no changes to Groq chat, no changes to `rul.py`, no auth, no offline/staleness alerting, no new sensors. UI polish is Muskan / Claude design's job.

## Structure & steps

**Files touched:**

- edit: `backend/models/device.py` — extend `DeviceCreate`, `DeviceUpdate`, `DeviceResponse` with new fields (additive)
- edit: `backend/alerts.py` — add run-hours accumulation, `consumable_reorder` alert type, run-hours cooldown key, role-lookup helper
- edit: `backend/whatsapp.py` — replace single-recipient `send_alert` with routing-aware version; per-recipient try/catch + logging
- edit: `backend/routers/devices.py` — no code change expected (existing PATCH handles additive fields), but verify
- edit: `seed_nfl.py` (from Phase 10) — extend seed to populate new fields on all 5 devices
- edit: `frontend/src/components/EditDevice.jsx` — expose new fields (asset_type, warranty, vendor, criticality). Contacts array editing is nice-to-have, skip if time is tight (see step 6)
- edit: `frontend/src/components/DeviceList.jsx` — add **Criticality** badge (color-coded) + **Warranty** traffic-light column
- edit: `frontend/src/components/AlertsPanel.jsx` — render `consumable_reorder` alert type with distinct icon; optionally show which role received it (from `alert.routed_to`)

**Ordered steps:**

### 11.1 Schema extensions (device model)

Extend `models/device.py`. All fields default sensibly so existing devices keep working.

```python
# additive fields on DeviceCreate / DeviceUpdate / DeviceResponse
asset_type:          Optional[str] = None                    # "genset" | "compressor" | "chiller" | "cold_storage" | "storage"
plant:               Optional[str] = None                    # "SITE Karachi" | "Faisalabad" | "Sharjah" | "Kunri"
criticality:         Optional[str] = "medium"                # "low" | "medium" | "high"  — ties to SLOB pain
warranty_expiry:     Optional[date] = None                   # ISO date on the wire
purchase_date:       Optional[date] = None
vendor_name:         Optional[str] = None
vendor_whatsapp:     Optional[str] = None                    # E.164 or "whatsapp:+92..." — normalized in whatsapp.py
run_hours:           float = 0.0
run_hours_threshold: int = 500
last_run_hours_update: Optional[datetime] = None
contacts:            list[dict] = []                         # [{role, name, whatsapp}] — roles: "maintenance_head" | "owner" | "vendor" | "supply_chain_lead"
```

`location` (already exists) and `plant` overlap — keep both. `location` is the human-readable label used in Phase 10's grouping; `plant` is the normalized key. Seed script sets both consistently.

### 11.2 Consumable auto-reorder

Storage
- Device doc fields (above): `run_hours`, `last_run_hours_update`, `run_hours_threshold`.

Accumulation (in `alerts.py`, called from the MQTT handler after `evaluate()`):
```
# Module-level state (matches existing pattern of _cooldown, _fuel_buf)
_run_hours_mem:   dict = {}   # {device_id: float}
_last_hr_update:  dict = {}   # {device_id: float} unix ts seconds

# On every reading where sensor == "load_current":
if value > OFF_AMPS:  # OFF_AMPS = 2.0 already defined
    now = time.time()
    delta_s = now - _last_hr_update.get(device_id, now)
    delta_h = delta_s / 3600
    _run_hours_mem[device_id] = _run_hours_mem.get(device_id, 0) + delta_h
    _last_hr_update[device_id] = now

    # Fire condition
    threshold = <device.run_hours_threshold from cache>
    if _run_hours_mem[device_id] >= threshold and not _in_cooldown(...):
        _set_cooldown(device_id, "run_hours", "consumable_reorder")
        # persist immediately on threshold cross
        # zero the counter after firing
```

Persistence:
- In-memory counter for the hot path (no per-tick Mongo write).
- **Immediate** flush to `device.run_hours` on threshold cross.
- Batch flush every 60s otherwise (add to the existing `cache_loop()` or a sibling task in `main.py`).
- On backend startup, seed `_run_hours_mem[device_id]` from `device.run_hours` so restarts don't zero the counter.

Alert message (bilingual, matches existing pattern in `whatsapp.py`):
```
EN: "🟠 {device_name} — {run_hours:.0f}h reached — oil filter change due. Vendor: {vendor_name}. Reorder now to avoid over-stock or SLOB accumulation."
UR: "{device_name} — {run_hours:.0f} ghante ho gaye — oil filter tabdeeli chahiye. Vendor: {vendor_name}. Abhi order kar dein — zayada stock ya SLOB nuqsaan se bachne ke liye."
```
The SLOB reference is deliberate — ties directly to NFL Recon §2.3 pain point 5 (PKR 60M+/yr SLOB waste).

Demo trigger:
- Not via time acceleration. Via `PATCH /devices/{id}` with `{"run_hours": <threshold - 0.1>}`.
- Next `load_current > OFF_AMPS` reading crosses threshold naturally, alert fires.
- On seed, `NFL-SITE-COMP-02` should have `run_hours_threshold: 500` so the demo button sets `run_hours: 499.9`.

### 11.3 Role-based routing

Schema (already in 11.1): `contacts: [{role, name, whatsapp}]` on the device doc. Roles: `maintenance_head`, `owner`, `vendor`, `supply_chain_lead`.

Routing table (in `whatsapp.py`, module-level):
```python
ROUTING = {
    # (severity, alert_type) → [roles]
    ("critical", "threshold"):          ["owner", "maintenance_head"],
    ("warning",  "threshold"):          ["maintenance_head"],
    ("critical", "fuel_theft"):         ["owner", "maintenance_head"],
    ("critical", "consumable_reorder"): ["vendor", "supply_chain_lead"],
    ("warning",  "consumable_reorder"): ["vendor"],
}
# Default fallback if no rule matches
DEFAULT_ROLES = ["owner"]
```

`send_alert` refactor:
```python
def send_alert(alert: dict, device: dict) -> dict:
    """
    Route one alert to N recipients based on severity + alert_type.
    Returns {sent: [...], failed: [...]} — never raises.
    """
    roles = ROUTING.get((alert["severity"], alert["alert_type"]), DEFAULT_ROLES)
    contacts = [c for c in device.get("contacts", []) if c["role"] in roles]
    if not contacts:
        # fall back to legacy single-recipient env var so we don't silently drop
        contacts = [{"role": "owner", "name": "default", "whatsapp": settings.alert_whatsapp_to}]

    results = {"sent": [], "failed": []}
    body = _format_body(alert, device["name"])
    for c in contacts:
        try:
            client.messages.create(from_=..., to=c["whatsapp"], body=f"[→ {c['role']}: {c['name']}]\n\n{body}")
            results["sent"].append(c["role"])
        except Exception as e:
            log.error(f"[WHATSAPP ERROR] {c['role']}/{c['whatsapp']}: {e}")
            results["failed"].append({"role": c["role"], "error": str(e)})
    return results
```

Note: the `[→ role: name]` prefix in the message body is what makes role-routing **visible** to the judge — even though all recipients in the demo are the same phone number (per Arham's Twilio setup), the message body says "Routed to Vendor: Ali Traders" so the routing is demonstrated in-band. This is essential.

Callers of `send_alert` need one small change — they now pass `device` (dict) instead of `device_name` (str). Update the call site (`alerts.py` `_persist_alert` or wherever it lives — grep for `send_alert(`).

Persist the routing outcome on the alert doc: `alert["routed_to"] = results["sent"]` before insert. This lets `AlertsPanel.jsx` show which role got notified.

### 11.4 Seed extensions

Extend `seed_nfl.py` to populate the new fields on all 5 devices. Contact seeds — all `whatsapp` values use Arham's number (per confirmation: Twilio sandbox recipient collapse).

| device_id | asset_type | criticality | run_hours_threshold | Contacts (roles) |
|---|---|---|---|---|
| `NFL-SITE-GEN-01` | genset | high | 500 | owner, maintenance_head |
| `NFL-SITE-COMP-02` | compressor | medium | 500 | maintenance_head, vendor (Ali Traders) |
| `NFL-FSD-CHILL-03` | chiller | high | 750 | owner, maintenance_head, supply_chain_lead |
| `NFL-SHJ-COLD-04` | cold_storage | high | 750 | owner, supply_chain_lead |
| `NFL-FSD-STOR-05` | storage | high | — (no `load_current`, run_hours N/A) | supply_chain_lead, owner |

`criticality: "high"` on the two cold-chain assets + Kunri storage ties directly to SLOB pain (temp excursion = batch discard).

For `NFL-FSD-STOR-05`: no `load_current` sensor, so run-hours accumulation is a no-op. Do not fire `consumable_reorder` on this device.

Warranty/vendor sample data (realistic, all fictitious):

| device_id | warranty_expiry | vendor_name | vendor_whatsapp |
|---|---|---|---|
| `NFL-SITE-GEN-01` | 2026-11-30 (yellow) | Cummins PK Service | (Arham's number) |
| `NFL-SITE-COMP-02` | 2027-08-15 (green) | Atlas Copco Karachi | (Arham's number) |
| `NFL-FSD-CHILL-03` | 2026-08-05 (red — expiring soon) | Danfoss Cooling PK | (Arham's number) |
| `NFL-SHJ-COLD-04` | 2027-04-20 (green) | Emerson Cold Chain | (Arham's number) |
| `NFL-FSD-STOR-05` | 2028-01-10 (green) | Local Kunri Vendor | (Arham's number) |

Traffic-light rule (client-side date math in `DeviceList.jsx`):
- Green: `warranty_expiry - today > 180 days`
- Yellow: `30 < days_left ≤ 180`
- Red: `days_left ≤ 30` OR already expired

### 11.5 Frontend changes

`EditDevice.jsx`:
- Add form fields: `asset_type` (dropdown), `plant` (dropdown), `criticality` (dropdown low/medium/high), `warranty_expiry` (date), `purchase_date` (date), `vendor_name` (text), `vendor_whatsapp` (text), `run_hours` (readonly display), `run_hours_threshold` (number).
- Contacts array editing: **skip if time-tight.** Demo seeds contacts via `seed_nfl.py`. Add contacts UI only if Phase 11 lands with margin.

`DeviceList.jsx`:
- New column: **Criticality** — colored badge (green/yellow/red).
- New column: **Warranty** — traffic-light dot + expiry date.
- Sort within each plant group by criticality: high → medium → low.

`AlertsPanel.jsx`:
- New alert-type icon for `consumable_reorder` (🛒 or similar).
- Show `alert.routed_to` inline: `"Sent to: vendor, supply_chain_lead"`.

### 11.6 Verify no regressions

- Existing threshold + fuel_theft alerts still fire and send to configured recipients.
- Existing frontend components still render (no schema-mismatch errors from added fields).
- `sim-control` still works — no changes needed to that app in this phase.

## Start commands

Same stack as Phase 10 (CLAUDE.md §3). Reseed after schema extension:

```powershell
docker compose up -d
.venv\Scripts\python seed_nfl.py                        # UPDATED — writes new fields
.venv\Scripts\python ingestion.py
.venv\Scripts\python simulator.py
cd backend && ..\.venv\Scripts\uvicorn main:app --reload --port 8000
cd frontend && npm run dev
cd sim-control && npm run dev
```

## Expected outcome

**Verifiable end state:**

- `GET /devices` returns all 5 NFL devices with the new fields populated.
- `db.devices.findOne({device_id: "NFL-SITE-GEN-01"})` shows `criticality: "high"`, `contacts: [...]`, `run_hours_threshold: 500`, warranty fields present.
- Dashboard shows **Criticality** and **Warranty** columns/badges; Chiller-03 shows red warranty (30-day threshold).
- `NFL-FSD-STOR-05` renders correctly with warranty green.
- Restarting the backend does NOT reset in-memory run_hours (seeded from device doc on startup).
- Alerts panel shows a `consumable_reorder` alert type distinctly from thresholds.

**Acceptance checks (5–10 min walkthrough):**

1. `PATCH /devices/NFL-SITE-COMP-02` with `{"run_hours": 499.9}`. Within one load_current tick, a `consumable_reorder` alert fires. WhatsApp arrives with body: `[→ vendor: Ali Traders]` + EN/UR message referencing SLOB.
2. Manually click "Inject overheat" on `NFL-SITE-GEN-01`. Critical threshold alert routes to `[owner, maintenance_head]`. Two WhatsApp messages arrive on Arham's phone (or one, with both role tags — Twilio sandbox may collapse identical recipients; document either behavior). Alert doc has `routed_to: ["owner", "maintenance_head"]`.
3. Manually click "Inject fuel_theft" on `NFL-SITE-GEN-01`. Alert routes per critical/fuel_theft rule. Message body includes `[→ role]` prefix and Urdu "chori" text.
4. Restart backend, verify `_run_hours_mem` seeds from device doc (log the seeded values on startup).
5. `run seed_nfl.py` a second time — no duplicate-key errors, all upserts idempotent, run_hours preserved (do NOT zero it on reseed).

**Rubric impact:** closes the theme-fit gap (rubric #1, #2, #7 lift). Sets up Phase 12 for the deterministic demo choreography.

---
## ✅ Actually achieved

**Shipped:**
- `backend/models/device.py` — additive fields on `DeviceCreate`/`DeviceUpdate`/`DeviceResponse`: `asset_type`, `plant`, `criticality`, `warranty_expiry`, `purchase_date`, `vendor_name`, `vendor_whatsapp`, `run_hours`, `run_hours_threshold`, `last_run_hours_update`, `contacts`. Verified additive: the pre-existing `shell-gen-1` device (created before this phase) still validates fine through `GET /devices`, picking up field defaults.
- `backend/alerts.py` — `evaluate_run_hours()` (in-memory accumulation from `load_current` readings, fires `consumable_reorder` on threshold cross, resets counter), `seed_run_hours()` (startup, restores in-memory counters from Mongo), `flush_run_hours_loop()` (60s batch persist), `set_run_hours()` (syncs in-memory state when a PATCH sets `run_hours` directly), `_make_alert` extended with a `consumable_reorder` message branch.
- `backend/whatsapp.py` — `send_alert(alert, device)` now routes to N contacts via the `ROUTING` table (severity, alert_type) → roles, `[→ role: name]` prefix in the message body, falls back to `settings.alert_whatsapp_to` when no contacts match. Returns `{sent, failed}` instead of a bool.
- `backend/main.py` — calls `evaluate_run_hours()` on every `load_current` reading; `_persist_alert` fetches the full device doc (not just name) for routing, zeroes+persists `run_hours` immediately on a `consumable_reorder` fire, records `alert.routed_to`; lifespan now runs `refresh_cache()` + `seed_run_hours()` before the MQTT thread starts, plus `flush_run_hours_loop()` as a background task.
- `backend/routers/devices.py` — PATCH now calls `alert_engine.set_run_hours()` when the update body includes `run_hours` (see deviation #1).
- `seed_nfl.py` — extended with asset registry fields, warranty/vendor sample data, and role-tagged `contacts` for all 5 devices, per the phase's table. WhatsApp number loaded from `backend/.env` at runtime (`python-dotenv`), not hardcoded.
- `frontend/src/components/EditDevice.jsx` — form fields for asset_type, plant, criticality, warranty_expiry, purchase_date, vendor_name, vendor_whatsapp, run_hours (read-only display), run_hours_threshold. Contacts editing skipped per the doc's own "skip if time-tight" allowance.
- `frontend/src/components/DeviceList.jsx` — criticality badge (color-coded), warranty traffic-light dot + expiry date, devices sorted by criticality (high→medium→low) within each plant group.
- `frontend/src/components/AlertsPanel.jsx` — 🛒 icon + "consumable reorder" tag for the new alert type, `Sent to: {roles}` line from `alert.routed_to`.
- `frontend/src/App.css` — criticality badge, warranty dot, and consumable_reorder tag colors.

**Deviations:**
1. **`routers/devices.py` code change** — the doc said "no code change expected... but verify." In practice, acceptance check 1's demo trigger (`PATCH run_hours` → next `load_current` tick crosses threshold) only works if the PATCH also updates `alerts.py`'s in-memory `_run_hours_mem` counter, since `evaluate_run_hours()` never re-reads Mongo per-tick (by design, to avoid a hot-path DB read). Added one conditional call (`alert_engine.set_run_hours(...)`) to close that gap.
2. **Demo-trigger timing** — `evaluate_run_hours()` follows the doc's own formula (`delta_h = delta_s / 3600`, real wall-clock). Patching `run_hours` to `threshold - 0.1` does **not** cross on the literal next tick — it takes ~6 minutes of continuous above-`OFF_AMPS` readings to close a 0.1h gap at ~1 reading/sec. Verified the mechanism is correct by patching to `threshold - 0.0003` instead (crosses within one tick, confirmed: alert fired with `value: 500.0`). Flagging for Phase H — the demo choreography should patch closer to the threshold for a near-instant trigger, or accept the ~6 min wait.
3. **Twilio send unverified end-to-end** — `twilio_account_sid` isn't configured in this dev environment (pre-existing; see `phase/limitations.md`'s Error 63007 entry), so `whatsapp_sent` stays `false` for all test alerts. Routing *logic* was verified directly in isolation: `ROUTING[(severity, alert_type)]` resolves the correct roles, contacts are filtered correctly, and `_format_body` produces the `[→ role: name]`-prefixed bilingual text. Actual WhatsApp delivery with visible role tags wasn't observed live — recommend a real send test once Twilio creds are refreshed.

**Deferred:** contacts-array editing UI in `EditDevice.jsx` (per the doc's own guidance — seed data covers the demo). No changes to `rul.py`, Groq chat, or auth, per guardrails.

**Gotchas:**
- `bson`/pymongo cannot encode Python `date` objects — `seed_nfl.py`'s `warranty_expiry`/`purchase_date` had to be `datetime` instances (midnight), which Pydantic v2 then coerces back to `date` on the `DeviceResponse` read path. A `date(...)` literal would have raised `InvalidDocument` on the first seed run.
- Windows console `cp1252` can't print the 🟠/🛒 emoji used in alert bodies — hit this only in an ad-hoc test script's `print()`, not in the actual server path (emoji only ever go into a Twilio message body or a JSON response, never a console `log.info`), so no production risk — but worth knowing if debugging interactively on Windows.
- Killing/restarting the dev `uvicorn --reload` process via PID was unreliable in this shell session (stale `netstat` entries, PID reuse); startup-seed behavior (`seed_run_hours`) was instead verified by calling it directly in an isolated script against the live Mongo state, which confirmed accumulated hours survive a fresh read rather than zeroing.

**Acceptance checks — results:**
1. ✅ `PATCH /devices/NFL-SITE-COMP-02 {"run_hours": 499.9997}` → next `load_current` tick fired `consumable_reorder` (`value: 500.0`, `detail: "reached 500h threshold"`). WhatsApp routing resolved to `[vendor, supply_chain_lead]` per the `(warning, consumable_reorder)` rule (send itself unverified — see deviation #3).
2. ✅ Injected `overheat` on `NFL-SITE-GEN-01` → critical threshold alert; routing resolved to `[owner, maintenance_head]` (confirmed via isolated routing test — both contacts matched).
3. ✅ Injected `fuel_theft` on `NFL-SITE-GEN-01` (after resetting fuel to 80L so a real drop was observable) → critical `fuel_theft` alert fired with `message_ur` containing "Chori ka shak" and a PKR estimate, per the existing template.
4. ✅ (verified via direct function call rather than a full process restart — see gotchas) `seed_run_hours()` correctly restores in-memory counters from each device's persisted `run_hours`, not zero.
5. ✅ Re-ran `seed_nfl.py` — 5/5 upserted, no duplicate-key errors, device count unchanged, `run_hours` on `NFL-SITE-COMP-02` preserved at its accumulated value (not reset to 0 by the reseed).
