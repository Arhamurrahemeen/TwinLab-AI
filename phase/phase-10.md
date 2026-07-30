# Phase 10 — NFL/SCAPM reframe (content, labels, seed)

## Goal

Reframe the existing platform around NFL and the SCAPM category **without touching any logic**. Seed 4 NFL-flavored devices (SITE Karachi genset + compressor, Faisalabad chiller, Sharjah cold storage), kill the stale "TwinLab AI" brand string across code/UI, and add a visible `SIMULATED` badge so the theft alert can appear on stage without contradicting the vault's "do not claim theft detection" rule (Product_and_Demo §4 blocker #5).

Note: this phase absorbs the brand-string sub-task originally scoped in phase-9 (deferred — hardware is out of scope for this hackathon).

Out of scope (guardrail): no schema changes on the device model, no new alert types, no changes to `alerts.py` or `whatsapp.py` logic, no new sensors, no run-hours counter, no role-based routing. Those are Phase 11.

## Structure & steps

**Files touched:**

- new: `seed_nfl.py` (repo root — flat pymongo script per CLAUDE.md §5)
- edit: `frontend/src/App.jsx` (header tagline)
- edit: `frontend/src/components/DeviceList.jsx` (SIMULATED badge + plant column)
- edit: any file containing the literal string `"TwinLab AI"` (grep-replace to `"TwinLab"` — the product name stays "TwinLab" per CLAUDE.md §6; SCAPM is category positioning, not part of the product name)
- edit: `README.md` (top-of-file description reframed as SCAPM)
- edit: `frontend/index.html` (page `<title>`)

**Ordered steps:**

1. **Brand-string grep-replace (destructive, do this first so subsequent edits don't reintroduce it).**
   - `grep -rniI --exclude-dir={.venv,node_modules,.git,__pycache__,dist} "TwinLab AI" .` — list every hit
   - Replace each with `TwinLab`. Do not touch the GitHub repo name (`TwinLab-AI`) — that stays per CLAUDE.md §6.
   - Verify: same grep should return zero hits after.

2. **Write `seed_nfl.py` (idempotent, upsert-based).**
   - Connects to Mongo with the CLAUDE.md-standard URI (`mongodb://admin:twinlab123@localhost:27017`, DB `twinlab`).
   - Upserts 4 devices into `devices` collection via `update_one({device_id}, {$set: {...}}, upsert=True)`. Do NOT insert — script must be safe to re-run.
   - Upserts a matching `sim_control` doc per device with realistic `base_values` so alerts don't fire spuriously at baseline.
   - Uses the existing `DeviceCreate` shape (device_id, name, location, sensors, description, source="simulator", thresholds, status="active"). No schema extension in this phase.
   - Prints `[SEED] upserted {device_id}` per row. `[OK]` at the end.

   **Devices:**

   | device_id | name | location | sensors | thresholds | sim_control base_values |
   |---|---|---|---|---|---|
   | `NFL-SITE-GEN-01` | NFL SITE Karachi — Standby Genset 1 | SITE Karachi Plant | `fuel_level`, `load_current`, `temperature` | `{temperature: {max: 40}, load_current: {max: 30, min: 0}, fuel_level: {min: 20}}` | `{fuel_level: 70, load_current: 18, temperature: 35}` |
   | `NFL-SITE-COMP-02` | NFL SITE Karachi — Air Compressor 2 | SITE Karachi Plant | `load_current`, `temperature`, `accel_x`, `accel_y`, `accel_z` | `{temperature: {max: 55}, load_current: {max: 25}}` | `{load_current: 15, temperature: 42}` |
   | `NFL-FSD-CHILL-03` | NFL Faisalabad — Process Chiller 3 | Faisalabad Plant | `temperature`, `humidity`, `load_current` | `{temperature: {max: 8, min: -5}, load_current: {max: 20}}` | `{temperature: 2, humidity: 60, load_current: 12}` |
   | `NFL-SHJ-COLD-04` | NFL Sharjah — Cold Storage Unit 4 | Sharjah Plant | `temperature`, `humidity` | `{temperature: {max: -15, min: -25}, humidity: {max: 90}}` | `{temperature: -20, humidity: 65}` |

   Per-device `base_values` are essential — chiller/cold-storage temps are cold, and the simulator's global default (`35 C`) would trip max thresholds on tick 1.

   `sim_control` doc shape follows `_default_ctrl()` in `backend/routers/sim.py`: `{device_id, generator_on: True, base_values, inject: {fuel_theft/overheat/overload/offline: {active: false, until_ts: 0}}, updated_at: iso}`.

3. **`frontend/src/App.jsx` — header tagline.**
   - Change header title from whatever it currently reads to: **"TwinLab"** with a small tagline underneath: **"SCAPM for Pakistani Industry"**.
   - Do not restyle — Muskan/Claude design owns visual polish. Just replace the strings.

4. **`frontend/src/components/DeviceList.jsx` — two additions.**
   - Add a **Plant** column (renders `device.location`). Order: Device | Plant | Sensors | Status | (existing actions).
   - Add a **SIMULATED** pill/badge next to `device_id` (or in a Source column, whichever the current layout permits) rendered when `device.source === "simulator"`. Style: red/orange background, white text, small caps, `SIMULATED`. Purpose is visibility not aesthetics — Claude design refines later.
   - No sort/filter changes. No new data fetches — `source` and `location` are already on the device doc.

5. **`README.md` and `frontend/index.html`.**
   - README top paragraph: reframe from generic IoT description to SCAPM one-liner. Suggested first line: *"TwinLab — Supply Chain Asset Performance Management (SCAPM) platform. Non-invasive condition monitoring + WhatsApp alerts, priced in PKR."*
   - `<title>` tag: `TwinLab — SCAPM`

6. **Manual verification (see acceptance checks below).**

## Start commands

Run from repo root `D:\TwinLab` in PowerShell. Assumes phase-8 stack conventions (CLAUDE.md §3):

```powershell
docker compose up -d                                    # Mosquitto + InfluxDB + MongoDB
.venv\Scripts\python seed_nfl.py                        # NEW — idempotent seed
.venv\Scripts\python ingestion.py                       # MQTT -> InfluxDB
.venv\Scripts\python simulator.py                       # publishes for the 4 seeded devices
cd backend && ..\.venv\Scripts\uvicorn main:app --reload --port 8000
cd frontend && npm run dev                              # http://localhost:5173
cd sim-control && npm run dev                           # sim control mini-app
```

Order matters: `docker compose up` → `seed_nfl.py` (needs Mongo up) → everything else. Simulator won't publish for a device that isn't in the registry.

## Expected outcome

**Verifiable end state:**

- `grep -rniI --exclude-dir={.venv,node_modules,.git,__pycache__,dist} "TwinLab AI" .` returns **zero hits** across code/UI (the GitHub remote URL in `.git/config` is allowed to remain).
- `db.devices.count({device_id: /^NFL-/})` returns **4**.
- `db.sim_control.count({device_id: /^NFL-/})` returns **4**.
- Re-running `seed_nfl.py` produces `[SEED] upserted ...` for all 4 with no duplicate-key errors and no doc-count change.
- Dashboard at `http://localhost:5173` shows:
  - Header reads "TwinLab" with "SCAPM for Pakistani Industry" tagline.
  - All 4 NFL devices listed with correct plant labels.
  - Each row shows the **SIMULATED** badge (all 4 are source=simulator this phase).
  - Live charts stream for each device without any alerts firing at baseline (i.e. base_values keep readings inside thresholds).
- Sim-control app lists the 4 NFL devices (the existing `/sim` endpoint returns all `source: "simulator"` docs).

**Acceptance checks (walk-through, ~5 min):**

1. Stop simulator + backend, wipe `devices` and `sim_control` collections, re-run `seed_nfl.py`, restart stack → all 4 devices reappear identically.
2. Manually click "Inject overheat" on `NFL-SITE-GEN-01` in sim-control → threshold alert fires within one cooldown window with device name shown correctly in EN + UR.
3. No alert fires on any of the 4 devices when injectors are all inactive (baseline sanity).
4. Grep the frontend build (`frontend/dist` if built) for `"TwinLab AI"` → zero hits.

**Explicitly deferred to Phase 11:** asset registry fields (warranty/vendor/purchase), run_hours counter, consumable_reorder alert, role-based routing. Do not scope-creep this phase.

---
## ✅ Actually achieved   <!-- append after phase is done -->
<what shipped / deviations / deferrals / gotchas>
