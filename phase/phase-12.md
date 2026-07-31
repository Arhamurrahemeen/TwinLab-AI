# Phase 12 — Demo choreography + R&D benchmarks

## Goal

Make the 5-minute demo deterministic and one-click. Consolidate injector controls into a **Demo Control Panel** with 3 manual injector buttons + a **Demo Reset** button that fully clears state between runs. Capture **R&D benchmarks** during rehearsal into `phase/rd_benchmarks.md` — this partial-closes rubric criterion #4 (Traction), which is otherwise the biggest architecture-adjacent weakness. **Optional branch:** if the ESP32 + DHT22 can be plugged in with ≥2 hours to spare, wire a hybrid demo where the overheat segment uses a real physical stream — no code changes required (MQTT contract is source-agnostic).

Out of scope: no new alert types, no schema changes, no auth, no changes to `alerts.py` engine logic (only a state-reset helper is added). This phase is choreography and safety-net work.

## Structure & steps

**Files touched:**

- edit: `sim-control/src/components/DeviceControl.jsx` — audit existing UI first (fuel_theft, overheat, overload, offline buttons already exist per phase-8 scope); add the 3 explicitly labeled demo buttons + Demo Reset button
- edit: `backend/routers/sim.py` — new `POST /sim/reset` endpoint
- edit: `backend/alerts.py` — new `reset_state()` helper (clears `_cooldown`, `_fuel_buf`, `_run_hours_mem`, `_last_hr_update`, then optionally reloads run_hours from Mongo)
- new: `phase/rd_benchmarks.md` — captured during a rehearsal run (see step 4)
- edit: `README.md` — update demo instructions with the new Demo Control Panel workflow (small)

**Ordered steps:**

### 12.1 Audit existing sim-control UI

Before adding buttons, read `sim-control/src/components/DeviceControl.jsx` and list which injector buttons already exist. Phase 8 shipped injectors for `fuel_theft`, `overheat`, `overload`, `offline`. If they're already labeled and functional, this phase mostly **reorganizes** them into a demo-mode panel rather than creating from scratch.

### 12.2 Demo Control Panel (in sim-control app)

Add a compact panel above the device list. Four buttons in a row, one action each. Buttons are context-aware — they know which device to target (hard-coded to the demo device IDs from Phase 10 seed).

```
┌─ DEMO CONTROLS ────────────────────────────────────────────────────┐
│ [1. Inject Overheat]  [2. Inject Consumable]  [3. Inject Theft ⚠] │
│                                                    [Demo Reset]    │
└────────────────────────────────────────────────────────────────────┘
```

Button 1 — **Inject Overheat**
- Target: `NFL-SITE-GEN-01`
- Action: `PUT /sim/NFL-SITE-GEN-01` with `inject.overheat.active = true`, `until_ts = now + 90s`
- Expected: temperature reading crosses 40°C max within 1–2 ticks; critical threshold alert fires; WhatsApp routes to `[owner, maintenance_head]`

Button 2 — **Inject Consumable Reorder**
- Target: `NFL-SITE-COMP-02`
- Action: `PATCH /devices/NFL-SITE-COMP-02` with `{"run_hours": <run_hours_threshold - 0.1>}` (read the threshold from GET first, don't hard-code)
- Expected: next `load_current > OFF_AMPS` tick crosses threshold; `consumable_reorder` alert fires; WhatsApp routes to `[vendor, supply_chain_lead]` with SLOB-referencing copy

Button 3 — **Inject Fuel Theft** (with a red "SIMULATED" pill next to the label)
- Target: `NFL-SITE-GEN-01`
- Action: `PUT /sim/NFL-SITE-GEN-01` with `generator_on = false`, `inject.fuel_theft.active = true`, `until_ts = now + 15s`
- Expected: fuel_level drops ≥5 L in ≤15s while generator off; `fuel_theft` alert fires with Urdu "chori" text; WhatsApp routes per critical/fuel_theft rule
- The red **SIMULATED** pill on the button pre-empts the theft-honesty objection *before* the alert appears

Button 4 — **Demo Reset**
- Action: `POST /sim/reset`
- Expected: within ~2s, all cooldowns cleared, all run_hours zeroed and re-flushed to Mongo, all injectors deactivated across all sim devices, base_values restored to seed defaults, last N minutes of alerts optionally deleted (see 12.4)

### 12.3 `POST /sim/reset` endpoint

New endpoint in `backend/routers/sim.py`. Does the following in order (idempotent, safe to spam-click):

```python
@router.post("/reset")
async def reset_demo():
    """Nuke transient state so the demo can be re-run cleanly."""
    from alerts import reset_state
    db = get_db()

    # 1. Reset alert-engine in-memory state
    reset_state()

    # 2. Deactivate all injectors on all sim devices, restore base_values
    sim_docs = await db.sim_control.find({}).to_list(length=200)
    for doc in sim_docs:
        for inj_name in ("fuel_theft", "overheat", "overload", "offline"):
            doc["inject"][inj_name] = {"active": False, "until_ts": 0}
        doc["generator_on"] = True
        # base_values: leave as-is (seeded per-device); do NOT overwrite to generic defaults
        await db.sim_control.replace_one({"device_id": doc["device_id"]}, doc)

    # 3. Zero run_hours on all devices; also zero the in-memory counters (reset_state did this)
    await db.devices.update_many({}, {"$set": {"run_hours": 0.0, "last_run_hours_update": None}})

    # 4. Optional: purge recent alerts so the panel starts clean
    from datetime import datetime, timezone, timedelta
    cutoff = datetime.now(timezone.utc) - timedelta(minutes=30)
    result = await db.alerts.delete_many({"created_at": {"$gte": cutoff}})

    return {"ok": True, "purged_alerts": result.deleted_count}
```

### 12.4 `reset_state()` helper in `alerts.py`

Small addition to `alerts.py`:

```python
def reset_state() -> None:
    """
    Clear all in-memory demo state. Called by POST /sim/reset.
    Does NOT touch _threshold_cache (that's config, not demo state).
    """
    global _cooldown, _fuel_buf, _run_hours_mem, _last_hr_update
    _cooldown.clear()
    _fuel_buf.clear()
    _run_hours_mem.clear()
    _last_hr_update.clear()
    log.info("[alerts] state reset — cooldowns/fuel_buf/run_hours cleared")
```

### 12.5 R&D benchmarks capture (rubric #4 lifter)

During Phase 12 dress rehearsal, run the demo end-to-end 3 times and capture numbers into a new file `phase/rd_benchmarks.md`. Structure:

```markdown
# TwinLab R&D Benchmarks — <date>

Measured during Phase 12 dress rehearsal. All numbers are from the current MVP v2 codebase
(commit <sha>) running on <hardware summary — e.g. "Arham's laptop, Docker Desktop, WSL2">.

## System reliability
- Continuous simulator runtime before crash / restart: N hours
- MQTT messages published across N devices × N sensors × N seconds: N messages
- InfluxDB writes: N successful / N attempted (X% success rate)
- Backend uptime during rehearsal: N minutes, N restarts

## Alert engine
- Threshold alerts fired: N (all correctly matched to configured thresholds)
- Fuel-theft alerts fired: N (all under 15s from injector activation)
- Consumable-reorder alerts fired: N (all fired within 1 load_current tick after PATCH)
- Cooldown correctness: 0 duplicate alerts within the 10-min window
- False positives at baseline (no injector active): 0

## WhatsApp delivery
- Alert-to-WhatsApp latency: p50 = N seconds, p95 = N seconds
- Delivery success: N sent / N attempted (X% delivered per Twilio API response)
- Multi-recipient routing verified: N routes across critical / warning / consumable_reorder

## Cross-source compatibility
- MQTT topic contract validated against 2 sources: simulator (live) + ESP32 firmware code
  (deferred to next hackathon but the contract is source-agnostic — see phase-9.md)

## Demo choreography
- Demo Reset time-to-clean: <N seconds
- Successful end-to-end demo runs: 3 / 3 during rehearsal
- Time between injector click and visible alert on dashboard: p50 = N seconds
```

Fill this with real numbers only. If a metric can't be measured cleanly, leave it out — do not fabricate. The rubric explicitly asks for *verifiable* metrics.

### 12.6 Optional hybrid hardware branch

Only if ≥2 hours free and the ESP32 + DHT22 both work on the bench:

- Do NOT write firmware from scratch (phase-9 is deferred). Use whatever Arduino sketch you have that publishes on the MQTT topic contract from CLAUDE.md §4.
- Register a hardware device via `POST /devices` with `source: "esp32"`, `device_id: "NFL-SITE-GEN-01-REAL"` (or reuse `TL-01` if that's how the firmware is coded).
- Point ESP32 at laptop LAN IP (not localhost — firmware note in CLAUDE.md §3).
- In the demo, when the "Inject Overheat" segment is up, physically heat the DHT22 with a hair dryer / hand warmth. Real temperature stream on stage.
- The `SIMULATED` badge disappears automatically for this device (source ≠ "simulator").
- All other segments (consumable, theft) stay simulator-only.

**Judge-facing narration in this case:** *"Physical sensor for the overheat segment — real temperature stream. Fuel-theft and consumable-reorder segments stay simulator-only because we haven't wired those sensors yet, marked SIMULATED accordingly."*

### 12.7 Screen-recording backup (non-negotiable)

Once Phase 12 works end-to-end:
- Record one clean demo run with OBS or the built-in Windows recorder.
- Save to laptop Desktop AND external USB.
- Rehearse switching to the recording in <10s so a stage failure is invisible.

## Start commands

Same as Phase 11 stack. Verify `POST /sim/reset` in Swagger UI (`http://localhost:8000/docs`) before running the demo.

```powershell
docker compose up -d
.venv\Scripts\python seed_nfl.py
.venv\Scripts\python ingestion.py
.venv\Scripts\python simulator.py
cd backend && ..\.venv\Scripts\uvicorn main:app --reload --port 8000
cd frontend && npm run dev
cd sim-control && npm run dev
```

Pre-demo run-through (5 min):
```powershell
# 1. Reset state
curl -X POST http://localhost:8000/sim/reset

# 2. Click each demo button in sim-control, one at a time, wait 30s between clicks

# 3. Confirm 3 alerts in dashboard AlertsPanel + 3 WhatsApp messages on phone

# 4. Click Demo Reset. Confirm alerts panel clears, no lingering injectors.
```

## Expected outcome

**Verifiable end state:**

- Demo Control Panel visible in sim-control app with 4 clearly labeled buttons.
- Fuel Theft button has visible **SIMULATED** pill.
- All 3 injector buttons produce a matching alert + WhatsApp within 30s of click.
- Demo Reset clears all transient state in <2s (measured); alerts panel empties; next demo run starts clean.
- `POST /sim/reset` visible in `/docs` and returns `{"ok": true, "purged_alerts": N}`.
- `phase/rd_benchmarks.md` exists with real captured numbers (no `TODO`s or placeholder text).
- Screen recording exists on Desktop + USB.
- (Optional) Hybrid hardware: `NFL-SITE-GEN-01-REAL` (or `TL-01`) appears in dashboard with **no** SIMULATED badge; overheat segment runs off the real DHT22.

**Acceptance checks (10-min dress rehearsal):**

1. Fresh backend restart. `_run_hours_mem` seeds from Mongo per Phase 11 rule.
2. Click each demo button in sequence with 30s pauses. All 3 alerts fire, all route correctly, all reach the phone. Alert bodies include `[→ role: name]` prefix and correct EN/UR text.
3. Click Demo Reset. Alerts panel clears within 2s. Cooldown map cleared (verify: click Inject Overheat again immediately after reset — alert fires again, not suppressed).
4. Repeat the full sequence 3× back-to-back. All 3 runs succeed. Capture numbers into `rd_benchmarks.md`.
5. Kill the browser, reload, everything still works (seed persistence is Phase 11 territory but re-verify here).
6. If hybrid hardware is in scope: bench-verify DHT22 → MQTT → dashboard reads real ambient temp before demo day.

**Rubric impact:**
- Criterion 3 (Product & Demo): 3 → 4 (or 5 with hybrid hardware).
- Criterion 4 (Traction): 2–3 → 3–4 via R&D benchmarks doc.
- Criterion 11 (Pitch Delivery): timing discipline improved by deterministic 3-button flow.
- Criterion 12 (Q&A Defense): Demo Reset + benchmarks doc + SIMULATED honesty all give the team more surface area to defend from.

---
## ✅ Actually achieved

**Shipped:**
- `backend/alerts.py` — `reset_state()` clears `_cooldown`, `_fuel_buf`, `_run_hours_mem`, `_last_hr_ts`.
- `backend/routers/sim.py` — `POST /sim/reset`: calls `reset_state()`, deactivates all injectors + restores `generator_on: true` on every `sim_control` doc (base_values left untouched, per spec), zeroes `run_hours`/`last_run_hours_update` on all devices, purges alerts from the last 30 min. Returns `{"ok": true, "purged_alerts": N}`.
- `sim-control/src/components/DemoControlPanel.jsx` (new) — 4-button panel: Inject Overheat (`NFL-SITE-GEN-01`), Inject Consumable (`NFL-SITE-COMP-02`), Inject Theft (`NFL-SITE-GEN-01`, red SIMULATED pill), Demo Reset. Wired into `sim-control/src/App.jsx` above the existing per-device controls (which stay as-is — they're still useful for ad-hoc testing beyond the 3 demo devices).
- `sim-control/src/api.js` — added `getSimCtrl`, `getDevice`, `patchDevice`, `postSimReset`.
- `phase/rd_benchmarks.md` (new) — real numbers captured this rehearsal (see below).
- `README.md` — sim-control run instruction now mentions the Demo Control Panel + a short demo run-through blurb; roadmap table's F/G/H status columns corrected to ✅ (were stale ⬜ left over from before those phases shipped).

**Deviations:**
1. **`DemoControlPanel.jsx` fetches-then-merges before every `PUT /sim/{id}`**, rather than sending a bare partial patch as the phase doc's button pseudocode implies. `PUT /sim/{id}` does a full `replace_one` on the backend (confirmed in `routers/sim.py`), not a partial update — sending only `{inject: {...}}` would have wiped `base_values` and every other injector's state. This mirrors what `DeviceControl.jsx` already does (`merge()` before `putSimCtrl`) — I just hadn't noticed the same requirement applied to the new panel until testing it.
2. **Inject Consumable uses `threshold − 0.0003`, not `threshold − 0.1`** as phase-12.md's button 2 pseudocode literally specifies. Per the Phase 11 gotcha (carried forward and re-confirmed here): run-hours accrue in real wall-clock time, so `− 0.1` needs ~6 minutes of continuous ticks to cross, which isn't a workable on-stage button. `− 0.0003` crosses on the next `load_current` tick (~1s), confirmed in this rehearsal (`rd_benchmarks.md`).
3. **Screen recording and hybrid hardware branch not attempted.** Both require a human at a real keyboard/webcam/bench — this session ran the rehearsal headlessly via direct API calls (no browser session, no physical ESP32 on hand). Flagging both as manual follow-ups for Arham before demo day, not silently skipping them.

**Deferred:** hybrid hardware branch (12.6) and screen recording (12.7) — both explicitly require physical presence; see deviation #3. No changes to `rul.py`, Groq chat, schema, or `alerts.py` engine logic beyond the one `reset_state()` helper, per guardrails.

**Gotchas:**
- **Process-management chaos mid-session** — across this long multi-phase conversation, several background `nohup` launches of `ingestion.py`/`simulator.py`/`uvicorn` had accumulated as duplicates (2× each), and the actual backend serving port 8000 turned out to be an orphaned `multiprocessing.spawn_main` worker (PID 16728) under a parent PID (2104) that no Windows-side tool (`Get-Process`, `taskkill`, even `ps -W`) could directly query — only visible via `Get-CimInstance Win32_Process` cross-referencing `ParentProcessId`. All duplicates were stopped and the stack was restarted once, cleanly, before this rehearsal. **If you see stale/duplicate `python.exe` processes after a long session, use `Get-CimInstance Win32_Process | Where CommandLine -match '...'` rather than trusting `netstat`'s reported PID directly** — it can point at a phantom parent instead of the real worker.
- Because of the restart above, "continuous uptime" in `rd_benchmarks.md` reflects a fresh process, not a long soak — worth a longer unattended soak test before the actual pitch if time allows.

**Acceptance checks — results:**
1. ✅ Fresh backend restart → log confirms `"[alerts] run_hours seeded for 6 device(s)"` on startup (Phase 11 rule holds).
2. ✅ Ran all 3 injector actions via direct API calls (equivalent to clicking each button): overheat fired 3× (1.77s / 2.94s / 3.00s to visible alert), fuel theft fired (1.24s), consumable reorder fired (1.09s, using the `− 0.0003` fix). WhatsApp *routing* resolved correctly each time; actual delivery unverified (Twilio not configured this environment — pre-existing, see `phase/limitations.md`).
3. ✅ `POST /sim/reset` → re-injected overheat immediately after → new alert fired with a fresh `ts` (~27s after the first), confirming cooldown state is actually cleared, not just the UI.
4. ✅ 3 reset+inject cycles run back-to-back this rehearsal (see `rd_benchmarks.md` for the 3 reset timings + 3 overheat timings); all succeeded.
5. Not independently re-tested this phase (browser reload) — Phase 11 already covers seed/state persistence across restarts; no reason to expect regression since this phase only added a reset endpoint and a helper.
6. Hybrid hardware — out of scope this cycle (no bench access); see deviation #3.
