# Phase 10 — NFL/SCAPM reframe (content, labels, seed)

## Goal

Reframe the existing platform around NFL and the SCAPM category **without touching any logic**. Seed 5 NFL-flavored devices (SITE Karachi genset + compressor, Faisalabad chiller, Sharjah cold storage, **Kunri red chili storage** — addresses the aflatoxin sourcing pain from NFL Recon §2.3), kill the stale "TwinLab AI" brand string across code/UI, add a visible `SIMULATED` badge so the theft alert can appear on stage without contradicting the vault's "do not claim theft detection" rule (Product_and_Demo §4 blocker #5), and group the dashboard by plant so the cross-plant-inconsistency pain (COO Hasan Sarwat, NFL Recon §2.3) is visible in one glance.

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
   | `NFL-FSD-STOR-05` | NFL Kunri Sourcing — Red Chili Cold Storage | Kunri (sourced by Faisalabad) | `temperature`, `humidity` | `{temperature: {max: 15, min: 5}, humidity: {max: 65}}` | `{temperature: 10, humidity: 55}` |

   Per-device `base_values` are essential — chiller/cold-storage temps are cold, and the simulator's global default (`35 C`) would trip max thresholds on tick 1.

   **Note on `NFL-FSD-STOR-05`:** aflatoxin risk in raw red chili sits above ~65% relative humidity at warm storage temps. Humidity max = 65 is the risk envelope. Judge-facing rationale: *"Aflatoxin contamination is one of NFL's own disclosed pain points (NFL Recon §2.3); this device demonstrates upstream sourcing monitoring, not just downstream process control."*

   `sim_control` doc shape follows `_default_ctrl()` in `backend/routers/sim.py`: `{device_id, generator_on: True, base_values, inject: {fuel_theft/overheat/overload/offline: {active: false, until_ts: 0}}, updated_at: iso}`.

3. **`frontend/src/App.jsx` — header tagline.**
   - Change header title from whatever it currently reads to: **"TwinLab"** with a small tagline underneath: **"SCAPM for Pakistani Industry"**.
   - Do not restyle — Muskan/Claude design owns visual polish. Just replace the strings.

4. **`frontend/src/components/DeviceList.jsx` — four additions.**
   - Add a **Plant** column (renders `device.location`). Order: Device | Plant | Sensors | Status | (existing actions).
   - Add a **SIMULATED** pill/badge next to `device_id` (or in a Source column, whichever the current layout permits) rendered when `device.source === "simulator"`. Style: red/orange background, white text, small caps, `SIMULATED`. Purpose is visibility not aesthetics — Claude design refines later.
   - **Group rows by plant** (or add a plant filter). Use `device.location` as the group key. Order plants: SITE Karachi → Faisalabad → Sharjah → Kunri. Each group shows a small header row (`SITE Karachi Plant · 2 devices`). This makes the cross-plant view visible without a separate route.
   - **Cross-plant summary strip** above the list: `N plants · N assets · N active alerts today`. Data sources: `location` distinct count on devices, total device count, alerts collection filtered on `created_at >= startOfDay`. If the alerts fetch is expensive, hard-code "N alerts today" as `alerts.length` from the existing AlertsPanel state — do not add a new endpoint this phase.
   - No sort/filter changes beyond plant grouping. No new backend endpoints. `source`, `location`, and alerts are already available.

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
- `db.devices.count({device_id: /^NFL-/})` returns **5**.
- `db.sim_control.count({device_id: /^NFL-/})` returns **5**.
- Re-running `seed_nfl.py` produces `[SEED] upserted ...` for all 5 with no duplicate-key errors and no doc-count change.
- Dashboard at `http://localhost:5173` shows:
  - Header reads "TwinLab" with "SCAPM for Pakistani Industry" tagline.
  - Cross-plant summary strip visible near the top: `4 plants · 5 assets · N active alerts today`.
  - All 5 NFL devices listed, grouped by plant (SITE Karachi has 2, Faisalabad has 1, Sharjah has 1, Kunri has 1). Group headers visible.
  - Each row shows the **SIMULATED** badge (all 5 are source=simulator this phase).
  - Live charts stream for each device without any alerts firing at baseline (i.e. base_values keep readings inside thresholds).
- Sim-control app lists the 5 NFL devices (the existing `/sim` endpoint returns all `source: "simulator"` docs).

**Acceptance checks (walk-through, ~5 min):**

1. Stop simulator + backend, wipe `devices` and `sim_control` collections, re-run `seed_nfl.py`, restart stack → all 4 devices reappear identically.
2. Manually click "Inject overheat" on `NFL-SITE-GEN-01` in sim-control → threshold alert fires within one cooldown window with device name shown correctly in EN + UR.
3. No alert fires on any of the 5 devices when injectors are all inactive (baseline sanity — including the Kunri storage device at 55% RH / 10°C).
4. Grep the frontend build (`frontend/dist` if built) for `"TwinLab AI"` → zero hits.

**Explicitly deferred to Phase 11:** asset registry fields (warranty/vendor/purchase), run_hours counter, consumable_reorder alert, role-based routing. Do not scope-creep this phase.

---
## ✅ Actually achieved

**Shipped:**
- `seed_nfl.py` (repo root) — idempotent pymongo upsert script, 5 NFL devices + matching `sim_control` docs. Verified: fresh seed → 5/5; wipe + re-seed → 5/5 identical, no dupes.
- `"TwinLab AI"` → `"TwinLab"` in all product-facing surfaces: `assets/banner.svg`, `assets/folder-map.svg`, `backend/routers/chat.py` (system prompt), `frontend/src/components/ChatPanel.jsx` (2 strings), `phase/twinlab-build-summary.md`.
- `frontend/src/App.jsx` — navbar now reads "TwinLab" / "SCAPM for Pakistani Industry" (dropped the old "PRO" suffix span, reused the existing `navbar-sub` slot for the tagline — no restyle).
- `frontend/src/components/DeviceList.jsx` — cross-plant summary strip, plant-grouped rendering (custom order: SITE Karachi → Faisalabad → Sharjah → Kunri, unlisted plants sorted after), SIMULATED badge.
- `frontend/index.html` — `<title>TwinLab — SCAPM</title>`.
- `frontend/src/App.css` — badge recolor (red/orange bg, white text) + plant-group/summary-strip styles (structural only, no visual redesign).

**Deviations (all confirmed with Arham before implementing):**
1. **Grep-replace scope** — limited to product-facing files (assets, chat.py, ChatPanel.jsx, build-summary doc). Left `CLAUDE.md`, `MVP_v2_PLAN.md`, `phase-9.md`, and this file untouched — they quote `"TwinLab AI"` while describing the cleanup task itself; blind-replacing would garble sentences like CLAUDE.md's `not "TwinLab AI"`.
2. **Seeded `base_values.temperature`** for `NFL-SITE-GEN-01` (35→30), `NFL-SHJ-COLD-04` (-20→-25), `NFL-FSD-STOR-05` (10→5) — a -5°C offset vs. the literal table in this doc. Reason: `simulator.py` already adds +5°C to `base_values.temperature` whenever `generator_on: true` (its pre-existing "engine running warms things up" model). Seeding the table's literal numbers would land baseline noise straddling each device's max threshold, causing ~50% baseline alert rate — a direct violation of acceptance check 3. The offset is called out with a `ponytail:` comment in `seed_nfl.py`. `NFL-SITE-COMP-02` and `NFL-FSD-CHILL-03` didn't need the offset (their +5 headroom still clears their max thresholds).
3. **SIMULATED badge** — repurposed the existing `source-badge--simulator` element (text `SIM` → `SIMULATED`, color green → red/orange bg + white text) instead of adding a second duplicate badge.
4. **Plant column / grouping** — `DeviceList.jsx` is a card list, not a table (phase doc's table language assumed a layout that doesn't exist here; the doc itself permits "whichever the current layout permits"). Implemented plant visibility as group header rows (`SITE Karachi Plant · 2 devices`) instead of a literal column; kept the existing per-card `device-location` line as-is.
5. **Cross-plant alerts count** — no new backend endpoint. `alertsToday` is computed client-side in `DeviceList.jsx` by calling the existing per-device `GET /devices/{id}/alerts` for every listed device (`Promise.all`) and filtering `ts >= startOfDay`, per the doc's own fallback guidance.

**Deferred:** nothing new — all Phase G scope (asset registry, run_hours, role-based routing, alert-engine/schema changes) untouched, per guardrails.

**Gotchas:**
- The `simulator.py` generator-warm-up interaction (deviation #2) wasn't visible from the phase-10 table alone — only surfaced by tracing `_compute_values()` before seeding. Worth a note for future phases that touch `base_values`.
- A pre-existing legacy device (`shell-gen-1`, Shell Mirpurkhas, `source: simulator`) is unaffected by this phase's NFL scoping but now also gets the SIMULATED badge and its own plant group — correct behavior, since the badge/grouping conditions are general (`device.source`, `device.location`), not NFL-specific.
- Claude-in-Chrome browser extension wasn't connected this session, so the dashboard was verified via API-level checks (`/devices`, `/sim`, `/devices/{id}/alerts`) and a production `vite build` + grep, rather than a live screenshot walkthrough. Recommend a quick visual pass before the pitch.

**Acceptance checks — results:**
1. ✅ Wiped `devices`/`sim_control` (NFL-prefixed), re-ran `seed_nfl.py` → 5/5 reappeared identically.
2. ✅ Injected `overheat` on `NFL-SITE-GEN-01` via `PUT /sim/NFL-SITE-GEN-01` → threshold alert fired (`temperature = 96.12 C, above max 40`, severity critical) with EN + UR messages within the same cooldown window.
3. ✅ With injectors inactive, polled all 5 devices' alerts after a full ingest cycle — zero alerts fired on any device except the one intentionally injected in check 2 (which was then cleared).
4. ✅ `npm run build` → `grep -rniI "TwinLab AI" frontend/dist` → zero hits.
