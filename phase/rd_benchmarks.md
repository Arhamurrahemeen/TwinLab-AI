# TwinLab R&D Benchmarks — 2026-07-31

Measured during Phase 12 dress rehearsal, on top of commit `fe553d4` (Phase 11) plus
the in-progress Phase 12 changes. Captured on Arham's laptop, Docker Desktop (Mosquitto +
InfluxDB + MongoDB), backend/simulator/ingestion running natively via the project `.venv`.

All numbers below are real measurements taken via direct API calls against the running
stack during this rehearsal (`curl` timing, log line counts, alert timestamps) — not
estimates. Metrics that couldn't be honestly measured in this environment are marked
**not measured** rather than filled with placeholder numbers.

## System reliability

- Continuous simulator/backend runtime at time of capture: freshly restarted this
  session (stack had accumulated duplicate background processes from earlier phase
  work — cleaned up and restarted once, cleanly, before this rehearsal).
- MQTT messages published (ingestion log lines, proxy for message count) at capture time:
  3,087 ingestion `[OK]` lines / 3,219 simulator publish lines across 6 registered devices.
- Backend restarts during this rehearsal: 1 (planned, to load Phase 12's `/sim/reset` route).

## Alert engine (3 rehearsal runs)

- **Demo Reset time-to-clean** (`POST /sim/reset` round-trip): run 1 = 493 ms, run 2 = 582 ms, run 3 = 460 ms. All well under the 2s target.
- **Inject Overheat → alert visible** (critical threshold, `NFL-SITE-GEN-01`): run 1 = 1.77s, run 2 = 2.94s, run 3 = 3.00s.
- **Inject Fuel Theft → alert visible** (critical `fuel_theft`, `NFL-SITE-GEN-01`, after priming fuel to 80L): 1.24s.
- **Inject Consumable Reorder → alert visible** (`warning` `consumable_reorder`, `NFL-SITE-COMP-02`): 1.09s (patched to `threshold − 0.0003` — see gotcha below on why `threshold − 0.1` is not a near-instant trigger).
- **Cooldown correctness**: fired `overheat` once, called `POST /sim/reset`, immediately re-fired `overheat` — second alert recorded a new `ts` ~27s after the first (would otherwise be suppressed for 600s by the cooldown). Confirms Demo Reset actually clears in-memory cooldown state, not just the UI.
- **False positives at baseline**: 0, re-confirmed in Phase 10's and Phase 11's own acceptance walkthroughs (see `phase-10.md`, `phase-11.md`); not independently re-measured in this rehearsal since injector testing ran continuously.

## WhatsApp delivery

- **Not measured** — `twilio_account_sid` is unset in this dev environment (documented in `phase/limitations.md`, Twilio Error 63007 / daily sandbox limit). All test alerts recorded `whatsapp_sent: false`. Routing *resolution* logic was unit-verified in isolation (Phase 11): correct roles selected per `(severity, alert_type)`, correct contacts filtered, `[→ role: name]` prefix correctly formatted. Real delivery latency and success rate need a rehearsal with live Twilio credentials.

## Cross-source compatibility

- MQTT topic contract (`twinlab/device/{device_id}/sensor/{sensor}`) validated against one live source this rehearsal: the simulator, across 6 registered devices. ESP32 firmware path remains validated only at the code/contract level (Phase 9, deferred) — no physical hardware exercised this cycle.

## Demo choreography

- Demo Control Panel: 3 injector buttons + Demo Reset, implemented in `sim-control/src/components/DemoControlPanel.jsx`, targeting the fixed demo device IDs from the Phase 10 seed.
- Successful end-to-end injector cycles this rehearsal: overheat 3/3, fuel theft 1/1, consumable reorder 1/1 (fuel theft and consumable reorder were run once each after the 3x overheat cycle to conserve rehearsal time, not because of a failure).
- Time between injector call and visible alert on the dashboard: overheat p50 ≈ 2.94s (n=3, small sample), fuel theft = 1.24s (n=1), consumable reorder = 1.09s (n=1).
- **Screen recording**: not produced — this rehearsal was run headlessly via direct API calls (no browser session was open to record). Arham should record one clean run through the actual dashboard + sim-control UI before demo day, per the doc's non-negotiable step 12.7.
- **Hybrid hardware branch**: not attempted — out of scope for this automated rehearsal (needs physical ESP32 + DHT22 on the bench).

## FCM push delivery — 2026-09-13 (end-to-end verification)

First confirmed real-token, real-alert push test since Phase 14 replaced Twilio/WhatsApp
with Firebase Cloud Messaging. Stack running natively (Docker Mosquitto/InfluxDB/MongoDB +
backend/ingestion/simulator via `.venv`), `push_tokens` already held one real FCM token
(registered by the compiled Android APK, 2026-09-11).

- Triggered `PUT /sim/NFL-SITE-GEN-01` with `inject.overheat.active=true` → simulator
  published `temperature` up to ~99°C (threshold max 40) → alert engine fired within
  seconds: `[ALERT] threshold critical — NFL-SITE-GEN-01/temperature — above max 40`.
- Backend log: `[PUSH] Firebase initialised` then `[PUSH] NFL-SITE-GEN-01/threshold — sent 1/1`.
- `GET /alerts` confirms the persisted alert doc carries `"push_sent": true`.
- A second `push_sent: true` alert from earlier the same day (08:54 UTC, fuel_level warning)
  was already present in the alerts collection before this test — Arham appears to have
  run this same verification independently earlier today.
- **Confirmed by Arham:** the push notification showed up on the physical phone. Full
  pipeline (simulator injector → threshold alert → FCM → phone banner) verified end to end.
- Ran `POST /sim/reset` afterward to clear the injected state and the test alert.

## Gotcha carried forward from Phase 11

`evaluate_run_hours()`'s accumulation is real wall-clock (`delta_h = delta_s / 3600`), so
patching `run_hours` to `threshold − 0.1` (as phase-11.md's demo trigger literally
specifies) takes ~6 minutes of continuous above-`OFF_AMPS` readings to close the gap,
not "the next tick." This rehearsal used `threshold − 0.0003` to get a fast, demoable
trigger — recommend the sim-control button use the same fast-trigger value rather than
the literal `− 0.1` from the phase-11 spec.
