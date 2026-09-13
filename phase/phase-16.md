# Phase 16 — Vibration alert rule + vibration-driven run/stop detection

## Goal

Give hardware nodes (no CT clamp, so no `load_current`) two things that were
explicitly blocked pending a decision (`CLAUDE.md` §6): a threshold alert on
`vibration`, and a working run-hours meter driven by vibration instead of
current draw. Both reuse existing generic mechanisms — no new alert type,
no new schema, no frontend changes.

## Structure & steps

**`backend/alerts.py`:**
- Add `vibration` to `_CRITICAL_MAX` — a vibration max-breach is a critical
  alert, same tier as temperature/load_current, not a warning.
- Add `VIB_RUNNING_G` constant next to `OFF_AMPS` — the EMA-vibration value
  above which a hardware asset is considered "running". **Uncalibrated
  placeholder (0.03g)** — `TL-B49244` is a bench unit, never mounted on a
  spinning machine, so there is no real running-vs-idle sample yet. Flagged
  in a comment for recalibration once the sensor is actually strapped to a
  generator.
- Refactor `evaluate_run_hours`'s body into a shared `_evaluate_run_hours(device_id, running: bool)`
  helper, so the accumulate/threshold/reset/cooldown logic isn't duplicated.
  `evaluate_run_hours(device_id, value)` becomes a one-line wrapper
  (`running = value > OFF_AMPS`). New `evaluate_run_hours_vibration(device_id, value)`
  is the vibration-driven twin (`running = value > VIB_RUNNING_G`).
- No new alert type for the vibration threshold itself — it's the existing
  generic `"threshold"` alert_type in `evaluate()`, unlocked purely by a
  device having `thresholds.vibration` set.

**`backend/main.py`:**
- In `_on_mqtt_message`, add an `elif sensor_name == "vibration":` branch
  parallel to the existing `if sensor_name == "load_current":` branch,
  calling `evaluate_run_hours_vibration` instead. No device-source check —
  today's sensor sets don't overlap (simulator devices never publish
  `vibration`, hardware devices never publish `load_current`), so this
  can't double-accumulate in practice.

**Data (no schema change, just a value):**
- `PATCH /devices/TL-B49244` to set `thresholds.vibration = {"max": 0.15}` —
  same uncalibrated-placeholder caveat as `VIB_RUNNING_G`. `EditDevice.jsx`
  already renders a min/max threshold row for any sensor in a device's
  `sensors` list, so this is editable from the dashboard with no frontend
  change (vibration is already one of `TL-B49244`'s registered sensors).

**Self-check:** `if __name__ == "__main__":` block at the bottom of
`alerts.py` — assert-based, exercises `evaluate()` crossing the vibration
threshold and both `evaluate_run_hours*` functions accumulating and firing.
No test framework (matches `CLAUDE.md` §6 — no test suite yet).

## Start commands

```powershell
docker compose up -d
.venv\Scripts\python ingestion.py
cd backend; ..\.venv\Scripts\uvicorn main:app --reload --host 0.0.0.0 --port 8000
.venv\Scripts\python backend\alerts.py          # self-check, run standalone
```

## Expected outcome

- [ ] `python backend/alerts.py` runs clean, all asserts pass.
- [ ] Publishing a `vibration` reading above `TL-B49244`'s threshold via
      MQTT produces a critical `threshold` alert (check `GET /alerts`).
- [ ] Sustained `vibration` readings above `VIB_RUNNING_G` accumulate
      `run_hours` on `TL-B49244` (previously stuck at 0.0 forever on
      hardware devices) and fire `consumable_reorder` at the threshold,
      same as the simulator path already does for `load_current`.
- [ ] No change needed in `EditDevice.jsx` — vibration threshold row
      already renders and is editable.

---
## ✅ Actually achieved

All items shipped and verified end to end:

- `alerts.py`: `vibration` added to `_CRITICAL_MAX`; `VIB_RUNNING_G = 0.03` (uncalibrated
  placeholder, documented). `evaluate_run_hours` refactored into a shared
  `_evaluate_run_hours(device_id, running: bool)` helper; `evaluate_run_hours_vibration`
  is the vibration-driven twin. Self-check block (`if __name__ == "__main__"`) passes —
  covers threshold crossing, cooldown suppression, idle vs. running accumulation, and
  the consumable_reorder fire-and-reset.
- `main.py`: `elif sensor_name == "vibration":` branch added parallel to the existing
  `load_current` one, calling `evaluate_run_hours_vibration`.
- `TL-B49244` patched with `thresholds.vibration = {"max": 0.15}` (same placeholder
  caveat as `VIB_RUNNING_G`).
- Live-verified against the real running stack: published a vibration reading above
  0.15 via MQTT — got a **critical** `threshold` alert, pushed successfully
  (`push_sent: true`). Confirmed severity was `warning` on the first attempt because
  the already-running backend process hadn't picked up the code change — a genuine
  process restart (not just `--reload`, which for some reason didn't catch it this
  time) fixed it. Worth remembering for future phases.
- Confirmed run-hours actually accumulate from sustained vibration: `TL-B49244.run_hours`
  went from `0.0` to `0.0235` across repeated above-`VIB_RUNNING_G` publishes — the
  first hardware-side run-hours movement ever recorded (it's been stuck at `0.0`
  since Phase 13, since hardware devices have no `load_current`).
- **Not independently forced in the live stack:** an actual `consumable_reorder` fire
  via vibration — attempted with a temporarily-lowered `run_hours_threshold`, but that
  PATCH 422'd (`run_hours_threshold` is `int`-typed, rejects a sub-1 test value). Not a
  bug in this phase's code — the fire-on-threshold-cross logic is exactly the code path
  the self-check test above already asserts, and it's the same shared helper the
  load_current path has used since Phase 6.
- Cleaned up after testing via `POST /sim/reset` — `run_hours` back to `0.0`, test
  alerts purged. `thresholds.vibration` left in place as the real, intended config.
